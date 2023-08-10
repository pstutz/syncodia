/*
 * Copyright 2023 Justement GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package syncodia

import com.typesafe.config.{ Config, ConfigFactory }
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.ContentTypes.`application/json`
import org.apache.pekko.http.scaladsl.model.HttpMethods.POST
import org.apache.pekko.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling.*
import org.apache.pekko.pattern.after
import org.apache.pekko.stream.scaladsl.*
import syncodia.Syncodia.defaultPekkoConfig
import syncodia.openai.*
import syncodia.openai.protocol.*
import syncodia.openai.protocol.ChatCompletionModel.GPT_35_TURBO
import syncodia.openai.protocol.SerializeJson.*
import ujson.Value.Value

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
import scala.util.{ Failure, Success, Try }

implicit given string2Message: Conversion[String, Message] = (s: String) => Message(Role.User, s)

implicit given string2Messages: Conversion[String, Seq[Message]] =
  (s: String) => Seq(Message(Role.User, s))

implicit given message2messages: Conversion[Message, Seq[Message]] = (msg: Message) => Seq(msg)

extension [V, C, G](sourceWithContext: SourceWithContext[V, C, G])
  def withoutContext: Source[V, G] = sourceWithContext.asSource.map(_._1)

object Syncodia:

  val chatCompletionsEndpoint = "https://api.openai.com/v1/chat/completions"

  val embeddingsEndpoint = "https://api.openai.com/v1/embeddings"

  private val openAiApiKeyVarName = "OPENAI_API_KEY"

  val maxBackoffDelay: FiniteDuration = 10.seconds

  val defaultPekkoConfig: Config = ConfigFactory
    .parseString("""pekko.loglevel = "ERROR"""")
    .withFallback(ConfigFactory.defaultApplication())

  def apply(): Syncodia =
    val openAiApiKey: String = Option(System.getProperty(openAiApiKeyVarName))
      .orElse(Option(System.getenv(openAiApiKeyVarName)))
      .getOrElse(throw new IllegalArgumentException("No OpenAI API Key provided"))
    Syncodia(openAiApiKey)

  def apply(openAiApiKey: String): Syncodia = new Syncodia(openAiApiKey, None)

  def apply(openAiApiKey: String, actorSystem: ActorSystem): Syncodia =
    new Syncodia(openAiApiKey, Some(actorSystem))

end Syncodia

class Syncodia(openAiApiKey: String, maybeProvidedActorSystem: Option[ActorSystem]):

  implicit val actorSystem: ActorSystem = maybeProvidedActorSystem
    .getOrElse(ActorSystem("default", defaultPekkoConfig))

  implicit val executionContext: ExecutionContext = actorSystem.getDispatcher

  import Syncodia.*

  private val chatCompletionRequestTemplate: HttpRequest = HttpRequest(
    method = POST,
    uri = chatCompletionsEndpoint,
    headers = Seq(Authorization(OAuth2BearerToken(openAiApiKey))),
    entity = HttpEntity.Empty
  )

  private[syncodia] def executeChatCompletionRequest(
      ccr: ChatCompletionRequest,
      maxRetryAttempts: Int
  ): Future[ChatCompletionResponse] =
    val body           = SerializeJson.write(ccr)
    val request        = chatCompletionRequestTemplate.withEntity(HttpEntity(`application/json`, body))
    val responseFuture = retryWithExponentialBackoff(() => runApiRequest(request), maxRetryAttempts)
    responseFuture.flatMap { response =>
      Unmarshal(response.entity).to[String].map { responseString =>
        val tryParse = Try(SerializeJson.read[ChatCompletionResponse](responseString))
        tryParse match
          case Success(chatCompletionResponse) => chatCompletionResponse
          case Failure(e) => throw ParseException(s"Failed to parse response: $responseString", e)
      }
    }

  end executeChatCompletionRequest

  private[syncodia] def runApiRequest(r: HttpRequest): Future[HttpResponse] = Http()
    .singleRequest(r)
    .flatMap { response =>
      response.status.intValue() match
        case code if code >= 200 && code < 300 => Future.successful(response)
        case errorCode =>
          Unmarshal(response.entity).to[String].flatMap { responseBody =>
            errorCode match
              case 401 =>
                if responseBody.contains("Invalid Authentication") then
                  Future.failed(ApiException.InvalidAuthenticationException(responseBody))
                else if responseBody.contains("Incorrect API key provided") then
                  Future.failed(ApiException.IncorrectApiKeyException(responseBody))
                else if responseBody
                    .contains("You must be a member of an organization to use the API")
                then Future.failed(ApiException.NoMembershipException(responseBody))
                else Future.failed(ApiException.UnhandledException(401, responseBody))
              case 429 if responseBody.contains("Rate limit reached") =>
                Future.failed(ApiException.RateLimitException(responseBody))
              case 429 if responseBody.contains("exceeded your current quota") =>
                Future.failed(ApiException.QuotaExceededException(responseBody))
              case 500 => Future.failed(ApiException.ServerErrorException(responseBody))
              case 503 => Future.failed(ApiException.OverloadedException(responseBody))
              case unhandledCode =>
                Future
                  .failed(ApiException.UnhandledException(unhandledCode, responseBody))
          }
    }

  end runApiRequest

  private[syncodia] def retryWithExponentialBackoff[T](
      operation: () => Future[T],
      maxRetryAttempts: Int,
      backoffFactor: Double = 2,
      currentDelay: FiniteDuration = 0.millis // start with no delay
  ): Future[T] = operation().recoverWith {
    case ex: ApiException if ex.maybeRetryAfter.isDefined && maxRetryAttempts > 0 =>
      val calculatedDelay: Duration = currentDelay * backoffFactor + ex.maybeRetryAfter.get
      val nextDelay: FiniteDuration = calculatedDelay match
        case fd: FiniteDuration if fd <= maxBackoffDelay => fd
        case _                                           => maxBackoffDelay
      after(nextDelay, actorSystem.scheduler) {
        retryWithExponentialBackoff(operation, maxRetryAttempts - 1, backoffFactor, nextDelay)
      }
    case e => Future.failed(e)
  }

  end retryWithExponentialBackoff

  def complete(
      messages: Seq[Message],
      model: ChatCompletionModel = ChatCompletionModel.GPT_35_TURBO,
      functionsOrForceFunction: Seq[ChatFunction] | Option[ChatFunction] = None,
      maxTokens: Int = -1, // -1 sets no max tokens
      temperature: Option[Float] = Some(0),
      maxRetryAttempts: Int = 5
  ): Future[ChatCompletionResponse] =
    val maybeMaxTokens = if maxTokens == -1 then None else Some(maxTokens)
    val (functions, functionCallRequestParameter) = functionsOrForceFunction match
      case Some(f) => Some(Seq(f.functionSchema)) -> Some(FunctionCallRequestParameter(f.name))
      case None    => None                        -> None
      case seq: Seq[ChatFunction @unchecked] => Some(seq.map(_.functionSchema)) -> None
    val ccr = ChatCompletionRequest(
      model = model,
      messages = messages,
      functions = functions,
      functionCall = functionCallRequestParameter,
      temperature = temperature,
      maxTokens = maybeMaxTokens
    )
    executeChatCompletionRequest(ccr, maxRetryAttempts)

  end complete

  def execute(
      messages: Seq[Message],
      model: ChatCompletionModel = ChatCompletionModel.GPT_35_TURBO,
      functions: Seq[ChatFunction] | Option[ChatFunction] = None,
      reportFunctionResult: Boolean = true,
      maxTokens: Int = -1, // -1 sets no max tokens
      temperature: Option[Float] = Some(0),
      maxApiRetryAttempts: Int = 5,
      maxFunctionCallRetryAttempts: Int = 2,
      printMessages: Boolean = false,
      isFollowUpExecution: Boolean = false
  ): Future[ChatCompletionResponse] =
    if printMessages && !isFollowUpExecution then messages.foreach(m => println(m.pretty))
    val functionsByName = functions match
      case Some(f)                           => Map(f.name -> f)
      case None                              => Map.empty
      case seq: Seq[ChatFunction @unchecked] => seq.map(f => f.name -> f).toMap
    val responseFuture =
      complete(messages, model, functions, maxTokens, temperature, maxApiRetryAttempts)
    responseFuture.flatMap { response =>
      val maybeMessage = response.choices.headOption.map(_.message)
      if printMessages then maybeMessage.foreach(m => println(m.pretty))
      maybeMessage match
        case Some(m: Message) if m.role == Role.Assistant && m.functionCall.isDefined =>
          val functionCall = m.functionCall.get
          val functionName = functionCall.name
          val (result, isSuccess) = functionsByName.get(functionName) match
            case None => s"No function with name $functionName found" -> false
            case Some(chatFunction) =>
              val (resultValue, resultString) = chatFunction
                .invokeWithParams(functionCall.arguments)
              resultString -> !resultValue.isInstanceOf[Throwable]
          val resultMessage = Message(Role.Function, result, Some(functionName))
          if printMessages then println(resultMessage.pretty)
          if isSuccess then
            if reportFunctionResult then
              execute(
                messages :+ m :+ resultMessage,
                model,
                functions = functions,
                reportFunctionResult,
                maxTokens,
                temperature,
                maxApiRetryAttempts,
                maxFunctionCallRetryAttempts,
                printMessages,
                isFollowUpExecution = true
              )
            else Future.successful(response)
          else if !isSuccess && maxFunctionCallRetryAttempts > 0 then
            execute(
              messages :+ m :+ resultMessage,
              model,
              functions,
              reportFunctionResult,
              maxTokens,
              temperature,
              maxApiRetryAttempts,
              maxFunctionCallRetryAttempts - 1,
              printMessages,
              isFollowUpExecution = true
            )
          else Future.failed(new Exception(result))
        case _ => Future.successful(response)
    }

  end execute

  def executeContinuously(
      messages: Seq[Message],
      model: ChatCompletionModel = ChatCompletionModel.GPT_35_TURBO,
      functions: Seq[ChatFunction] | Option[ChatFunction] = None,
      maxTokens: Int = -1, // -1 sets no max tokens
      temperature: Option[Float] = Some(0),
      maxApiRetryAttempts: Int = 5,
      maxFunctionCallRetryAttempts: Int = 2,
      printMessages: Boolean = false
  ): Future[ChatCompletionResponse] =

    def recExecute(recMessages: Seq[Message]): Future[ChatCompletionResponse] =
      val updatedMaxTokens =
        if maxTokens == -1 then -1
        else maxTokens - recMessages.drop(messages.length).map(_.tokenCount).sum
      val responseFuture =
        execute(
          recMessages,
          model,
          functions,
          reportFunctionResult = true,
          updatedMaxTokens,
          temperature,
          maxApiRetryAttempts,
          maxFunctionCallRetryAttempts,
          printMessages
        )
      responseFuture.flatMap { response =>
        response.choices.headOption match
          case Some(choice) if choice.finishReason == "function_call" =>
            if printMessages then println(choice.message.pretty)
            val updatedMessages = recMessages :+ choice.message
            recExecute(updatedMessages)
          case _ => Future.successful(response)
      }
    end recExecute

    if printMessages then messages.foreach(m => println(m.pretty))

    recExecute(messages)

  end executeContinuously

  private[syncodia] def runStreamingChatCompletionRequest(
      ccr: ChatCompletionRequest,
      maxRetryAttempts: Int
  ): SourceWithContext[String, ChatCompletionDeltaResponse, Future[NotUsed]] =
    val body    = SerializeJson.write(ccr)
    val request = chatCompletionRequestTemplate.withEntity(HttpEntity(`application/json`, body))
    val sseSourceFuture: Future[Source[ServerSentEvent, NotUsed]] =
      retryWithExponentialBackoff(() => runApiRequest(request), maxRetryAttempts).flatMap { response =>
        Unmarshal(response.entity).to[Source[ServerSentEvent, NotUsed]]
      }
    val sseSource: Source[ServerSentEvent, Future[NotUsed]] = Source.futureSource(sseSourceFuture)

    sseSource
      .takeWhile(sse => sse.data != "[DONE]", inclusive = false)
      .map { sse =>
        val tryJson = Try(read[ChatCompletionDeltaResponse](sse.data))
        tryJson match
          case Failure(exception) =>
            throw new Exception(
              s"""Error when parsing
                 |${sse.data}
                 |as a ChatCompletionDeltaResponse: '${exception.getMessage}'""".stripMargin,
              exception
            )
          case Success(parsed) => parsed
      }
      .asSourceWithContext(identity)
      .map(parsed => parsed.completion)

  end runStreamingChatCompletionRequest

  def completeStreaming(
      messages: Seq[Message],
      model: ChatCompletionModel = ChatCompletionModel.GPT_35_TURBO,
      functionsOrForceFunction: Seq[ChatFunction] | Option[ChatFunction] = None,
      maxTokens: Int = -1, // -1 sets no max tokens
      temperature: Option[Float] = Some(0),
      maxRetryAttempts: Int = 5
  ): SourceWithContext[String, ChatCompletionDeltaResponse, Future[NotUsed]] =
    val maybeMaxTokens = if maxTokens == -1 then None else Some(maxTokens)
    val (functions, functionCallRequestParameter) = functionsOrForceFunction match
      case None    => None                        -> None
      case Some(f) => Some(Seq(f.functionSchema)) -> Some(FunctionCallRequestParameter(f.name))
      case seq: Seq[ChatFunction @unchecked] => Some(seq.map(_.functionSchema)) -> None
    val ccr = ChatCompletionRequest(
      model = model,
      messages = messages,
      functions = functions,
      functionCall = functionCallRequestParameter,
      stream = Some(true),
      temperature = temperature,
      maxTokens = maybeMaxTokens
    )
    runStreamingChatCompletionRequest(ccr, maxRetryAttempts)

  end completeStreaming

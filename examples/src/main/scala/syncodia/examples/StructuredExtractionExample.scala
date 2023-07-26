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

package syncodia.examples

import syncodia.*

import scala.util.Failure

enum ProductFeature:
  case Screen, Battery, Camera, Performance

enum Sentiment:
  case Positive, Negative, Neutral

case class FeatureSentimentAnalysis(reviewId: Int, feature: ProductFeature, sentiment: Sentiment)

object StructuredInformationExtraction extends App:

  val reviews = Seq(
    "The screen on this device is excellent but the performance can be sluggish. Camera is alright.",
    "I'm not happy with the battery life, but the camera quality is impressive.",
    "Camera is good. Performance is ok. Costs too much!",
    "Performance could be better, but I love the screen!"
  )

  var allAnalyses: Seq[FeatureSentimentAnalysis] = Seq.empty

  def recordReviewSentiments(analyses: Seq[FeatureSentimentAnalysis]): Unit = allAnalyses = analyses

  val message = s"""|Extract sentiments about product features from these reviews:
                    |${reviews.zipWithIndex
                     .map { case (review, idx) =>
                       s"Review #${idx + 1}: '$review'"
                     }
                     .mkString("\n")}""".stripMargin

  val syncodia = Syncodia()

  import syncodia.*

  val execution = syncodia
    .execute(
      message,
      functions = Some(ChatFunction(recordReviewSentiments)),
      reportFunctionResult = false,
      printMessages = true
    )
  execution.onComplete {
      case Failure(exception) =>
        println(s"Failed to extract sentiments: ${exception.getMessage}")
      case _ =>
        println(s"Sentiment analysis for product features:\n${
          allAnalyses
            .groupBy(_.reviewId)
            .toSeq
            .sortBy(_._1)
            .map { case (reviewId, sentiment) =>
              s"\t$reviewId. ${sentiment.map(s => s"${s.feature}: ${s.sentiment}").mkString(", ")}"
            }
            .mkString("\n")
        }")
    }
  execution.andThen(_ => syncodia.actorSystem.terminate())

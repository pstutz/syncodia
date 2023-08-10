# Syncodia: Bridging Large Language Models with Scala 3 Functions

Syncodia is an open-source Scala 3 project that allows Large Language Models (LLMs) with function calling capabilities
(GPT-3.5 and GPT-4 are currently supported) to seamlessly execute Scala functions. Syncodia uses Scala 3's
metaprogramming capabilities to automatically generate the LLM/Scala bridging code, saving you time and effort.

This project is still experimental, please report any issues you encounter.

## Use Cases

- **Interaction with Structured Data Sources:** Syncodia can provide a natural language interface to data that
  traditionally requires structured queries or programming knowledge to access.

- **Information Extraction and Structuring:** Syncodia can facilitate the transformation of unstructured text into
  structured data.

- **Task Orchestration:** With Syncodia, an LLM can plan and orchestrate complex sequences of tasks described in natural
  language, allowing you to execute multi-step or recursive LLM operations in a flexible, intuitive way.

- **Language Interface for Traditional Software:** Syncodia can help you enable your software to understand and execute
  complex user commands given in natural language, making your software more accessible and user-friendly.

## Supported Data Structures

Syncodia supports a subset of Scala 3 data structures for use as function parameters and return values:

- Basic types: String, Boolean, Int, Long, Float, Double, Unit
- Option, Seq, Map with String keys
- Tuples, Case Classes, and Enums

These can also be composed in nested structures.


## Getting Started

<!--TODO
### Installation

To use Syncodia, you will need JVM version >= 11 and Scala 3. To add Syncodia to your Scala 3 project, include the
following SBT dependency:

```sbt
  libraryDependencies += "com.syncodia" %% "syncodia-core" % "0.1.0"
```

Alternatively, you can generate a Syncodia Scala 3 example project from a giter8 template:

```shell
sbt new syncodia/syncodia.g8
```
-->

### Setup

To use Syncodia with the OpenAI APIs, you will need an OpenAI account. Provide your OpenAI secret to Syncodia either via
the constructor or by defining the environment variable or system property `OPENAI_API_KEY`.

Please note that your API key is sensitive information that should not be hardcoded, as this would risk accidental
sharing.

### Usage

Syncodia provides a single entry point: the `Syncodia` class.

#### Example: Simple function calling

This example shows how to allow an LLM to call a Scala function.

```scala
import syncodia.*

@main def callSqrtFunction(): Unit =
  Syncodia("your-openai-api-key").execute(
    "What is the square root of 17?",
    functions = Seq(ChatFunction(scala.math.sqrt)),
    printMessages = true
  )
```

Console output:

```
User: "What is the square root of 17?"
Assistant: call sqrt(x=17)
Function: result = 4.123105625617661
Assistant: "The square root of 17 is approximately 4.123105625617661."
```

#### Example: Structured extraction

This example shows how to use Syncodia to extract structured information from unstructured text.

```scala
import syncodia.*

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
                    |${reviews.zipWithIndex.map { case (review, idx) =>
                     s"Review #${idx + 1}: '$review'"
                   }
                     .mkString("\n")}""".stripMargin

  val syncodia = Syncodia("your-openai-api-key")

  import syncodia.*

  syncodia
    .execute(
      message,
      functions = Some(ChatFunction(recordReviewSentiments)),
      reportFunctionResult = false
    )
    .onComplete { _ =>
      println(s"Sentiment analysis for product features:\n${allAnalyses
          .groupBy(_.reviewId)
          .toSeq
          .sortBy(_._1)
          .map { case (reviewId, sentiment) =>
            s"\t$reviewId. ${sentiment.map(s => s"${s.feature}: ${s.sentiment}").mkString(", ")}"
          }
          .mkString("\n")}")
      syncodia.actorSystem.terminate()
    }
```

Console output:

```
Sentiment analysis for product features:
  1. Screen: Positive, Performance: Negative, Camera: Neutral
  2. Battery: Negative, Camera: Positive
  3. Camera: Positive, Performance: Neutral
  4. Performance: Negative, Screen: Positive
```

## License

Syncodia is released under the Apache 2.0 license.

## Contributing

Contributions are welcome. For non-trivial contributions please submit an issue with the proposal before submitting a
pull request.

## Contact

For matters directly related to the project and its usage, please use GitHub issues. For all other inquiries or concerns, please do not hesitate to reach out to [Philip Stutz](mailto:philip.stutz@justement.ch?subject=Syncodia). Your feedback and questions are always welcome.

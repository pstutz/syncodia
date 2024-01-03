name := "syncodia"

// License information
organizationName := "Justement GmbH"
startYear        := Some(2023)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

val syncodiaVersion = "0.1.0-SNAPSHOT"

val scala3Version = "3.3.1"

val pekkoHttpVersion      = "1.0.0"
val pekkoVersion          = "1.0.1"
val uSerializationVersion = "3.1.3"
val scalaReflectVersion   = "2.13.12"
val jTokkitVersion        = "0.6.1"
val munitVersion          = "0.7.29"

lazy val commonSettings = Seq(
  version      := syncodiaVersion,
  scalaVersion := scala3Version,
  organization := "Justement GmbH",
  scalacOptions ++= Seq(
    "-feature",     // Enables detailed warning messages for advanced language features which require import
    "-deprecation", // Emits warning and location for usages of deprecated APIs
    "-language:implicitConversions", // Enables the use of implicit conversions
    "-explain",                      // Explain errors in more detail
    "-new-syntax"                    // Require 'then' and 'do' in control expressions
  ),
  libraryDependencies ++= Seq(
    "org.scalameta" %% "munit"            % munitVersion % Test,
    "org.scalameta" %% "munit-scalacheck" % munitVersion % Test
  )
)

lazy val syncodia = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "syncodia",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http"    % pekkoHttpVersion,
      "org.apache.pekko" %% "pekko-stream"  % pekkoVersion,
      "com.lihaoyi"      %% "upickle"       % uSerializationVersion,
      "com.lihaoyi"      %% "ujson"         % uSerializationVersion,
      "org.scala-lang"    % "scala-reflect" % scalaReflectVersion,
      "com.knuddels"      % "jtokkit"       % jTokkitVersion
    )
  )

lazy val examples = project
  .settings(commonSettings)
  .dependsOn(syncodia)

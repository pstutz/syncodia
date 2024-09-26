name := "syncodia"

// License information
organizationName := "Justement GmbH"
startYear        := Some(2023)
licenses += ("Apache-2.0", new URI("https://www.apache.org/licenses/LICENSE-2.0.txt").toURL)

val syncodiaVersion = "0.1.0-SNAPSHOT"

val scala3Version = "3.5.0"

val pekkoHttpVersion      = "1.0.1"
val uSerializationVersion = "4.0.1"
val pekkoVersion          = "1.0.3"
val scalaReflectVersion   = "2.13.15"
val jTokkitVersion        = "1.1.0"
val munitVersion          = "1.0.1"

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
    "org.scalameta" %% "munit-scalacheck" % "1.0.0"      % Test // TODO: Align with munitVersion, once released
  )
)

lazy val syncodia = project.in(file("core")).settings(commonSettings).settings(
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

lazy val examples = project.settings(commonSettings).dependsOn(syncodia)

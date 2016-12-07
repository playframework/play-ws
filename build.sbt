resolvers ++= DefaultOptions.resolvers(snapshot = true)

val PlayVersion = playVersion("2.5.10")

// XXX No version of play-iteratees for 2.12 yet
scalaVersion := "2.11.8"

val specsVersion = "3.8.6"
val specsBuild = Seq(
  "specs2-core",
  "specs2-junit",
  "specs2-mock"
).map("org.specs2" %% _ % specsVersion)

val logback = "ch.qos.logback" % "logback-classic" % "1.1.7"

val playJsonVersion = "2.6.0-M1"
val playJson = Seq("com.typesafe.play" %% "play-json" % playJsonVersion)

val playIterateesVersion = "2.6.0"
val playIteratees = Seq("com.typesafe.play" %% "play-iteratees" % playIterateesVersion)

val guiceVersion = "4.0"
val guiceDeps = Seq(
  "com.google.inject" % "guice" % guiceVersion,
  "com.google.inject.extensions" % "guice-assistedinject" % guiceVersion
)

// https://mvnrepository.com/artifact/javax.inject/javax.inject
val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

val sslConfigVersion = "0.2.1"
val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % sslConfigVersion)

val scalaXmlVersion = "1.0.6"
val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion)

val oauth = Seq(
  "signpost-core", "signpost-commonshttp4").map("oauth.signpost" % _  % "1.2.1.2"
)

val asyncHttpClientVersion = "2.0.11"
val asyncHttpClient = Seq(
  "org.asynchttpclient" % "async-http-client" % asyncHttpClientVersion
)

val akkaVersion = "2.4.14"
val akka = Seq(
  "akka-actor", "akka-slf4j").map("com.typesafe.akka" %% _ % akkaVersion
)

def wsDependencies(scalaVersion: String) =
  javaxInject ++
  sslConfigCore ++
  akka ++
  scalaXml ++
  playJson ++
  playIteratees ++
  Seq(
    logback % Test
    //) ++ (specsBuild :+ specsMatcherExtra).map(_ % Test) :+ mockitoAll % Test
  ) ++ specsBuild.map(_ % Test)

val ahcDependencies = asyncHttpClient ++ oauth

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayRootProject)
  .settings(scalaVersion := "2.11.8")
  .aggregate(`play-ws`, `play-ahc-ws`, `play-ws-extras`)

// Play API definition only, no implementation
lazy val `play-ws` = project
  .in(file("play-ws"))
  .enablePlugins(PlayLibrary)
  .settings(scalaVersion := "2.11.8")
  .settings(libraryDependencies ++= wsDependencies(scalaVersion.value))

// Play WS Implementation using AsyncHttpClient
lazy val `play-ahc-ws` = project
  .in(file("play-ahc-ws"))
  .enablePlugins(PlayLibrary)
  .settings(scalaVersion := "2.11.8")
  .settings(libraryDependencies ++= ahcDependencies)
  .dependsOn(`play-ws`)

// Depends on Play classes specifically
lazy val `play-ws-extras` = project
  .in(file("play-ws-extras"))
  .enablePlugins(PlayLibrary)
  .settings(scalaVersion := "2.11.8")
  .settings(libraryDependencies += "com.typesafe.play" %% "play" % PlayVersion)
  .dependsOn(`play-ws`, `play-ahc-ws`)

playBuildRepoName in ThisBuild := "play-ws"

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
  pushChanges
)

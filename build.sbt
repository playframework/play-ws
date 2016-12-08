resolvers ++= DefaultOptions.resolvers(snapshot = true)

// Latest snapshot is in
// https://oss.sonatype.org/content/repositories/snapshots/com/typesafe/play/play_2.11/maven-metadata.xml
//val PlayVersion = playVersion("2.6.0-2016-12-11-1904442-SNAPSHOT")
val PlayVersion = playVersion("2.6.0-SNAPSHOT")

// XXX No version of play-iteratees for 2.12 yet
scalaVersion := "2.11.8"

val specsVersion = "3.8.6"
val specsBuild = Seq(
  "specs2-core",
  "specs2-junit",
  "specs2-mock"
).map("org.specs2" %% _ % specsVersion)

val logback = "ch.qos.logback" % "logback-classic" % "1.1.7"

val playJsonVersion = PlayVersion
val playJson = Seq(
  "com.typesafe.play" %% "play-json" % playJsonVersion,
  "com.typesafe.play" %% "play-functional" % playJsonVersion
)

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
  "signpost-core", "signpost-commonshttp4").map("oauth.signpost" % _ % "1.2.1.2"
)

val asyncHttpClientVersion = "2.0.11"
val asyncHttpClient = Seq(
  "org.asynchttpclient" % "async-http-client" % asyncHttpClientVersion
)

val akkaVersion = "2.4.14"
val akka = Seq(
  "akka-stream", "akka-slf4j").map("com.typesafe.akka" %% _ % akkaVersion
)

val playTest = Seq(
  logback % Test,
  "com.typesafe.play" %% "play-test" % PlayVersion % Test,
  "com.typesafe.play" %% "play-specs2" % PlayVersion % Test
) ++ specsBuild.map(_ % Test)

def wsDependencies(scalaVersion: String) =
  javaxInject ++
    sslConfigCore ++
    akka ++
    scalaXml ++
    playJson ++
    playIteratees ++
    playTest

val ahcDependencies = asyncHttpClient ++ oauth ++ playTest

// Shading taken from
// https://manuzhang.github.io/2016/10/15/shading.html
// https://github.com/sbt/sbt-assembly#shading
//
//val shaded = Project(
//  id = "ws-shaded",
//  base = file("shaded")
//).aggregate(`shaded-asynchttpclient`)
//
//lazy val `shaded-asynchttpclient` = Project(
//  id = "shaded-asynchttpclient",
//  base = file("shaded/asynchttpclient"),
//  settings = shadeAssemblySettings ++ addArtifact(Artifact("ws-shaded-asynchttpclient"), sbtassembly.AssemblyKeys.assembly) ++
//    Seq(
//      assemblyShadeRules in assembly := Seq(
//        ShadeRule.rename("com.google.**" -> "play.api.libs.ws.ahc.asynchttpclient.@1").inAll
//      )
//    ) ++
//    Seq(
//      libraryDependencies ++= asyncHttpClient
//    )
//)

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayRootProject)
  .settings(scalaVersion := "2.11.8")
  .aggregate(`play-ws`, `play-ws-standalone`, `play-ws`, `play-ahc-ws`, `play-ws-integration-tests`)

// Play API definition only, no implementation
lazy val `play-ws-standalone` = project
  .in(file("play-ws-standalone"))
  .enablePlugins(PlayLibrary)
  .settings(scalaVersion := "2.11.8")
  .settings(libraryDependencies ++= wsDependencies(scalaVersion.value))

// Play WS Implementation using AsyncHttpClient
lazy val `play-ahc-ws-standalone` = project
  .in(file("play-ahc-ws-standalone"))
  .enablePlugins(PlayLibrary)
  .settings(scalaVersion := "2.11.8")
  .settings(libraryDependencies ++= ahcDependencies)
  .dependsOn(`play-ws-standalone`)

// Depends on Play classes specifically
lazy val `play-ws` = project
  .in(file("play-ws"))
  .enablePlugins(PlayLibrary)
  .settings(scalaVersion := "2.11.8")
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play" % PlayVersion,
    "com.typesafe.play" %% "play-java" % PlayVersion))
  .settings(libraryDependencies ++= specsBuild.map(_ % Test))
  .dependsOn(`play-ws-standalone`)

lazy val `play-ahc-ws` = project
  .in(file("play-ahc-ws"))
  .enablePlugins(PlayLibrary)
  .settings(scalaVersion := "2.11.8")
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play" % PlayVersion,
    "com.typesafe.play" %% "play-java" % PlayVersion))
  .settings(libraryDependencies ++= specsBuild.map(_ % Test))
  .dependsOn(`play-ws`, `play-ahc-ws-standalone`)

lazy val `play-ws-integration-tests` = project
  .in(file("play-ws-integration-tests"))
  .enablePlugins(PlayLibrary)
  .settings(scalaVersion := "2.11.8")
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play" % PlayVersion,
    "com.typesafe.play" %% "play-java" % PlayVersion))
  .settings(libraryDependencies ++= specsBuild.map(_ % Test) ++ playTest)
  .dependsOn(`play-ahc-ws`)

playBuildRepoName in ThisBuild := "play-ws"

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

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

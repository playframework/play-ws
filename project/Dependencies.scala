/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  // Should be sync with GA (.github/workflows/build-test.yml)
  val scala213 = "2.13.10"
  val scala3   = "3.2.2"

  val logback = Seq("ch.qos.logback" % "logback-core" % "1.4.6")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.24.2")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.2.0")

  val specsVersion = "4.19.2"
  val specsBuild = Seq(
    "specs2-core",
    "specs2-junit",
    "specs2-mock"
  ).map("org.specs2" %% _ % specsVersion cross CrossVersion.for3Use2_13)

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.github.sbt" % "junit-interface" % "0.13.3")

  val playJson = Seq("com.typesafe.play" %% "play-json" % "2.10.0-RC7")

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "2.0.7")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % "0.6.1")

  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % "2.1.0")

  val oauth = Seq("oauth.signpost" % "signpost-core" % "2.1.1")

  val cachecontrol = Seq("com.typesafe.play" %% "cachecontrol" % "2.2.0")

  val asyncHttpClient = Seq("org.asynchttpclient" % "async-http-client" % "2.12.3")

  val akkaStreams = Seq(("com.typesafe.akka" %% "akka-stream" % "2.6.20").cross(CrossVersion.for3Use2_13))
  val akkaHttp    = Seq(("com.typesafe.akka" %% "akka-http"   % "10.2.9").cross(CrossVersion.for3Use2_13))

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.4")

  val testDependencies = (specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = javaxInject ++ sslConfigCore ++ akkaStreams.map(
    _.exclude("com.typesafe", "*")
  ) ++ testDependencies

  val standaloneAhcWSDependencies = cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  // Should be sync with GA (.github/workflows/build-test.yml)
  val scala213 = "2.13.14"
  val scala3   = "3.3.3"

  val logback = Seq("ch.qos.logback" % "logback-core" % "1.5.6")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.25.3")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.2.1")

  val specsVersion = "4.20.7"
  val specsBuild = Seq(
    "specs2-core",
  ).map("org.specs2" %% _ % specsVersion)

  val mockito = Seq("org.mockito" % "mockito-core" % "5.12.0")

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.github.sbt" % "junit-interface" % "0.13.3")

  val playJson = Seq("com.typesafe.play" %% "play-json" % "2.10.5")

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "2.0.13")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % "0.6.1")

  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % "2.2.0")

  val oauth = Seq("oauth.signpost" % "signpost-core" % "2.1.1")

  val cachecontrol = Seq("com.typesafe.play" %% "cachecontrol" % "2.3.1")

  val asyncHttpClient = Seq("org.asynchttpclient" % "async-http-client" % "2.12.3")

  val akkaVersion = "2.6.21"

  val akkaStreams = Seq("com.typesafe.akka" %% "akka-stream" % akkaVersion)

  val backendServerTestDependencies = Seq(
    "com.typesafe.play" %% "play-netty-server" % "2.9.3",
    // Following dependencies are pulled in by play-netty-server, we just make sure
    // now that we use the same akka version here like akka-stream above.
    // This is because when upgrading the akka version in Play and play-ws here we usually release
    // a new Play version before we can bump it here, so the versions will always differ for a short time.
    // Since these deps are only used in tests it does not matter anyway.
    "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"                 % akkaVersion
  ).map(_ % Test)

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.4")

  val testDependencies =
    (mockito ++ specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = javaxInject ++ sslConfigCore ++ akkaStreams.map(
    _.exclude("com.typesafe", "*")
  ) ++ testDependencies

  val standaloneAhcWSDependencies = cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

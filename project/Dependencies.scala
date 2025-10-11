/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  // Should be sync with GA (.github/workflows/build-test.yml)
  val scala213 = "2.13.17"
  val scala3   = "3.3.6"

  val logback = Seq("ch.qos.logback" % "logback-core" % "1.5.19")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.27.6")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.3.0")

  val specsVersion = "4.20.9"
  val specsBuild   = Seq(
    "specs2-core",
  ).map("org.specs2" %% _ % specsVersion)

  val mockito = Seq("org.mockito" % "mockito-core" % "5.20.0")

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.github.sbt" % "junit-interface" % "0.13.3")

  val playJson = Seq("org.playframework" %% "play-json" % "3.0.6")

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "2.0.17")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % "0.6.1")

  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % "2.2.0")

  val oauth = Seq("oauth.signpost" % "signpost-core" % "2.1.1")

  val cachecontrol = Seq("org.playframework" %% "cachecontrol" % "3.0.1")

  val asyncHttpClient = Seq(
    ("org.asynchttpclient" % "async-http-client" % "2.12.4") // 2.12.x comes with outdated netty-reactive-streams, so we ...
      .exclude("com.typesafe.netty", "netty-reactive-streams"), // ... exclude it and pull in ...
    "com.typesafe.netty" % "netty-reactive-streams" % "2.0.15", // ... a newer version ourselves (ahc v3 will drop that dependency)
  )

  val pekkoVersion = "1.0.3"

  val pekkoStreams = Seq("org.apache.pekko" %% "pekko-stream" % pekkoVersion)

  val backendServerTestDependencies = Seq(
    "org.playframework" %% "play-netty-server" % "3.0.9",
    // Following dependencies are pulled in by play-netty-server, we just make sure
    // now that we use the same pekko version here like pekko-stream above.
    // This is because when upgrading the pekko version in Play and play-ws here we usually release
    // a new Play version before we can bump it here, so the versions will always differ for a short time.
    // Since these deps are only used in tests it does not matter anyway.
    "org.apache.pekko" %% "pekko-actor-typed"           % pekkoVersion,
    "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion,
    "org.apache.pekko" %% "pekko-slf4j"                 % pekkoVersion
  ).map(_ % Test)

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.4")

  val testDependencies =
    (mockito ++ specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = javaxInject ++ sslConfigCore ++ pekkoStreams ++ testDependencies

  val standaloneAhcWSDependencies = cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

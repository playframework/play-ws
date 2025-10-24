/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  // Should be sync with GA (.github/workflows/build-test.yml)
  val scala213 = "2.13.17"
  val scala3   = "3.3.7"

  val logback = Seq("ch.qos.logback" % "logback-core" % "1.5.20")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.27.6")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.3.0")

  val specsVersion = "4.23.0"
  val specsBuild   = Seq(
    "specs2-core",
  ).map("org.specs2" %% _ % specsVersion)

  val mockito = Seq("org.mockito" % "mockito-core" % "5.20.0")

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.github.sbt" % "junit-interface" % "0.13.3")

  val playJson = Seq("org.playframework" %% "play-json" % "3.1.0-M4")

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "2.0.17")

  val jakartaInject = Seq("jakarta.inject" % "jakarta.inject-api" % "2.0.1")

  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % "0.7.0")

  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % "2.4.0")

  val oauth = Seq("oauth.signpost" % "signpost-core" % "2.1.1")

  val cachecontrol = Seq("org.playframework" %% "cachecontrol" % "3.1.0-M2")

  val nettyVersion    = "4.1.128.Final" // Keep in sync with the netty version netty-reactive-streams uses (see below)
  val asyncHttpClient = Seq(
    ("org.asynchttpclient" % "async-http-client" % "3.0.3") // 2.12.x comes with outdated netty-reactive-streams and netty, so we ...
      .exclude("com.typesafe.netty", "netty-reactive-streams") // ... exclude netty-reactive-streams and ...
      .excludeAll(ExclusionRule("io.netty")), // ... also exclude all netty dependencies and pull in ...
    "com.typesafe.netty" % "netty-reactive-streams" % "2.0.16", // ... a new netty-reactive-streams (ahc v3 will drop it btw) ...
    "io.netty" % "netty-codec-http" % nettyVersion, // ... and the (up-to-date) netty artifacts async-http-client needs.
    "io.netty" % "netty-codec-socks"   % nettyVersion, // Same.
    "io.netty" % "netty-handler-proxy" % nettyVersion, // Same.
    "io.netty" % "netty-handler"       % nettyVersion, // Same.
    "io.netty" % "netty-buffer"        % nettyVersion, // Almost same - needed by async-http-client-netty-utils.

  )

  val pekkoVersion = "1.2.1"

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
    // play-json pulls in newer jackson version than pekkoVersion ships, need to override to avoid exceptions:
    // https://github.com/apache/pekko/blob/v1.2.1/project/Dependencies.scala#L110-L111
    ("com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.20.0")
      .excludeAll(ExclusionRule(organization = "org.scala-lang")),
    "org.apache.pekko" %% "pekko-slf4j" % pekkoVersion
  ).map(_ % Test)

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.4")

  val testDependencies =
    (mockito ++ specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = jakartaInject ++ sslConfigCore ++ pekkoStreams ++ testDependencies

  val standaloneAhcWSDependencies = cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

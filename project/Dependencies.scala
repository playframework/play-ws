/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  val scala213Version   = "2.13.18"
  val scala33LTSVersion = "3.3.8"
  val scala39LTSVersion = "3.9.0"
  val scala3NextVersion = "3.10.0-RC3"

  val publishedScalaVersions = Seq(scala213Version, scala33LTSVersion)

  private val scalaVersionAliases = Map(
    "2.13.x" -> scala213Version,
    "3.3.x"  -> scala33LTSVersion,
    "3.9.x"  -> scala39LTSVersion,
    "3.next" -> scala3NextVersion,
  )

  def resolveScalaVersion(version: String): String = scalaVersionAliases.getOrElse(version, version)

  val logback = Seq("ch.qos.logback" % "logback-core" % "1.6.5")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.27.7")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.3.0")

  val specsVersion = "4.23.0"
  val specsBuild   = Seq(
    "specs2-core",
  ).map("org.specs2" %% _ % specsVersion)

  val mockito = Seq("org.mockito" % "mockito-core" % "5.24.0")

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.github.sbt" % "junit-interface" % "0.13.3")

  val playJson = Seq("org.playframework" %% "play-json" % "3.1.0-M10+89-827e146f-SNAPSHOT")

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "2.0.20")

  val jakartaInject = Seq("jakarta.inject" % "jakarta.inject-api" % "2.0.1")

  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % "0.7.2")

  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % "2.5.0")

  val oauth = Seq("oauth.signpost" % "signpost-core" % "2.1.1")

  val cachecontrol = Seq("org.playframework" %% "cachecontrol" % "3.1.0-M3+2-5a8b88cf-SNAPSHOT")

  val asyncHttpClient = Seq("org.asynchttpclient" % "async-http-client" % "3.0.14")

  // Keep in sync with the Netty version tested by the AHC version above. Netty
  // is bundled into Play WS's shaded AHC jar, so applications cannot override it.
  val nettyVersion   = "4.2.18.Final"
  val nettyOverrides = Seq(
    "netty-buffer",
    "netty-codec-base",
    "netty-codec-compression",
    "netty-codec-dns",
    "netty-codec-http",
    "netty-codec-http2",
    "netty-codec-socks",
    "netty-common",
    "netty-handler",
    "netty-handler-proxy",
    "netty-resolver",
    "netty-resolver-dns",
    "netty-transport",
    "netty-transport-native-unix-common"
  ).map("io.netty" % _ % nettyVersion)

  val pekkoVersion = "2.0.0-M4"

  val pekkoStreams = Seq("org.apache.pekko" %% "pekko-stream" % pekkoVersion)

  val backendServerTestDependencies = Seq(
    "org.playframework" %% "play-netty-server" % "3.0.12",
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

  val reactiveStreamsTck = Seq("org.reactivestreams" % "reactive-streams-tck" % "1.0.4" % Test)

  // AHC 2 is an OAuth compatibility reference for tests only.
  // Production AHC 3 and its Netty classes come from the separate shaded jar.
  val asyncHttpClient2Test = Seq("org.asynchttpclient" % "async-http-client" % "2.16.1" % Test)

  val testDependencies =
    (mockito ++ specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = jakartaInject ++ sslConfigCore ++ pekkoStreams ++ testDependencies

  val standaloneAhcWSDependencies =
    cachecontrol ++ slf4jApi ++ reactiveStreams ++ reactiveStreamsTck ++ asyncHttpClient2Test ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

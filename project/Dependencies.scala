/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {
  val logback = Seq("ch.qos.logback" % "logback-core" % "1.2.3")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.8.0")

  val awaitility = Seq("org.awaitility" % "awaitility" % "3.0.0")

  val specsVersion = "3.8.6"
  val specsBuild = Seq(
    "specs2-core",
    "specs2-junit",
    "specs2-mock"
  ).map("org.specs2" %% _ % specsVersion)

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.novocode" % "junit-interface" % "0.11")

  val scalaJava8Compat = Seq("org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0")

  val playJsonVersion = "2.6.10"
  val playJson = Seq("com.typesafe.play" %% "play-json" % playJsonVersion)

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.25")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigVersion = "0.2.2"
  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % sslConfigVersion)

  val scalaXmlVersion = "1.0.6"
  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion)

  val signpostVersion = "1.2.1.2"
  val oauth = Seq("oauth.signpost" % "signpost-core" % signpostVersion)

  val cachecontrolVersion = "1.1.4"
  val cachecontrol = Seq("com.typesafe.play" %% "cachecontrol" % cachecontrolVersion)

  val asyncHttpClientVersion = "2.0.39"
  val asyncHttpClient = Seq("org.asynchttpclient" % "async-http-client" % asyncHttpClientVersion)

  val akkaVersion = "2.5.17"
  val akkaStreams = Seq("com.typesafe.akka" %% "akka-stream" % akkaVersion)
  val akkaHttp = Seq("com.typesafe.akka" %% "akka-http" % "10.0.14")

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.2")

  val testDependencies = (specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = javaxInject ++ sslConfigCore ++ akkaStreams ++ testDependencies

  val standaloneAhcWSDependencies = scalaJava8Compat ++ cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

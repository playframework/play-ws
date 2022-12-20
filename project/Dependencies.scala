/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {
  val logback = Seq("ch.qos.logback" % "logback-core" % "1.2.3")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.14.0")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.0.1")

  val specsVersion = "4.8.1"
  val specsBuild = Seq(
    "specs2-core",
    "specs2-junit",
    "specs2-mock"
  ).map("org.specs2" %% _ % specsVersion)

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.novocode" % "junit-interface" % "0.11")

  def scalaJava8Compat(scalaVersion: String) = Seq("org.scala-lang.modules" %% "scala-java8-compat" % (CrossVersion.partialVersion(scalaVersion) match {
     case Some((2, major)) if major >= 13 => "1.0.2"
     case _                               => "0.9.1"
   }))

  val playJsonVersion = "2.8.2"
  val playJson = Seq("com.typesafe.play" %% "play-json" % playJsonVersion)

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.29")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigVersion = "0.4.3"
  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % sslConfigVersion)

  val scalaXmlVersion = "2.1.0"
  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion)

  val signpostVersion = "1.2.1.2"
  val oauth = Seq("oauth.signpost" % "signpost-core" % signpostVersion)

  val cachecontrolVersion = "2.0.0"
  val cachecontrol = Seq("com.typesafe.play" %% "cachecontrol" % cachecontrolVersion)

  val asyncHttpClientVersion = "2.10.5"
  val asyncHttpClient = Seq("org.asynchttpclient" % "async-http-client" % asyncHttpClientVersion)

  val akkaVersion = "2.6.1"
  val akkaStreams = Seq("com.typesafe.akka" %% "akka-stream" % akkaVersion)
  val akkaHttp = Seq("com.typesafe.akka" %% "akka-http" % "10.1.11")

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.3")

  val testDependencies = (specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = javaxInject ++ sslConfigCore ++ akkaStreams ++ testDependencies

  def standaloneAhcWSDependencies(scalaVersion: String) = scalaJava8Compat(scalaVersion) ++ cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

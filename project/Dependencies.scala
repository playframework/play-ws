/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  // must align with versions in .travis.yml
  val scala212 = "2.12.10"
  val scala213 = "2.13.1"

  val logback = Seq("ch.qos.logback" % "logback-core" % "1.2.3")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.16.1")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.0.3")

  val specsVersion = "4.10.3"
  val specsBuild = Seq(
    "specs2-core",
    "specs2-junit",
    "specs2-mock"
  ).map("org.specs2" %% _ % specsVersion)

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.novocode" % "junit-interface" % "0.11")

  val scalaJava8Compat = Seq("org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1")

  val playJson = Seq("com.typesafe.play" %% "play-json" % "2.9.0")

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.30")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % "0.4.2")

  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % "1.3.0")

  val oauth = Seq("oauth.signpost" % "signpost-core" % "1.2.1.2")

  val cachecontrol = Seq("com.typesafe.play" %% "cachecontrol" % "2.0.0")

  val asyncHttpClient = Seq("org.asynchttpclient" % "async-http-client" % "2.12.1")

  val akkaStreams = Seq("com.typesafe.akka" %% "akka-stream" % "2.6.6")
  val akkaHttp    = Seq("com.typesafe.akka" %% "akka-http" % "10.1.12")

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.3")

  val testDependencies = (specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = javaxInject ++ sslConfigCore ++ akkaStreams ++ testDependencies

  val standaloneAhcWSDependencies = scalaJava8Compat ++ cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

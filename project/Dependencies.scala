/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  // must align with versions in .travis.yml
  val scala213 = "2.13.6"

  val logback = Seq("ch.qos.logback" % "logback-core" % "1.2.6")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.21.0")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.1.1")

  val specsVersion = "4.12.4-js-ec"
  val specsBuild = Seq(
    "specs2-core",
    "specs2-junit",
    "specs2-mock"
  ).map("org.specs2" %% _ % specsVersion)

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.github.sbt" % "junit-interface" % "0.13.3")

  val scalaJava8Compat = Seq("org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2")

  val playJson = Seq("com.typesafe.play" %% "play-json" % "2.9.2")

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.32")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % "0.6.0")

  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % "2.0.1")

  val oauth = Seq("oauth.signpost" % "signpost-core" % "2.1.1")

  val cachecontrol = Seq("com.typesafe.play" %% "cachecontrol" % "2.1.0")

  val asyncHttpClient = Seq("org.asynchttpclient" % "async-http-client" % "2.12.3")

  val akkaStreams = Seq("com.typesafe.akka" %% "akka-stream" % "2.6.17")
  val akkaHttp    = Seq("com.typesafe.akka" %% "akka-http" % "10.2.6")

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.3")

  val testDependencies = (specsBuild ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = javaxInject ++ sslConfigCore ++ akkaStreams ++ testDependencies

  val standaloneAhcWSDependencies = scalaJava8Compat ++ cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  val standaloneAhcWSXMLDependencies = scalaXml ++ testDependencies

}

/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  // Should be sync with GA (.github/workflows/build-test.yml)
  val scala213 = "2.13.8"
  val scala3   = "3.1.1"

  val logback = Seq("ch.qos.logback" % "logback-core" % "1.2.10")

  val assertj = Seq("org.assertj" % "assertj-core" % "3.22.0")

  val awaitility = Seq("org.awaitility" % "awaitility" % "4.1.1")

  val specsVersion = "4.15.0"
  val specsBuild = Seq(
    "specs2-core",
    "specs2-junit",
    "specs2-mock"
  ).map("org.specs2" %% _ % specsVersion)

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.2.0")

  val junitInterface = Seq("com.github.sbt" % "junit-interface" % "0.13.3")

  val scalaJava8Compat = Seq("org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2")

  val playJson = Seq("com.typesafe.play" %% "play-json" % "2.10.0-RC6")

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.36")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % "0.6.1")

  def scalaXml(scalaVersion: String) = Seq("org.scala-lang.modules" %% "scala-xml" % {
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, _)) => "1.3.0"
      case _            => "2.0.1"
    }
  })

  val oauth = Seq("oauth.signpost" % "signpost-core" % "2.1.1")

  val cachecontrol = Seq("com.typesafe.play" %% "cachecontrol" % "2.2.0")

  val asyncHttpClient = Seq("org.asynchttpclient" % "async-http-client" % "2.12.3")

  val akkaStreams = Seq(("com.typesafe.akka" %% "akka-stream" % "2.6.19").cross(CrossVersion.for3Use2_13))
  val akkaHttp    = Seq(("com.typesafe.akka" %% "akka-http"   % "10.2.9").cross(CrossVersion.for3Use2_13))

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.3")

  val testDependencies = (specsBuild.map(
    _.exclude("org.scala-lang.modules", "*")
  ) ++ junitInterface ++ assertj ++ awaitility ++ slf4jtest ++ logback).map(_ % Test)

  val standaloneApiWSDependencies = javaxInject ++ scalaJava8Compat ++ sslConfigCore ++ akkaStreams.map(
    _.exclude("com.typesafe", "*").exclude("org.scala-lang.modules", "*")
  ) ++ testDependencies

  val standaloneAhcWSDependencies = scalaJava8Compat ++ cachecontrol ++ slf4jApi ++ reactiveStreams ++ testDependencies

  val standaloneAhcWSJsonDependencies = playJson ++ testDependencies

  def standaloneAhcWSXMLDependencies(scalaVersion: String) = scalaXml(scalaVersion) ++ testDependencies

}

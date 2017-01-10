/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Dependencies {

  val specsVersion = "3.8.6"
  val specsBuild = Seq(
    "specs2-core",
    "specs2-junit",
    "specs2-mock"
  ).map("org.specs2" %% _ % specsVersion % Test)

  // Use the published milestone
  val playJsonVersion = "2.6.0-M1"
  val playJson = Seq(
    "com.typesafe.play" %% "play-json" % playJsonVersion,
    "com.typesafe.play" %% "play-functional" % playJsonVersion
  )

  def slf4j = Seq("org.slf4j" % "slf4j-api" % "1.7.16")

  def logback = Seq("ch.qos.logback" % "logback-classic" % "1.1.8")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigVersion = "0.2.1"
  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % sslConfigVersion)

  val scalaXmlVersion = "1.0.6"
  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion)

  val signpostVersion = "1.2.1.2"
  val oauth = Seq("oauth.signpost" % "signpost-core" % signpostVersion)

  val asyncHttpClientVersion = "2.0.11"
  val asyncHttpClient = Seq(
    "org.asynchttpclient" % "async-http-client" % asyncHttpClientVersion excludeAll ExclusionRule(organization = "org.slf4j")
  )

  val akkaVersion = "2.4.14"
  val akka = Seq("com.typesafe.akka" %% "akka-stream" % akkaVersion)

  val standaloneApiWSDependencies = javaxInject ++
      sslConfigCore ++
      akka ++
      scalaXml ++
      playJson ++
      specsBuild

  val standaloneAhcWSDependencies = slf4j ++ specsBuild

}

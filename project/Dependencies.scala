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
  ).map("org.specs2" %% _ % specsVersion)

  // Use the published milestone
  val playJsonVersion = "2.6.0-M5"
  val playJson = "com.typesafe.play" %% "play-json" % playJsonVersion

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.22")

  val slf4jtest = Seq("uk.org.lidalia" % "slf4j-test" % "1.1.0")

  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigVersion = "0.2.1"
  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % sslConfigVersion)

  val scalaXmlVersion = "1.0.6"
  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion)

  val signpostVersion = "1.2.1.2"
  val oauth = Seq(
    "oauth.signpost" % "signpost-core" % signpostVersion
  )

  val cachecontrolVersion = "1.1.2"
  val cachecontrol = Seq("com.typesafe.play" %% "cachecontrol" % cachecontrolVersion)

  val asyncHttpClientVersion = "2.0.27"
  val asyncHttpClient = Seq(
    "org.asynchttpclient" % "async-http-client" % asyncHttpClientVersion
  )

  // XXX need to use 2.3 for scala 2.10
  val akkaVersion = "2.4.14"
  val akka = Seq("com.typesafe.akka" %% "akka-stream" % akkaVersion)
  val akkaHttp = Seq("com.typesafe.akka" %% "akka-http" % "10.0.1")

  val reactiveStreams = Seq("org.reactivestreams" % "reactive-streams" % "1.0.0")

  val junitInterface = Seq("com.novocode" % "junit-interface" % "0.11")

  val jcache = Seq("javax.cache" % "cache-api" % "1.0.0")

  // https://mvnrepository.com/artifact/com.github.ben-manes.caffeine/jcache
  val caffeineVersion = "2.3.5"
  val caffeine = Seq("com.github.ben-manes.caffeine" % "jcache" % caffeineVersion)

  val standaloneApiWSDependencies = javaxInject ++
      sslConfigCore ++
      akka ++
      scalaXml :+
      playJson

  val standaloneAhcWSDependencies = cachecontrol ++ jcache ++ slf4jApi ++ reactiveStreams
}

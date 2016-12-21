name := "standalone"

organization := "com.example"

version := "1.0.0"

scalaVersion := "2.12.1"

val playWsVersion = "2.6.0-SNAPSHOT"
libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.22"

scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:experimental.macros",
    "-unchecked",
    //"-Ywarn-unused-import",
    "-Ywarn-nullary-unit",
    "-Xfatal-warnings",
    "-Xlint",
    //"-Yinline-warnings",
    "-Ywarn-dead-code",
    "-Xfuture")

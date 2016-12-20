name := "standalone"

organization := "com.example"

version := "1.0.0"

scalaVersion := "2.11.8"

val playWsVersion = "2.6.0-SNAPSHOT"
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion
)

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
    "-Ylog-classpath",
    "-Xfatal-warnings",
    "-Xlint",
    //"-Yinline-warnings",
    "-Ywarn-dead-code",
    "-Xfuture")

name := "standalone"

organization := "com.example"

version := "1.0.0"

scalaVersion := "2.12.1"

val playWsVersion = "2.6.0-SNAPSHOT"
libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.22"

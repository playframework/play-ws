import sbt._

object Dependencies {
  import interplay.PlayBuildBase.autoImport.playVersion

  // Latest snapshot is in
  // https://oss.sonatype.org/content/repositories/snapshots/com/typesafe/play/play_2.11/maven-metadata.xml
  val PlayVersion = playVersion("2.6.0-2016-12-11-1904442-SNAPSHOT")
  //val PlayVersion = playVersion("2.5.10")

  val specsVersion = "3.8.6"
  val specsBuild = Seq(
    "specs2-core",
    "specs2-junit",
    "specs2-mock"
  ).map("org.specs2" %% _ % specsVersion)

  val logback = "ch.qos.logback" % "logback-classic" % "1.1.7"

  val playJsonVersion = PlayVersion
  val playJson = Seq(
    "com.typesafe.play" %% "play-json" % playJsonVersion,
    "com.typesafe.play" %% "play-functional" % playJsonVersion
  )

  // Build this from source until 2.12 is published.
  // See https://github.com/playframework/play-iteratees/pull/6
  val playIterateesVersion = "2.6.0"
  val playIteratees = Seq("com.typesafe.play" %% "play-iteratees" % playIterateesVersion)

  val guiceVersion = "4.0"
  val guiceDeps = Seq(
    "com.google.inject" % "guice" % guiceVersion,
    "com.google.inject.extensions" % "guice-assistedinject" % guiceVersion
  )

  // https://mvnrepository.com/artifact/javax.inject/javax.inject
  val javaxInject = Seq("javax.inject" % "javax.inject" % "1")

  val sslConfigVersion = "0.2.1"
  val sslConfigCore = Seq("com.typesafe" %% "ssl-config-core" % sslConfigVersion)

  val scalaXmlVersion = "1.0.6"
  val scalaXml = Seq("org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion)

  val oauth = Seq(
    "signpost-core", "signpost-commonshttp4").map("oauth.signpost" % _ % "1.2.1.2"
  )

  // https://mvnrepository.com/artifact/org.reactivestreams/reactive-streams
  val reactiveStreams = Seq(
    "org.reactivestreams" % "reactive-streams" % "1.0.0"
  )

  val asyncHttpClientVersion = "2.0.11"
  val asyncHttpClient = Seq(
    "org.asynchttpclient" % "async-http-client" % asyncHttpClientVersion excludeAll(
      ExclusionRule(organization = "org.slf4j"),
      ExclusionRule(organization = "org.apache.http")
    )
  )

  val akkaVersion = "2.4.14"
  val akka = Seq(
    "akka-stream", "akka-slf4j").map("com.typesafe.akka" %% _ % akkaVersion
  )

  val playTest = Seq(
    logback % Test,
    "com.typesafe.play" %% "play-test" % PlayVersion % Test,
    "com.typesafe.play" %% "play-specs2" % PlayVersion % Test
  ) ++ specsBuild.map(_ % Test)

  def wsDependencies(scalaVersion: String) =
    javaxInject ++
      sslConfigCore ++
      akka ++
      scalaXml ++
      playJson ++
      playIteratees ++
      reactiveStreams ++
      playTest

  val playDeps = Seq(
    "com.typesafe.play" %% "play" % PlayVersion,
    "com.typesafe.play" %% "play-java" % PlayVersion)

  def excludeNetty(module: ModuleID): ModuleID =
    module.excludeAll(ExclusionRule(organization = "io.netty"))

}

import Dependencies._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import java.io.File
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

import scalariform.formatter.preferences._

//---------------------------------------------------------------
// Shading and Project Settings
//---------------------------------------------------------------

val scala211 = "2.11.12"
val scala212 = "2.12.6"
val scala213 = "2.13.0-M3"

val previousVersion = None

resolvers ++= DefaultOptions.resolvers(snapshot = true)
resolvers in ThisBuild += Resolver.sonatypeRepo("public")

val javacSettings = Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

def mimaPreviousArtifactFor(scalaV: String, module: ModuleID): Set[ModuleID] = scalaV match {
  case sv if sv == scala213 => Set.empty
  case _ => Set(module)
}

lazy val mimaSettings = mimaDefaultSettings ++ Seq(
  mimaBinaryIssueFilters ++= Seq(
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StandaloneAhcWSResponse.getBodyAsSource"),
    ProblemFilters.exclude[MissingClassProblem]("play.api.libs.ws.package$"),
    ProblemFilters.exclude[MissingClassProblem]("play.api.libs.ws.package"),

    // Implemented as a default method at the interface
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StandaloneAhcWSResponse.getBodyAsSource"),

    // Added to have better parity between Java and Scala APIs
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getBody"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getAuth"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getMethod"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.setAuth"),

    // Added in #268 for 2.0.0
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.setUrl"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.api.libs.ws.StandaloneWSRequest.withUrl"),

    // Now have a default implementation at the interface
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StandaloneAhcWSRequest.getPassword"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StandaloneAhcWSRequest.getUsername"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StandaloneAhcWSRequest.setAuth"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StandaloneAhcWSRequest.getScheme"),

    // Add getUri method
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSResponse.getUri"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.api.libs.ws.StandaloneWSResponse.uri"),

    // Using Optional and Duration for Java APIs
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StandaloneAhcWSRequest.getRequestTimeoutDuration"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.ahc.StandaloneAhcWSRequest.getFollowRedirects"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.ahc.StandaloneAhcWSRequest.getCalculator"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.ahc.StandaloneAhcWSRequest.getContentType"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getRequestTimeoutDuration"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.StandaloneWSRequest.getFollowRedirects"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.StandaloneWSRequest.getPassword"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.StandaloneWSRequest.getCalculator"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.StandaloneWSRequest.getUsername"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.StandaloneWSRequest.getContentType"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.libs.ws.StandaloneWSRequest.getScheme"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getRequestTimeout"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getFollowRedirects"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getCalculator"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getContentType"),

    // Update async-http-client to 2.5.2
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StreamedResponse.this"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.libs.ws.ahc.StandaloneAhcWSResponse.asCookie"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.StandaloneAhcWSRequest.asCookie"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.StandaloneAhcWSRequest.asCookie"),
    ProblemFilters.exclude[MissingTypesProblem]("play.api.libs.ws.ahc.AhcWSClientConfig$"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.apply"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.copy"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.this"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.StreamedResponse.asCookie"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.StreamedResponse.asCookie"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.StreamedResponse.this"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.WSCookieConverter.asCookie"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.WSCookieConverter.asCookie"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.WSCookieConverter.asCookie"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.api.libs.ws.ahc.WSCookieConverter.asCookie"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.DefaultStreamedAsyncHandler.onHeadersReceived"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.StandaloneAhcWSResponse.asCookie"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.StandaloneAhcWSResponse.asCookie"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.api.libs.ws.ahc.CookieBuilder.useLaxCookieEncoder"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.AsyncCacheableConnection.debug"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.Debug.debug"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.Debug.debug"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.api.libs.ws.ahc.cache.Debug.debug"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.copy$default$2"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.ahcHeaders"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.copy"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.getHeaders"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.headers"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.this"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.getHeader"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.AhcHttpCache.calculateTimeToLive"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.AhcHttpCache.debug"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.AhcHttpCache.generateOriginResponse"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.CacheableResponseBuilder.accumulate"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.CacheableResponseBuilder.this"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.AsyncCachingHandler.debug"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.AsyncCachingHandler.generateTimeoutResponse"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.AsyncCachingHandler.onHeadersReceived"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.AsyncCachingHandler.this"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.apply"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.apply"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.CacheableResponse.apply"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.TimeoutResponse.generateTimeoutResponse"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.api.libs.ws.ahc.cache.TimeoutResponse.generateTimeoutResponse"),
    ProblemFilters.exclude[MissingClassProblem]("play.api.libs.ws.ahc.cache.CacheableHttpResponseHeaders"),
    ProblemFilters.exclude[MissingClassProblem]("play.api.libs.ws.ahc.cache.CacheableHttpResponseHeaders$"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.CachingAsyncHttpClient.debug"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.CachingAsyncHttpClient.generateTimeoutResponse"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.BackgroundAsyncHandler.debug"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.ws.ahc.cache.BackgroundAsyncHandler.onHeadersReceived"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.cache.BackgroundAsyncHandler.this")
  )
)

lazy val commonSettings = mimaSettings ++ Seq(
  organization := "com.typesafe.play",
  scalaVersion := scala212,
  crossScalaVersions := Seq(scala213, scala212, scala211),
  scalacOptions in (Compile, doc) ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Ywarn-unused-import",
    "-Ywarn-nullary-unit",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-dead-code"
  ),
  // Work around 2.12 bug which prevents javadoc in nested java classes from compiling.
  scalacOptions in (Compile, doc) ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 12 =>
        Seq("-no-java-comments")
      case _ =>
        Nil
    }
  },
  pomExtra := (
    <url>https://github.com/playframework/play-ws</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com/playframework/play-ws.git</url>
        <connection>scm:git:git@github.com/playframework/play-ws.git</connection>
      </scm>
      <developers>
        <developer>
          <id>playframework</id>
          <name>Play Team</name>
          <url>http://playframework.com/</url>
        </developer>
      </developers>),
  javacOptions in (Compile, doc) ++= javacSettings,
  javacOptions in Test ++= javacSettings,
  javacOptions in IntegrationTest ++= javacSettings
)

val formattingSettings = Seq(
  scalariformAutoformat := true,
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(SpacesAroundMultiImports, true)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(DoubleIndentConstructorArguments, true)
)

val disableDocs = Seq[Setting[_]](
  sources in (Compile, doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false
)

val disablePublishing = Seq[Setting[_]](
  publishArtifact := false,
  skip in publish := true,
  crossScalaVersions := Seq(scala212)
)

lazy val shadeAssemblySettings = commonSettings ++ Seq(
  crossScalaVersions := Seq(scala212),
  assemblyOption in assembly ~= (_.copy(includeScala = false)),
  test in assembly := {},
  assemblyOption in assembly ~= {
    _.copy(includeScala = false)
  },
  assemblyJarName in assembly := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((epoch, major)) =>
        s"${name.value}.jar" // we are only shading java
      case _ =>
        sys.error("Cannot find valid scala version!")
    }
  },
  target in assembly := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((epoch, major)) =>
        baseDirectory.value.getParentFile / "target" / s"$epoch.$major"
      case _ =>
        sys.error("Cannot find valid scala version!")
    }
  },
  crossPaths := false // only useful for Java
)

val ahcMerge: MergeStrategy = new MergeStrategy {
  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    import scala.collection.JavaConverters._
    val file = MergeStrategy.createMergeTarget(tempDir, path)
    val lines = java.nio.file.Files.readAllLines(files.head.toPath).asScala
    lines.foreach { line =>
      // In AsyncHttpClientConfigDefaults.java, the shading renames the resource keys
      // so we have to manually tweak the resource file to match.
      val shadedline = line.replace("org.asynchttpclient", "play.shaded.ahc.org.asynchttpclient")
      IO.append(file, line)
      IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
      IO.append(file, shadedline)
      IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    }
    Right(Seq(file -> path))
  }

  override val name: String = "ahcMerge"
}

import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, NodeSeq, Node => XNode}

def dependenciesFilter(n: XNode) = new RuleTransformer(new RewriteRule {
  override def transform(n: XNode): NodeSeq = n match {
    case e: Elem if e.label == "dependencies" => NodeSeq.Empty
    case other => other
  }
}).transform(n).head

//---------------------------------------------------------------
// Shaded AsyncHttpClient implementation
//---------------------------------------------------------------

lazy val `shaded-asynchttpclient` = project.in(file("shaded/asynchttpclient"))
  .settings(commonSettings)
  .settings(shadeAssemblySettings)
  .settings(
    libraryDependencies ++= asyncHttpClient,
    name := "shaded-asynchttpclient",
    logLevel in assembly := Level.Error,
    assemblyMergeStrategy in assembly := {
      val NettyPropertiesPath = "META-INF" + File.separator + "io.netty.versions.properties"
      ({
        case NettyPropertiesPath =>
          MergeStrategy.first
        case ahcProperties if ahcProperties.endsWith("ahc-default.properties") =>
          ahcMerge
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      }: String => MergeStrategy)
    },
    //logLevel in assembly := Level.Debug,
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.asynchttpclient.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("io.netty.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("javassist.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("com.typesafe.netty.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.zap("org.reactivestreams.**").inAll,
      ShadeRule.zap("org.slf4j.**").inAll
    ),

    // https://stackoverflow.com/questions/24807875/how-to-remove-projectdependencies-from-pom
    // Remove dependencies from the POM because we have a FAT jar here.
    makePomConfiguration := makePomConfiguration.value.withProcess(process = dependenciesFilter),
    //ivyXML := <dependencies></dependencies>,
    //ivyLoggingLevel := UpdateLogging.Full,
    //logLevel := Level.Debug,

    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeBin = false, includeScala = false),
    packageBin in Compile := assembly.value
  )

//---------------------------------------------------------------
// Shaded oauth
//---------------------------------------------------------------

lazy val `shaded-oauth` = project.in(file("shaded/oauth"))
  .settings(commonSettings)
  .settings(shadeAssemblySettings)
  .settings(
    libraryDependencies ++= oauth,
    name := "shaded-oauth",
    //logLevel in assembly := Level.Debug,
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("oauth.**" -> "play.shaded.oauth.@0").inAll,
      ShadeRule.rename("org.apache.commons.**" -> "play.shaded.oauth.@0").inAll
    ),

    // https://stackoverflow.com/questions/24807875/how-to-remove-projectdependencies-from-pom
    // Remove dependencies from the POM because we have a FAT jar here.
    makePomConfiguration := makePomConfiguration.value.withProcess(process = dependenciesFilter),

    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeBin = false, includeScala = false),
    packageBin in Compile := assembly.value
  )

// Make the shaded version of AHC available downstream
val shadedAhcSettings = Seq(
  unmanagedJars in Compile += (packageBin in (`shaded-asynchttpclient`, Compile)).value
)

val shadedOAuthSettings = Seq(
  unmanagedJars in Compile += (packageBin in (`shaded-oauth`, Compile)).value
)

//---------------------------------------------------------------
// Shaded aggregate project
//---------------------------------------------------------------

lazy val shaded = Project(id = "shaded", base = file("shaded") )
  .aggregate(
    `shaded-asynchttpclient`,
    `shaded-oauth`
  ).disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    disableDocs,
    disablePublishing,
  )

//---------------------------------------------------------------
// WS API
//---------------------------------------------------------------

// WS API, no play dependencies
lazy val `play-ws-standalone` = project
  .in(file("play-ws-standalone"))
  .settings(commonSettings ++ Seq(
    libraryDependencies ++= standaloneApiWSDependencies,
    mimaPreviousArtifacts := mimaPreviousArtifactFor(scalaVersion.value, "com.typesafe.play" %% "play-ws-standalone" % "1.0.0"))
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Shaded AsyncHttpClient implementation of WS
//---------------------------------------------------------------

def addShadedDeps(deps: Seq[xml.Node], node: xml.Node): xml.Node = {
  node match {
    case elem: xml.Elem =>
      val child = if (elem.label == "dependencies") {
        elem.child ++ deps
      } else {
        elem.child.map(addShadedDeps(deps, _))
      }
      xml.Elem(elem.prefix, elem.label, elem.attributes, elem.scope, false, child: _*)
    case _ =>
      node
  }
}

// Standalone implementation using AsyncHttpClient
lazy val `play-ahc-ws-standalone` = project
  .in(file("play-ahc-ws-standalone"))
  .settings(commonSettings ++ formattingSettings ++ shadedAhcSettings ++ shadedOAuthSettings ++ Seq(
    fork in Test := true,
    testOptions in Test := Seq(
      Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
    libraryDependencies ++= standaloneAhcWSDependencies,
    mimaPreviousArtifacts := mimaPreviousArtifactFor(scalaVersion.value, "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.0"),
    // This will not work if you do a publishLocal, because that uses ivy...
    pomPostProcess := {
      (node: xml.Node) => addShadedDeps(List(
        <dependency>
          <groupId>com.typesafe.play</groupId>
          <artifactId>shaded-asynchttpclient</artifactId>
          <version>{version.value}</version>
        </dependency>,
        <dependency>
          <groupId>com.typesafe.play</groupId>
          <artifactId>shaded-oauth</artifactId>
          <version>{version.value}</version>
        </dependency>
      ), node)
    }
  ))
  .dependsOn(
    `play-ws-standalone`
  ).disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// JSON Readables and Writables
//---------------------------------------------------------------

lazy val `play-ws-standalone-json` = project
  .in(file("play-ws-standalone-json"))
  .settings(commonSettings)
  .settings(formattingSettings)
  .settings(mimaPreviousArtifacts := mimaPreviousArtifactFor(scalaVersion.value, "com.typesafe.play" %% "play-ws-standalone-json" % "1.0.0"))
  .settings(
    fork in Test := true,
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v"))
  )
  .settings(libraryDependencies ++= standaloneAhcWSJsonDependencies)
  .dependsOn(
    `play-ws-standalone`
  ).disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// XML Readables and Writables
//---------------------------------------------------------------

lazy val `play-ws-standalone-xml` = project
  .in(file("play-ws-standalone-xml"))
  .settings(commonSettings)
  .settings(formattingSettings)
  .settings(mimaPreviousArtifacts := mimaPreviousArtifactFor(scalaVersion.value, "com.typesafe.play" %% "play-ws-standalone-xml" % "1.0.0"))
  .settings(
    fork in Test := true,
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
    libraryDependencies ++= standaloneAhcWSXMLDependencies
  )
  .dependsOn(
    `play-ws-standalone`
  ).disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Integration Tests
//---------------------------------------------------------------

lazy val `integration-tests` = project.in(file("integration-tests"))
  .settings(commonSettings)
  .settings(formattingSettings)
  .settings(disableDocs)
  .settings(disablePublishing)
  .settings(
    crossScalaVersions := Seq(scala213, scala212, scala211),
    fork in Test := true,
    concurrentRestrictions += Tags.limitAll(1), // only one integration test at a time
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
    libraryDependencies ++= akkaHttp.map(_ % Test) ++ testDependencies
  )
  .settings(shadedAhcSettings)
  .settings(shadedOAuthSettings)
  .dependsOn(
    `play-ahc-ws-standalone`,
    `play-ws-standalone-json`,
    `play-ws-standalone-xml`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Root Project
//---------------------------------------------------------------

lazy val root = project
  .in(file("."))
  .settings(
    name := "play-ws-standalone-root",
    // otherwise same as orgname, and "sonatypeList"
    // says "No staging profile is found for com.typesafe.play"
    sonatypeProfileName := "com.typesafe"
  )
  .settings(commonSettings)
  .settings(formattingSettings)
  .settings(disableDocs)
  .settings(disablePublishing)
  .aggregate(
    `shaded`,
    `play-ws-standalone`,
    `play-ws-standalone-json`,
    `play-ws-standalone-xml`,
    `play-ahc-ws-standalone`,
    `integration-tests`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Release
//---------------------------------------------------------------
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

// This automatically selects the snapshots or staging repository
// according to the version value.
publishTo in ThisBuild := Some(sonatypeDefaultResolver.value)

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeRelease"),
  pushChanges
)

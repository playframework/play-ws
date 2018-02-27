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
val scala212 = "2.12.4"

val previousVersion = None

resolvers ++= DefaultOptions.resolvers(snapshot = true)
resolvers in ThisBuild += Resolver.sonatypeRepo("public")

val javacSettings = Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

lazy val mimaSettings = mimaDefaultSettings ++ Seq(
  mimaBinaryIssueFilters ++= Seq(
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.libs.ws.ahc.StandaloneAhcWSResponse.getBodyAsSource"),
    ProblemFilters.exclude[MissingClassProblem]("play.api.libs.ws.package$"),
    ProblemFilters.exclude[MissingClassProblem]("play.api.libs.ws.package")
  )
)

lazy val commonSettings = mimaSettings ++ Seq(
  organization := "com.typesafe.play",
  scalaVersion := scala212,
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
      case Some((2, v)) if v == 12 =>
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

lazy val crossBuildSettings = Seq(
  crossScalaVersions := Seq(scala212, scala211)
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
  // The above is enough for Maven repos but it doesn't prevent publishing of ivy.xml files
  publish := {},
  publishLocal := {}
)

lazy val shadeAssemblySettings = Seq(
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
  // Since these are Java libraries, disable some Scala and cross-building settings
  // Note we can't add crossScalaPaths = None or Seq("2.12.4")  here due to
  // https://github.com/sbt/sbt-release/issues/219. Instead we have to have it as
  // a separate setting.
  releaseCrossBuild := false,
  crossPaths := false,
  autoScalaLibrary := false
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
        case "ahc-default.properties" =>
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
  .settings(disableDocs)
  .settings(disablePublishing)
  .aggregate(
    `shaded-asynchttpclient`,
    `shaded-oauth`
  ).disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// WS API
//---------------------------------------------------------------

// WS API, no play dependencies
lazy val `play-ws-standalone` = project
  .in(file("play-ws-standalone"))
  .settings(commonSettings)
  .settings(crossBuildSettings)
  .settings(
    mimaPreviousArtifacts := Set("com.typesafe.play" %% "play-ws-standalone" % "1.0.0"),
    libraryDependencies ++= standaloneApiWSDependencies
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
  .settings(commonSettings)
  .settings(crossBuildSettings)
  .settings(formattingSettings)
  .settings(mimaPreviousArtifacts := Set("com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.0"))
  .settings(
    fork in Test := true,
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v"))
  )
  .settings(libraryDependencies ++= standaloneAhcWSDependencies)
  .settings(shadedAhcSettings)
  .settings(shadedOAuthSettings)
  .settings(
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
  )
  .dependsOn(
    `play-ws-standalone`
  ).aggregate(
    `shaded`
  ).disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// JSON Readables and Writables
//---------------------------------------------------------------

lazy val `play-ws-standalone-json` = project
  .in(file("play-ws-standalone-json"))
  .settings(commonSettings)
  .settings(crossBuildSettings)
  .settings(formattingSettings)
  .settings(mimaPreviousArtifacts := Set("com.typesafe.play" %% "play-ws-standalone-json" % "1.0.0"))
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
  .settings(crossBuildSettings)
  .settings(formattingSettings)
  .settings(mimaPreviousArtifacts := Set("com.typesafe.play" %% "play-ws-standalone-xml" % "1.0.0"))
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
  .settings(crossBuildSettings)
  .settings(formattingSettings)
  .settings(disableDocs)
  .settings(disablePublishing)
  .settings(
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
  .settings(name := "play-ws-standalone-root")
  .settings(commonSettings)
  .settings(crossBuildSettings)
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

// otherwise same as orgname, and "sonatypeList" says "No staging profile is found for com.typesafe.play"
sonatypeProfileName in ThisBuild := "com.typesafe"

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
  releaseStepCommand("+sonatypeReleaseAll"),
  pushChanges
)

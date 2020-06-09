import java.io.File

import Dependencies._

import com.typesafe.tools.mima.core.ProblemFilters
import com.typesafe.tools.mima.core._
import play.ws.AutomaticModuleName
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

//---------------------------------------------------------------
// Shading and Project Settings
//---------------------------------------------------------------

resolvers ++= DefaultOptions.resolvers(snapshot = true)
resolvers in ThisBuild += Resolver.sonatypeRepo("public")
resolvers in ThisBuild += Resolver.bintrayRepo("akka", "snapshots")

// Customise sbt-dynver's behaviour to make it work with tags which aren't v-prefixed
dynverVTagPrefix in ThisBuild := false

// Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
// https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
Global / onLoad := (Global / onLoad).value.andThen { s =>
  val v = version.value
  if (dynverGitDescribeOutput.value.hasNoTags)
    throw new MessageOnlyException(
      s"Failed to derive version from git tags. Maybe run `git fetch --unshallow`? Version: $v"
    )
  s
}

val javacSettings = Seq(
  "-source",
  "1.8",
  "-target",
  "1.8",
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

def scalacOpts: Seq[String] =
  Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-Ywarn-unused:imports",
    "-Xlint:nullary-unit",
    "-Xlint",
    "-Ywarn-dead-code",
  )

lazy val mimaSettings = Seq(
  mimaPreviousArtifacts := Set(
    organization.value %% name.value % previousStableVersion.value
      .getOrElse(throw new Error("Unable to determine previous version"))
  ),
  // these exclusions are only for master branch and are targeting 2.2.x
  mimaBinaryIssueFilters ++= Seq(
    ProblemFilters.exclude[MissingTypesProblem]("play.api.libs.ws.ahc.AhcWSClientConfig$"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.<init>$default$6"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.<init>$default$8"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.apply$default$6"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.apply$default$8"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.copy$default$6"),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.copy$default$8"),
    ProblemFilters.exclude[IncompatibleSignatureProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.tupled"),
    ProblemFilters.exclude[IncompatibleSignatureProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.unapply"),
    ProblemFilters.exclude[IncompatibleSignatureProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.curried"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.apply"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.copy"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.this")
  )
)

lazy val commonSettings = Def.settings(
  organization := "com.typesafe.play",
  organizationName := "Lightbend Inc.",
  organizationHomepage := Some(url("https://www.lightbend.com/")),
  homepage := Some(url("https://www.playframework.com/documentation/latest/")),
  scmInfo := Some(ScmInfo(url("https://github.com/playframework/play-ws"), "git@github.com:playframework/play-ws.git")),
  developers += Developer("contributors",
    "Contributors",
    "https://gitter.im/playframework/",
    url("https://gitter.im/playframework/contributors")),
  licenses := Seq("Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
  scalaVersion := scala213,
  crossScalaVersions := Seq(scala213, scala212),
  scalacOptions ++= scalacOpts,
  scalacOptions in (Compile, doc) ++= Seq(
    "-Xfatal-warnings",
    // Work around 2.12 bug which prevents javadoc in nested java classes from compiling.
    "-no-java-comments",
  ),
  javacOptions in Compile ++= javacSettings,
  javacOptions in Test ++= javacSettings,
  headerLicense := {
    Some(
      HeaderLicense.Custom(
        s"Copyright (C) Lightbend Inc. <https://www.lightbend.com>"
      )
    )
  }
)


lazy val shadedCommonSettings = Seq(
  scalaVersion := scala213,
  crossScalaVersions := Seq(scala213),
  // No need to cross publish the shaded libraries
  crossPaths := false,
)

lazy val shadeAssemblySettings = commonSettings ++ shadedCommonSettings ++ Seq(
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
)

val ahcMerge: MergeStrategy = new MergeStrategy {
  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    import scala.collection.JavaConverters._
    val file  = MergeStrategy.createMergeTarget(tempDir, path)
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

import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer
import scala.xml.Elem
import scala.xml.NodeSeq
import scala.xml.{ Node => XNode }

def dependenciesFilter(n: XNode) =
  new RuleTransformer(new RewriteRule {
    override def transform(n: XNode): NodeSeq =
      n match {
        case e: Elem if e.label == "dependencies" => NodeSeq.Empty
        case other                                => other
      }
  }).transform(n).head

//---------------------------------------------------------------
// Shaded AsyncHttpClient implementation
//---------------------------------------------------------------

lazy val `shaded-asynchttpclient` = project
  .in(file("shaded/asynchttpclient"))
  .enablePlugins(Publish)
  .disablePlugins(MimaPlugin)
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
      ShadeRule.rename("javax.activation.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("com.sun.activation.**" -> "play.shaded.ahc.@0").inAll,
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

lazy val `shaded-oauth` = project
  .in(file("shaded/oauth"))
  .enablePlugins(Publish)
  .disablePlugins(MimaPlugin)
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

lazy val shaded = Project(id = "shaded", base = file("shaded"))
  .aggregate(
    `shaded-asynchttpclient`,
    `shaded-oauth`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin, HeaderPlugin, MimaPlugin)
  .settings(
    publish / skip := true,
    shadedCommonSettings,
  )

//---------------------------------------------------------------
// WS API
//---------------------------------------------------------------

// WS API, no play dependencies
lazy val `play-ws-standalone` = project
  .in(file("play-ws-standalone"))
  .enablePlugins(Publish)
  .settings(commonSettings)
  .settings(mimaSettings)
  .settings(libraryDependencies ++= standaloneApiWSDependencies)
  .settings(AutomaticModuleName.settings("play.ws.standalone"))
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
  .enablePlugins(Publish)
  .settings(
    commonSettings ++ shadedAhcSettings ++ shadedOAuthSettings ++ Seq(
      fork in Test := true,
      testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
      libraryDependencies ++= standaloneAhcWSDependencies,
      // This will not work if you do a publishLocal, because that uses ivy...
      pomPostProcess := { (node: xml.Node) =>
        addShadedDeps(
          List(
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
          ),
          node
        )
      }
    )
  )
  .settings(mimaSettings)
  .settings(AutomaticModuleName.settings("play.ws.standalone.ahc"))
  .dependsOn(
    `play-ws-standalone`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// JSON Readables and Writables
//---------------------------------------------------------------

lazy val `play-ws-standalone-json` = project
  .in(file("play-ws-standalone-json"))
  .enablePlugins(Publish)
  .settings(commonSettings)
  .settings(mimaSettings)
  .settings(
    fork in Test := true,
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
    libraryDependencies ++= standaloneAhcWSJsonDependencies
  )
  .settings(AutomaticModuleName.settings("play.ws.standalone.json"))
  .dependsOn(
    `play-ws-standalone`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// XML Readables and Writables
//---------------------------------------------------------------

lazy val `play-ws-standalone-xml` = project
  .in(file("play-ws-standalone-xml"))
  .enablePlugins(Publish)
  .settings(commonSettings)
  .settings(mimaSettings)
  .settings(
    fork in Test := true,
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
    libraryDependencies ++= standaloneAhcWSXMLDependencies
  )
  .settings(AutomaticModuleName.settings("play.ws.standalone.xml"))
  .dependsOn(
    `play-ws-standalone`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Integration Tests
//---------------------------------------------------------------

lazy val `integration-tests` = project
  .in(file("integration-tests"))
  .disablePlugins(MimaPlugin, sbtassembly.AssemblyPlugin)
  .settings(commonSettings)
  .settings(publish / skip := true)
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

//---------------------------------------------------------------
// Benchmarks (run manually)
//---------------------------------------------------------------

lazy val bench = project
  .in(file("bench"))
  .enablePlugins(JmhPlugin)
  .disablePlugins(MimaPlugin)
  .dependsOn(
    `play-ws-standalone`,
    `play-ws-standalone-json`,
    `play-ws-standalone-xml`,
    `play-ahc-ws-standalone`
  )
  .settings(commonSettings)
  .settings(scalacOptions in (Compile, doc) -= "-Xfatal-warnings")
  .settings(publish / skip := true)

//---------------------------------------------------------------
// Root Project
//---------------------------------------------------------------

lazy val root = project
  .in(file("."))
  .disablePlugins(MimaPlugin, sbtassembly.AssemblyPlugin)
  .settings(
    name := "play-ws-standalone-root",
    // otherwise same as orgname, and "sonatypeList"
    // says "No staging profile is found for com.typesafe.play"
//    sonatypeProfileName := "com.typesafe"
  )
  .settings(commonSettings)
  .settings(publish / skip := true)
  .settings(crossScalaVersions := Seq(scala213))
  .aggregate(
    `shaded`,
    `play-ws-standalone`,
    `play-ws-standalone-json`,
    `play-ws-standalone-xml`,
    `play-ahc-ws-standalone`,
    `integration-tests`,
    bench
  )

addCommandAlias(
  "validateCode",
  ";scalafmtCheck;headerCheck;test:headerCheck"
)

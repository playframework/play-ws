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

// Customise sbt-dynver's behaviour to make it work with tags which aren't v-prefixed
ThisBuild / dynverVTagPrefix := false

// Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
// https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
Global / onLoad := (Global / onLoad).value.andThen { s =>
  dynverAssertTagVersion.value
  s
}

val javacSettings = Seq(
  "--release",
  "11",
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

val scalacOpts = Def.setting[Seq[String]] {
  val sv = scalaBinaryVersion.value

  val common = Seq(
    "-release",
    "11",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  )

  if (sv == "3") {
    common
  } else {
    common ++ Seq("-Ywarn-unused:imports", "-Xlint:nullary-unit", "-Xlint", "-Ywarn-dead-code")
  }
}

lazy val mimaSettings = Seq(
  mimaPreviousArtifacts := {
    if (scalaBinaryVersion.value == "3") Set.empty[ModuleID]
    else
      Set(
        organization.value %% name.value % previousStableVersion.value
          .getOrElse(throw new Error("Unable to determine previous version"))
      )
  },
  // these exclusions are only for main branch and are targeting 2.2.x
  mimaBinaryIssueFilters ++= Seq(
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "play.api.libs.ws.ahc.AhcConfigBuilder.validateDefaultTrustManager"
    ),
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
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.ws.ahc.AhcWSClientConfig.this"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.setDisableUrlEncoding"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.libs.ws.StandaloneWSRequest.getDisableUrlEncoding"),
    ProblemFilters.exclude[ReversedMissingMethodProblem]("play.api.libs.ws.StandaloneWSRequest.withDisableUrlEncoding")
  )
)

lazy val commonSettings = Def.settings(
  organization         := "com.typesafe.play",
  organizationName     := "The Play Framework Project",
  organizationHomepage := Some(url("https://playframework.com")),
  homepage             := Some(url("https://github.com/playframework/play-ws/")),
  scmInfo := Some(ScmInfo(url("https://github.com/playframework/play-ws"), "git@github.com:playframework/play-ws.git")),
  developers += Developer(
    "playframework",
    "The Play Framework Contributors",
    "contact@playframework.com",
    url("https://github.com/playframework")
  ),
  licenses           := Seq("Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
  scalaVersion       := scala213,
  crossScalaVersions := Seq(scala213, scala3),
  conflictWarning := {
    if (scalaBinaryVersion.value == "3") {
      ConflictWarning("warn", sbt.Level.Warn, false)
    } else {
      conflictWarning.value
    }
  },
  scalacOptions ++= scalacOpts.value,
  Compile / doc / scalacOptions ++= Seq(
    "-Xfatal-warnings",
    // Work around 2.12+ bug which prevents javadoc in nested java classes from compiling.
    "-no-java-comments",
  ),
  Compile / javacOptions ++= javacSettings,
  Test / javacOptions ++= javacSettings,
  headerLicense := {
    Some(
      HeaderLicense.Custom(
        """Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>"""
      )
    )
  }
)

lazy val shadedCommonSettings = Seq(
  // scalaVersion := scala213,
  // crossScalaVersions := Seq(scala213),
  // No need to cross publish the shaded libraries
  crossPaths       := false,
  autoScalaLibrary := false,
)

lazy val shadeAssemblySettings = commonSettings ++ shadedCommonSettings ++ Seq(
  assembly / assemblyOption ~= (_.withIncludeScala(false)),
  assembly / test := {},
  assembly / assemblyJarName := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((epoch, major)) =>
        s"${name.value}.jar" // we are only shading java
      case _ =>
        sys.error("Cannot find valid scala version!")
    }
  },
)

val ahcMerge: MergeStrategy = CustomMergeStrategy("ahcMerge") { dependencies =>
  Right(dependencies.map { f =>
    val stream = () => {
      val out    = new java.io.ByteArrayOutputStream
      val reader = new java.io.BufferedReader(new java.io.InputStreamReader(f.stream.apply()))
      try {
        reader.lines().forEach { line =>
          // In AsyncHttpClientConfigDefaults.java, the shading renames the resource keys
          // so we have to manually tweak the resource file to match.
          val shadedline = line.replace("org.asynchttpclient", "play.shaded.ahc.org.asynchttpclient")
          out.write(line.getBytes(IO.defaultCharset))
          out.write(IO.Newline.getBytes(IO.defaultCharset))
          out.write(shadedline.getBytes(IO.defaultCharset))
          out.write(IO.Newline.getBytes(IO.defaultCharset))
        }
      } finally {
        reader.close()
      }
      new java.io.ByteArrayInputStream(out.toByteArray)
    }
    JarEntry(target = f.target, stream = stream)
  }.toVector)
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
  .disablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(shadeAssemblySettings)
  .settings(
    libraryDependencies ++= asyncHttpClient,
    name                := "shaded-asynchttpclient",
    assembly / logLevel := Level.Error,
    assembly / assemblyMergeStrategy := {
      val NettyPropertiesPath = "META-INF" + File.separator + "io.netty.versions.properties"
      val mergeStrategy: String => MergeStrategy = {
        case NettyPropertiesPath =>
          MergeStrategy.first

        case ahcProperties if ahcProperties.endsWith("ahc-default.properties") =>
          ahcMerge

        case x =>
          val oldStrategy = (assembly / assemblyMergeStrategy).value
          oldStrategy(x)
      }
      mergeStrategy
    },
    // logLevel in assembly := Level.Debug,
    assembly / assemblyShadeRules := Seq(
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
    // ivyXML := <dependencies></dependencies>,
    // ivyLoggingLevel := UpdateLogging.Full,
    // logLevel := Level.Debug,
    assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeBin(false).withIncludeScala(false),
    Compile / packageBin      := assembly.value
  )

//---------------------------------------------------------------
// Shaded oauth
//---------------------------------------------------------------

lazy val `shaded-oauth` = project
  .in(file("shaded/oauth"))
  .disablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(shadeAssemblySettings)
  .settings(
    libraryDependencies ++= oauth,
    name := "shaded-oauth",
    // logLevel in assembly := Level.Debug,
    assembly / assemblyShadeRules := Seq(
      ShadeRule.rename("oauth.**" -> "play.shaded.oauth.@0").inAll,
      ShadeRule.rename("org.apache.commons.**" -> "play.shaded.oauth.@0").inAll
    ),
    // https://stackoverflow.com/questions/24807875/how-to-remove-projectdependencies-from-pom
    // Remove dependencies from the POM because we have a FAT jar here.
    makePomConfiguration      := makePomConfiguration.value.withProcess(process = dependenciesFilter),
    assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeBin(false).withIncludeScala(false),
    Compile / packageBin      := assembly.value
  )

// Make the shaded version of AHC available downstream
val shadedAhcSettings = Seq(
  Compile / unmanagedJars += (`shaded-asynchttpclient` / Compile / packageBin).value
)

val shadedOAuthSettings = Seq(
  Compile / unmanagedJars += (`shaded-oauth` / Compile / packageBin).value
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
    commonSettings,
    shadedCommonSettings,
  )

//---------------------------------------------------------------
// WS API
//---------------------------------------------------------------

// WS API, no play dependencies
lazy val `play-ws-standalone` = project
  .in(file("play-ws-standalone"))
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
  .settings(
    commonSettings ++ shadedAhcSettings ++ shadedOAuthSettings ++ Seq(
      Test / fork        := true,
      Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
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
  .settings(commonSettings)
  .settings(mimaSettings)
  .settings(
    Test / fork        := true,
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
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
  .settings(commonSettings)
  .settings(mimaSettings)
  .settings(
    Test / fork        := true,
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
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
    Test / fork := true,
    concurrentRestrictions += Tags.limitAll(1), // only one integration test at a time
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
    libraryDependencies ++= akkaHttp.map(_ % Test) ++ testDependencies,
    libraryDependencies ++= akkaStreams.map(
      _.cross(CrossVersion.for3Use2_13) // temporary, to make it tests work with Scala 3
    ),
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
  .settings(Compile / doc / scalacOptions -= "-Xfatal-warnings")
  .settings(publish / skip := true)

//---------------------------------------------------------------
// Root Project
//---------------------------------------------------------------

lazy val root = project
  .in(file("."))
  .disablePlugins(MimaPlugin, sbtassembly.AssemblyPlugin)
  .settings(
    name := "play-ws-standalone-root",
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
  List(
    "headerCheckAll",
    "scalafmtSbtCheck",
    "scalafmtCheckAll",
  ).mkString(";")
)

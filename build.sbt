import Dependencies._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

import scalariform.formatter.preferences._

//---------------------------------------------------------------
// Shading and Project Settings
//---------------------------------------------------------------

val previousVersion = None

resolvers ++= DefaultOptions.resolvers(snapshot = true)
resolvers in ThisBuild += Resolver.sonatypeRepo("public")

val javacSettings = Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

lazy val commonSettings = mimaDefaultSettings ++ Seq(
  organization := "com.typesafe.play",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.12.1", "2.11.8"),
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
  scalacOptions in (Compile, doc) ++= {
    // Work around 2.12 bug which prevents javadoc in nested java classes from compiling.
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

val formattingSettings = Seq(
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(SpacesAroundMultiImports, true)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(DoubleIndentClassDeclaration, true)
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

lazy val shadeAssemblySettings = commonSettings ++ Seq(
  assemblyOption in assembly ~= (_.copy(includeScala = false)),
  test in assembly := {},
  assemblyOption in assembly ~= {
    _.copy(includeScala = false)
  },
  assemblyJarName in assembly := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((epoch, major)) =>
        s"${name.value}_$epoch.$major-${version.value}.jar"
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
  }
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
      IO.append(file, shadedline)
      IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    }
    Right(Seq(file -> path))
  }

  override val name: String = "ahcMerge"
}

//---------------------------------------------------------------
// Shaded AsyncHttpClient implementation
//---------------------------------------------------------------

lazy val `shaded-asynchttpclient` = project.in(file("shaded/asynchttpclient"))
  .settings(commonSettings)
  .settings(shadeAssemblySettings)
  .settings(
    libraryDependencies ++= asyncHttpClient,
    name := "shaded-asynchttpclient"
  )
  .settings(
    logLevel in assembly := Level.Error,
    assemblyMergeStrategy in assembly := {
      case "META-INF/io.netty.versions.properties" =>
        MergeStrategy.first
      case "ahc-default.properties" =>
        ahcMerge
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    //logLevel in assembly := Level.Debug,
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.asynchttpclient.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("io.netty.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("javassist.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("com.typesafe.netty.**" -> "play.shaded.ahc.@0").inAll
    ),
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
    name := "shaded-oauth"
  )
  .settings(
    logLevel in assembly := Level.Error,
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("oauth.**" -> "play.shaded.oauth.@0").inAll,
      ShadeRule.rename("org.apache.commons.**" -> "play.shaded.oauth.@0").inAll
    ),
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
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// WS API
//---------------------------------------------------------------

// WS API, no play dependencies
lazy val `play-ws-standalone` = project
  .in(file("play-ws-standalone"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= (specsBuild ++ junitInterface).map(_ % Test))
  .settings(libraryDependencies ++= standaloneApiWSDependencies)
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Shaded AsyncHttpClient implementation of WS
//---------------------------------------------------------------

// Standalone implementation using AsyncHttpClient
// Note that this uses integration tests in the suite, so has
// some extra bells and whistles.
lazy val `play-ahc-ws-standalone` = project
  .in(file("play-ahc-ws-standalone"))
  .settings(commonSettings)
  .settings(formattingSettings)
  .settings(SbtScalariform.scalariformSettings)
  .settings(
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
    libraryDependencies ++= (slf4jtest ++ specsBuild ++ junitInterface).map(_ % Test)
  )
  .settings(libraryDependencies ++= standaloneAhcWSDependencies)
  .settings(shadedAhcSettings)
  .settings(shadedOAuthSettings)
  .dependsOn(
    `play-ws-standalone`,
    `shaded-oauth`,
    `shaded-asynchttpclient`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

lazy val `integration-tests` = project.in(file("integration-tests"))
  .settings(commonSettings)
  .settings(formattingSettings)
  .settings(SbtScalariform.scalariformSettings)
  .settings(
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v")),
    libraryDependencies ++= (specsBuild ++ akkaHttp ++ caffeine).map(_ % Test)
  )
  .settings(libraryDependencies ++= standaloneAhcWSDependencies)
  .settings(shadedAhcSettings)
  .settings(shadedOAuthSettings)
  .dependsOn(
    `play-ahc-ws-standalone`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Root Project
//---------------------------------------------------------------

lazy val root = project
  .in(file("."))
  .settings(name := "play-ws-standalone-root")
  .settings(commonSettings)
  .settings(formattingSettings)
  .settings(disableDocs)
  .settings(disablePublishing)
  .dependsOn(
    `integration-tests`
  )
  .aggregate(
    `shaded`,
    `play-ws-standalone`,
    `play-ahc-ws-standalone`
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Release
//---------------------------------------------------------------
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
  pushChanges
)

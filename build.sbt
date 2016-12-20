import sbt._
import Dependencies._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

resolvers ++= DefaultOptions.resolvers(snapshot = true)

lazy val scalaVersionMajor = "2.11"

lazy val commonSettings = Seq(
  organization := "com.typesafe.play",
  scalaVersion := "2.11.8"
)
//---------------------------------------------------------------
// WS API
//---------------------------------------------------------------

// WS API, no play dependencies
lazy val `play-ws-standalone` = project
  .in(file("play-ws-standalone"))
  .enablePlugins(PlayLibrary)
  .settings(commonSettings)
  .settings(libraryDependencies ++= wsDependencies(scalaVersion.value))
  .disablePlugins(sbtassembly.AssemblyPlugin)

// WS API with Play dependencies
lazy val `play-ws` = project
  .in(file("play-ws"))
  .enablePlugins(PlayLibrary)
  .settings(commonSettings)
  .settings(libraryDependencies ++= playDeps)
  .settings(libraryDependencies ++= specsBuild.map(_ % Test))
  .dependsOn(`play-ws-standalone`)
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// WS with shaded AsyncHttpClient implementation
//---------------------------------------------------------------

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

// Shading implementation from:
// https://manuzhang.github.io/2016/10/15/shading.html
// https://github.com/huafengw/incubator-gearpump/blob/4474618c4fdd42b152d26a6915704a4f763d14c1/project/BuildShaded.scala

lazy val shadeAssemblySettings = commonSettings ++ Seq(
  assemblyOption in assembly ~= (_.copy(includeScala = false)),
  test in assembly := {},
  assemblyOption in assembly ~= {
    _.copy(includeScala = false)
  },
  assemblyJarName in assembly := {
    s"${name.value}_$scalaVersionMajor-${version.value}.jar"
  },
  target in assembly := baseDirectory.value.getParentFile / "target" / scalaVersionMajor
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

lazy val `shaded-asynchttpclient` = project.in(file("shaded/asynchttpclient"))
  .settings(commonSettings)
  .settings(disableDocs)
  .settings(shadeAssemblySettings)
  .settings(
    libraryDependencies ++= asyncHttpClient,
    name := "shaded-asynchttpclient"
  )
  .settings(
    assemblyMergeStrategy in assembly := {
      case "META-INF/io.netty.versions.properties" =>
        MergeStrategy.first
      case "ahc-default.properties" =>
        ahcMerge
      case "ahc-mime.types" =>
        MergeStrategy.first
      case "ahc-version.properties" =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    //logLevel in assembly := Level.Debug,
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.asynchttpclient.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("io.netty.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("javassist.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.rename("com.typesafe.netty.**" -> "play.shaded.ahc.@0").inAll,
      ShadeRule.zap("org.reactivestreams.**").inAll // somehow gets dragged in
    ),
    artifact in(Compile, assembly) ~= (_.copy(classifier = Some("assembly"))),
    addArtifact(Artifact("shaded-asynchttpclient"), assembly)
  )

lazy val `shaded-oauth` = project.in(file("shaded/oauth"))
  .settings(commonSettings)
  .settings(disableDocs)
  .settings(shadeAssemblySettings)
  .settings(
    libraryDependencies ++= oauth,
    name := "shaded-oauth"
  )
  .settings(
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("oauth.**" -> "play.shaded.oauth.@0").inAll,
      ShadeRule.rename("org.apache.http.**" -> "play.shaded.oauth.@0").inAll,
      ShadeRule.rename("com.google.gdata.**" -> "play.shaded.oauth.@0").inAll,
      ShadeRule.rename("org.apache.commons.**" -> "play.shaded.oauth.@0").inAll
    ),
    artifact in(Compile, assembly) ~= (_.copy(classifier = Some("assembly"))),
    addArtifact(Artifact("shaded-oauth"), assembly)
  )

lazy val shaded = Project(id = "shaded", base = file("shaded") )
  .settings(disableDocs)
  .settings(disablePublishing)
  .aggregate(`shaded-asynchttpclient`, `shaded-oauth`)
  .disablePlugins(sbtassembly.AssemblyPlugin)

def getShadedJarFile(name: String, version: String): File = {
  shaded.base / "target" / scalaVersionMajor /
    s"${name}_$scalaVersionMajor-$version.jar"
}

// Make the shaded version of AHC available downstream
val shadedAhcSettings = Seq(
  unmanagedJars in Compile ++= Seq(
    getShadedJarFile("shaded-asynchttpclient", version.value)
  )
)

val shadedOAuthSettings = Seq(
  unmanagedJars in Compile ++= Seq(
    getShadedJarFile("shaded-oauth", version.value)
  )
)

def changeShadedDeps(toExclude: Set[String], toInclude: List[xml.Node],
                             node: xml.Node): xml.Node = {
  node match {
    case elem: xml.Elem =>
      val child =
        if (elem.label == "dependencies") {
          elem.child.filterNot { dep =>
            dep.child.find(_.label == "groupId").exists(gid => toExclude.contains(gid.text))
          } ++ toInclude
        } else {
          elem.child.map(changeShadedDeps(toExclude, toInclude, _))
        }
      xml.Elem(elem.prefix, elem.label, elem.attributes, elem.scope, false, child: _*)
    case _ =>
      node
  }
}

// Standalone implementation using AsyncHttpClient
lazy val `play-ahc-ws-standalone` = project
  .in(file("play-ahc-ws-standalone"))
  .enablePlugins(PlayLibrary)
  .settings(commonSettings)
  .settings(shadedAhcSettings)
  .settings(shadedOAuthSettings)
  .dependsOn(`play-ws-standalone`)
  .disablePlugins(sbtassembly.AssemblyPlugin)

// Play implementation using AsyncHttpClient
lazy val `play-ahc-ws` = project
  .in(file("play-ahc-ws"))
  .enablePlugins(PlayLibrary)
  .settings(commonSettings)
  .settings(libraryDependencies ++= playDeps)
  .settings(libraryDependencies ++= specsBuild.map(_ % Test))
  .dependsOn(`play-ws`, `play-ahc-ws-standalone`)
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Root Project
//---------------------------------------------------------------

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .enablePlugins(PlayRootProject)
  .settings(
    name := "play-ws-root",
    pomPostProcess := {
      (node: xml.Node) => changeShadedDeps(
        Set(
          "shaded-asynchttpclient",
          "shaded-oauth"
        ),
        List.empty[xml.Node],
        node)
    }
  )
  .aggregate(
    `shaded`,
    `play-ws-standalone`,
    `play-ws`,
    `play-ahc-ws-standalone`,
    `play-ahc-ws`,
    `play-ws-integration-tests`)
  .disablePlugins(sbtassembly.AssemblyPlugin)

//---------------------------------------------------------------
// Integration tests
//---------------------------------------------------------------

lazy val `play-ws-integration-tests` = project
  .in(file("play-ws-integration-tests"))
  .enablePlugins(PlayLibrary)
  .settings(disableDocs)
  .settings(disablePublishing)
  .settings(commonSettings)
  .settings(libraryDependencies ++= playDeps)
  .settings(libraryDependencies ++= specsBuild.map(_ % Test) ++ playTest)
  .dependsOn(`play-ahc-ws`)
  .disablePlugins(sbtassembly.AssemblyPlugin)

playBuildRepoName in ThisBuild := "play-ws"

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

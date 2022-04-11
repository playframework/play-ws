/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 *
 */

// https://github.com/sbt/sbt-dirty-money/blob/master/src/main/scala/sbtdirtymoney/DirtyMoneyPlugin.scala

import sbt._
import Keys._

object CleanShadedPlugin extends AutoPlugin {
  override def requires = plugins.IvyPlugin
  override def trigger  = allRequirements

  object autoImport {
    val cleanCacheIvyDirectory: SettingKey[File] = settingKey[File]("")
    val cleanCache: InputKey[Unit]               = inputKey[Unit]("")
    val cleanCacheFiles: InputKey[Seq[File]]     = inputKey[Seq[File]]("")
    val cleanLocal: InputKey[Unit]               = inputKey[Unit]("")
    val cleanLocalFiles: InputKey[Seq[File]]     = inputKey[Seq[File]]("")
  }
  import autoImport._

  object CleanShaded {
    import sbt.complete.Parser
    import sbt.complete.DefaultParsers._

    final case class ModuleParam(organization: String, name: Option[String])

    def parseParam: Parser[Option[ModuleParam]] =
      (parseOrg ~ parseName.?).map { case o ~ n => ModuleParam(o, n) }.?

    private def parseOrg: Parser[String] = Space ~> token(StringBasic.examples("\"organization\""))

    private def parseName: Parser[String] =
      Space ~> token(token("%") ~> Space ~> StringBasic.examples("\"name\""))

    def query(base: File, param: Option[ModuleParam], org: String, name: String): Seq[File] = {
      val base1 = PathFinder(base)
      val pathFinder = param match {
        case None                               => base1 ** stringToGlob(org) ** stringToGlob(name)
        case Some(ModuleParam(org, None))       => base1 ** stringToGlob(org)
        case Some(ModuleParam(org, Some(name))) => base1 ** stringToGlob(org) ** stringToGlob(name)
      }
      pathFinder.get()
    }

    private def stringToGlob(s: String) = if (s == "*") "*" else s"*$s*"
  }

  override def projectSettings =
    Seq(
      cleanCacheIvyDirectory := ivyPaths.value.ivyHome.getOrElse(Path.userHome / ".ivy2"),
      cleanCache             := IO.delete(cleanCacheFiles.evaluated),
      cleanLocal             := IO.delete(cleanLocalFiles.evaluated),
      cleanCacheFiles := {
        val base  = cleanCacheIvyDirectory.value / "cache"
        val param = CleanShaded.parseParam.parsed
        CleanShaded.query(base, param, organization.value, moduleName.value)
      },
      cleanLocalFiles := {
        val base  = cleanCacheIvyDirectory.value / "local"
        val param = CleanShaded.parseParam.parsed
        CleanShaded.query(base, param, organization.value, moduleName.value)
      }
    )
}

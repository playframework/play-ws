/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
 */

// https://github.com/sbt/sbt-dirty-money/blob/master/src/main/scala/sbtdirtymoney/DirtyMoneyPlugin.scala

import sbt._, Keys._

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
      ((parseOrg ~ parseName.?) map {
        case o ~ n => ModuleParam(o, n)
      }).?

    private def parseOrg: Parser[String] =
      Space ~> token(StringBasic.examples("\"organization\""))

    private def parseName: Parser[String] =
      Space ~> token(token("%") ~> Space ~> StringBasic.examples("\"name\""))

    def query(base: File, param: Option[ModuleParam], org: String, name: String): Seq[File] =
      (param match {
        case None                                   => base ** ("*" + org + "*") ** ("*" + name + "*")
        case Some(ModuleParam("*", None))           => base ** "*"
        case Some(ModuleParam(o, None | Some("*"))) => base ** ("*" + o + "*") ** "*"
        case Some(ModuleParam(o, Some(n)))          => base ** ("*" + o + "*") ** ("*" + n + "*")
      }).get
  }

  override def projectSettings = Seq(
    cleanCacheIvyDirectory := ivyPaths.value.ivyHome getOrElse (Path.userHome / ".ivy2"),
    cleanCache := IO.delete(cleanCacheFiles.evaluated),
    cleanLocal := IO.delete(cleanLocalFiles.evaluated),
    cleanCacheFiles := {
      val base = cleanCacheIvyDirectory.value / "cache"
      val param = CleanShaded.parseParam.parsed
      CleanShaded.query(base, param, organization.value, moduleName.value)
    },
    cleanLocalFiles := {
      val base = cleanCacheIvyDirectory.value / "local"
      val param = CleanShaded.parseParam.parsed
      CleanShaded.query(base, param, organization.value, moduleName.value)
    }
  )
}

import sbt.Keys._
import sbt._

object Publish extends AutoPlugin {

  import bintray.BintrayPlugin
  import bintray.BintrayPlugin.autoImport._

  override def trigger = noTrigger

  override def requires = BintrayPlugin

  override def projectSettings =
    Seq(
      bintrayOrganization := Some("playframework"),
      bintrayRepository := (if (isSnapshot.value) "snapshots" else "maven"),
      bintrayPackage := "play-ws",
    )
}

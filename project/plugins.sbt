
resolvers ++= DefaultOptions.resolvers(snapshot = true)
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.8")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.3.0")

addSbtPlugin("com.gilt" % "sbt-dependency-graph-sugar" % "0.9.0")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.4")

import sbt._
import Keys._
import spray.revolver.RevolverPlugin._
import spray.revolver.RevolverPlugin.Revolver._
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin._
import com.typesafe.sbt.SbtAspectj.aspectjSettings
import com.typesafe.sbt.SbtAspectj.AspectjKeys
import com.typesafe.sbt.SbtAspectj.Aspectj

object Build extends sbt.Build {
  val akkaVersion = "2.4.2"
  val sprayVersion = "1.3.3"
  val kamonVersion = "0.5.2"

  lazy val soaker = Project("gubnor", file("."))
    .settings(Revolver.settings: _*)
    .settings(assemblySettings: _*)
    .settings(aspectjSettings: _*)
    .settings(
      organization := "com.shw",
      version := "0.1.0-routed",
      scalaVersion := "2.11.7",
      javaOptions in run <++= AspectjKeys.weaverOptions in Aspectj,
      fork in run := true,
      mergeStrategy in assembly := AspectJMergeStrategy.customMergeStrategy,
      scalacOptions ++= Seq("-feature", "-language:implicitConversions", "-language:postfixOps"),
      javaOptions in reStart += "-Dconfig.file=gubnor.conf",
      resolvers ++= Seq( "spray repo" at "http://repo.spray.io"),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-agent" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.0.13",
        "com.google.guava" % "guava" % "18.0",
        "io.spray" %% "spray-can" % sprayVersion,
        "io.spray" %% "spray-routing" % sprayVersion,
        "io.spray" %% "spray-client" % sprayVersion,
        "io.spray" %%  "spray-json" % "1.3.1",
        "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
        "com.github.scopt" %% "scopt" % "3.3.0",

        //"com.typesafe.akka" %% "akka-testkit" % "2.3.6",

        "io.kamon" %% "kamon-core" % kamonVersion,
        "io.kamon" %% "kamon-statsd" % kamonVersion,
        "io.kamon" %% "kamon-datadog" % kamonVersion,
        "io.kamon" %% "kamon-akka" % kamonVersion,
        "io.kamon" %% "kamon-spray" % kamonVersion
      )
  )
}

import sbt._
import Keys._
import spray.revolver.RevolverPlugin._
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
      version := "0.1.0",
      scalaVersion := "2.11.7",
      javaOptions in run <++= AspectjKeys.weaverOptions in Aspectj,
      fork in run := true,
      mergeStrategy in assembly := AspectJMergeStrategy.customMergeStrategy,
      //javacOptions ++= Seq("-source", "1.6"),
      //scalacOptions += "-target:jvm-1.6",
      resolvers ++= Seq(
        "spray repo" at "http://repo.spray.io",
        Resolver.sonatypeRepo("snapshots")
        //"Gamlor Repo" at "https://github.com/gamlerhart/gamlor-mvn/raw/master/snapshots",
        //"czcollier repo" at "https://github.com/czcollier/gamlor-mvn/raw/master/snapshots"
      ),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-agent" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.0.13",
        "com.google.guava" % "guava" % "18.0",
        //"com.typesafe.akka" %% "akka-testkit" % "2.3.6",
        "io.spray" %% "spray-can" % sprayVersion,
        "io.spray" %% "spray-routing" % sprayVersion,
        "io.spray" %% "spray-client" % sprayVersion,
        "io.spray" %%  "spray-json" % "1.3.1",
        "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
        //"info.gamlor.adbcj" %% "scala-adbcj" % "0.6-SNAPSHOT",
        //"org.adbcj" % "adbcj-connection-pool" % "0.7.2-SNAPSHOT",
        //"org.adbcj" % "mysql-async-driver" % "0.7.2-SNAPSHOT",
        "com.github.scopt" %% "scopt" % "3.3.0",

        "io.kamon" %% "kamon-core" % kamonVersion,
        "io.kamon" %% "kamon-statsd" % kamonVersion,
        "io.kamon" %% "kamon-datadog" % kamonVersion,
        "io.kamon" %% "kamon-akka" % kamonVersion,
        "io.kamon" %% "kamon-spray" % kamonVersion
      )
  )
}
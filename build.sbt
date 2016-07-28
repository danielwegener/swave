import scalariform.formatter.preferences._
import de.heikoseeberger.sbtheader.HeaderPattern
import com.typesafe.sbt.SbtScalariform._
import ReleaseTransformations._

lazy val contributors = Seq(
  "Mathias Doenitz" -> "sirthias")

lazy val commonSettings = Seq(
  resolvers += Resolver.sonatypeRepo("snapshots"),
  organization := "io.swave",
  scalaVersion := "2.11.8",
  homepage := Some(url("http://swave.io")),
  description := "A Reactive Streams infrastructure implementation in Scala",
  startYear := Some(2016),
  licenses := Seq("MPL 2.0" -> new URL("https://www.mozilla.org/en-US/MPL/2.0/")),
  javacOptions ++= commonJavacOptions,
  scalacOptions ++= commonScalacOptions,
  scalacOptions in Test ~= (_ filterNot (_ == "-Ywarn-value-discard")),
  scalacOptions in (Test, console) ~= { _ filterNot { o => o == "-Ywarn-unused-import" || o == "-Xfatal-warnings" } },
  scalacOptions in (Compile, doc) ~= { _.filterNot(o => o == "-Xlint" || o == "-Xfatal-warnings") :+ "-nowarn" },
  initialCommands in console := """import swave.core._""",
  scmInfo := Some(ScmInfo(url("https://github.com/sirthias/swave"), "scm:git:git@github.com:sirthias/swave.git")),
  headers := Map("scala" -> (
    HeaderPattern.cStyleBlockComment,
    """/* This Source Code Form is subject to the terms of the Mozilla Public
      | * License, v. 2.0. If a copy of the MPL was not distributed with this
      | * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
      |
      |""".stripMargin)),
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignParameters, false)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DanglingCloseParenthesis, Prevent)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(RewriteArrowSymbols, true),
  coverageMinimum := 80,
  coverageFailOnMinimum := false)

lazy val publishingSettings = Seq(
  useGpg := true,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo <<= version { v: String =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim endsWith "SNAPSHOT") Some("snapshots" at nexus + "content/repositories/snapshots")
    else                            Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra :=
    <developers>
      {for ((name, username) <- contributors)
        yield <developer><id>{username}</id><name>{name}</name><url>http://github.com/{username}</url></developer>
      }
    </developers>)

lazy val noPublishingSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false)

lazy val releaseSettings = {
  val runCompile = ReleaseStep(
    action = { st: State =>
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      extracted.runAggregated(compile in Compile in ref, st)
    })

  Seq(
    releaseCrossBuild := false,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runCompile,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges))
}

lazy val commonJavacOptions = Seq(
  "-encoding", "UTF-8",
  "-Xlint:unchecked",
  "-Xlint:deprecation")

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:experimental.macros",
  "-unchecked",
  //"-Xfatal-warnings",
  "-Xlint",
  "-Yinline-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-unused-import",
  "-Xfuture")

lazy val macroParadise =
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

/////////////////////// DEPENDENCIES /////////////////////////

// core
val `reactive-streams`     = "org.reactivestreams"        %   "reactive-streams"     % "1.0.0"
val `jctools-core`         = "org.jctools"                %   "jctools-core"         % "1.2.1"
val `typesafe-config`      = "com.typesafe"               %   "config"               % "1.3.0"
val `shocon`               = "eu.unicredit"               %%%!"shocon"               % "0.0.2-dwe"
val shapeless              = "com.chuusai"                %%%!"shapeless"            % "2.3.1"
val `scala-logging`        = "com.typesafe.scala-logging" %%  "scala-logging"        % "3.4.0"

// *-compat
val `akka-stream`          = "com.typesafe.akka"          %%  "akka-stream"          % "2.4.8"
val `akkajs-stream`        = "eu.unicredit"               %%%!"akkajsactorstream"    % "0.1.2-SNAPSHOT"
val `scodec-bits`          = "org.scodec"                 %%%!"scodec-bits"          % "1.1.0"

// test
val scalatestJS              = "org.scalatest"              %%%! "scalatest"           % "3.0.0-RC4" % "test"
val scalatest              = "org.scalatest"              %% "scalatest"           % "3.0.0-RC4" % "test"
val scalacheckJS             = "org.scalacheck"             %%%! "scalacheck"          % "1.13.1"
val scalacheck             = "org.scalacheck"             %% "scalacheck"          % "1.13.1"
val `reactive-streams-tck` = "org.reactivestreams"        %    "reactive-streams-tck"% "1.0.0" % "test"

// examples
val `akka-http-core`       = "com.typesafe.akka"          %%   "akka-http-core"        % "2.4.8"
val logback                = "ch.qos.logback"             %    "logback-classic"       % "1.1.7"

/////////////////////// PROJECTS /////////////////////////

lazy val swave = project.in(file("."))
  .aggregate(akkaCompatJVM, akkaCompatJS, benchmarks, coreJVM, coreJS, `core-macrosJVM`, `core-macrosJS`, `core-testsJVM`, `core-testsJS`, examples, scodecCompatJS, scodecCompatJVM, testkitJVM, testkitJS)
  .settings(commonSettings: _*)
  .settings(releaseSettings: _*)
  .settings(noPublishingSettings: _*)

lazy val akkaCompat = crossProject.crossType(CrossType.Pure)
  .dependsOn(core, `core-macros` % "compile-internal", testkit)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings: _*)
  .settings(releaseSettings: _*)
  .settings(publishingSettings: _*)
  .settings(
    moduleName := "swave-akka-compat")
  .jvmSettings(
    libraryDependencies ++= Seq(`akka-stream`, scalatest))
  .jsSettings(
    libraryDependencies ++= Seq(`akkajs-stream`, scalatestJS))

lazy val akkaCompatJVM = akkaCompat.jvm
lazy val akkaCompatJS = akkaCompat.js

lazy val benchmarks = project
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings: _*)
  .settings(noPublishingSettings: _*)


lazy val core = crossProject.crossType(CrossType.Pure)
  .dependsOn(`core-macros` % "compile-internal, test-internal")
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings: _*)
  .settings(releaseSettings: _*)
  .settings(publishingSettings: _*)
  .settings(
    moduleName := "swave-core",
    macroParadise,
    libraryDependencies ++= Seq(shapeless, `scala-logging`
      ))
    .jvmSettings(
      libraryDependencies ++= Seq(`typesafe-config`, `reactive-streams`,  `jctools-core`, scalatest, scalacheck % "test"))
    .jsSettings(
      libraryDependencies ++= Seq(shocon, scalatestJS, scalacheckJS % "test")
    )


lazy val coreJS = core.js
lazy val coreJVM = core.jvm

lazy val `core-macros` = crossProject.crossType(CrossType.Pure)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings: _*)
  .settings(noPublishingSettings: _*)
  .settings(
    macroParadise,
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value)

lazy val `core-macrosJS` = `core-macros`.js
lazy val `core-macrosJVM` = `core-macros`.jvm

lazy val `core-tests` = crossProject.crossType(CrossType.Pure)
  .dependsOn(core, testkit, `core-macros` % "test-internal")
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings: _*)
  .settings(noPublishingSettings: _*)
  .settings(libraryDependencies ++= Seq(shapeless, `reactive-streams-tck`, logback % "test"))
  .jsSettings(libraryDependencies ++= Seq(scalatestJS, scalacheckJS % "test"))
  .jvmSettings(libraryDependencies ++= Seq(scalatest, scalacheck % "test"))

lazy val `core-testsJS` = `core-tests`.js
lazy val `core-testsJVM` = `core-tests`.jvm

lazy val examples = project
  .dependsOn(coreJVM, akkaCompatJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.typesafe.sbt.SbtScalariform)
  .settings(commonSettings: _*)
  .settings(noPublishingSettings: _*)
  .settings(
    fork in run := true,
    connectInput in run := true,
    javaOptions in run ++= Seq("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder"),
    libraryDependencies ++= Seq(`akka-stream`, `akka-http-core`, logback))

lazy val scodecCompat = crossProject.crossType(CrossType.Pure)
  .dependsOn(core, `core-macros` % "compile-internal", testkit, `core-tests` % "test->test")
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings: _*)
  .settings(releaseSettings: _*)
  .settings(publishingSettings: _*)
  .settings(
    moduleName := "swave-scodec-compat",
    libraryDependencies ++= Seq(`scodec-bits`, scalatest, logback % "test"))
  .jsSettings(libraryDependencies += scalatestJS)
  .jvmSettings(libraryDependencies += scalatest)
lazy val scodecCompatJS = scodecCompat.js
lazy val scodecCompatJVM = scodecCompat.jvm


lazy val testkit = crossProject.crossType(CrossType.Pure)
  .dependsOn(core, `core-macros` % "compile-internal")
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings: _*)
  .settings(releaseSettings: _*)
  .settings(publishingSettings: _*)
  .settings(
    moduleName := "swave-testkit",
    macroParadise)
  .jsSettings(libraryDependencies ++= Seq(scalacheckJS % "test"))
  .jvmSettings(libraryDependencies ++= Seq(scalacheck % "test"))

lazy val testkitJS = testkit.js
lazy val testkitJVM = testkit.jvm

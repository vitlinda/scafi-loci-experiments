import Dependencies._

ThisBuild / scalaVersion     := "2.13.2"
ThisBuild / version          := "0.1.0"

ThisBuild / semanticdbEnabled := true

resolvers += "jitpack" at "https://jitpack.io"

lazy val root = (project in file("."))
  .settings(
    name := "aggregate-loci-experiments",
    scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-Xlint", "-Ymacro-annotations", "-Ywarn-unused"),
    libraryDependencies ++= Seq(
      //      munit % Test,
      lociLanguage,
      lociRuntime,
      lociTransmitter,
      lociCommunicatorTcp,
      lociSerializerUpickle,
      lociSerializerCirce,
      scafiCore
    ) ++ spala
  )


import sbt._

object Dependencies {
  val scalaLociVersion = "0.5.0"
  val scafiVersion = "1.1.6"

//  lazy val munit = "org.scalameta" %% "munit" % "0.7.29"

  lazy val lociLanguage = "io.github.scala-loci" %% "scala-loci-language" % scalaLociVersion % "provided"
  lazy val lociRuntime = "io.github.scala-loci" %% "scala-loci-language-runtime" % scalaLociVersion
  lazy val lociTransmitter = "io.github.scala-loci" %% "scala-loci-language-transmitter-rescala" % scalaLociVersion
  lazy val lociCommunicatorTcp = "io.github.scala-loci" %% "scala-loci-communicator-tcp" % scalaLociVersion
  lazy val lociSerializerUpickle = "io.github.scala-loci" %% "scala-loci-serializer-upickle" % scalaLociVersion
  lazy val lociSerializerCirce = "io.github.scala-loci" %% "scala-loci-serializer-circe" % scalaLociVersion

  lazy val scafiCore  = "it.unibo.scafi" %% "scafi-core"  % scafiVersion
  lazy val scafiSimulatorGui  = "it.unibo.scafi" %% "scafi-simulator-gui"  % scafiVersion

  def spala: Seq[ModuleID] = Seq(
    "it.unibo.scafi" %% "spala" % scafiVersion intransitive(),
    "com.typesafe.play" %% "play-json" % "2.9.4",
    "com.typesafe.akka" %% "akka-remote" % "2.8.0",
    "com.typesafe.akka" %% "akka-actor" % "2.8.0",
    "com.github.scopt" %% "scopt" % "4.1.0",
    "org.slf4j" % "slf4j-log4j12" % "2.0.5"
  )
}

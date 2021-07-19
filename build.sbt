name := "tunnel-sbt"

version := "0.1"

scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  "org.whispersystems" % "curve25519-java" % "0.5.0",
  "org.eclipse.paho" % "org.eclipse.paho.mqttv5.client" % "1.2.5",
  "org.scala-lang" % "scala-reflect" % "2.13.6",
  "org.jitsi" % "ice4j" % "3.0-33-g311a495"
)
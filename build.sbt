name := "tunnel-sbt"

version := "0.1"

scalaVersion := "2.13.6"

resolvers += "jitpack" at "https://jitpack.io"


mainClass in Compile := Some("xyz.octeep.tunnel.Main")

libraryDependencies ++= Seq(
  "org.whispersystems" % "curve25519-java" % "0.5.0",
  "org.eclipse.paho" % "org.eclipse.paho.mqttv5.client" % "1.2.5",
  "org.scala-lang" % "scala-reflect" % "2.13.6",
   "com.github.jitsi" % "ice4j" % "v3.0",
  "info.picocli" % "picocli" % "4.6.1",
)

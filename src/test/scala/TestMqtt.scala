import xyz.octeep.tunnel.crypto.X25519.X25519PrivateKey
import xyz.octeep.tunnel.mqtt.{MClient, MServer}
import xyz.octeep.tunnel.packet.C2SPacket

import scala.concurrent.ExecutionContext.Implicits.global

object TestMqtt {

  def main(args: Array[String]): Unit = {
    val serverSecretKey = X25519PrivateKey.randomKey
    val server = MServer[String]("epic state", serverSecretKey)
    println(server.topic)
    val client = MClient[String]
    server.connect()
    client.connect()
    println("sending")
    val response = client.request(serverSecretKey.derivePublicKey, new PingPacket, 5000L)
    println(response)
  }

  case class PongPacket(state: String) extends Serializable

  class PingPacket extends C2SPacket[String] {
    override type Response = PongPacket
    override def respond(state: String): PingPacket.this.Response = PongPacket(s"response: $state")
  }

}

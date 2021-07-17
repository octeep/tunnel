import xyz.octeep.tunnel.crypto.X25519.X25519PrivateKey
import xyz.octeep.tunnel.mqtt.{MClient, MServer}
import xyz.octeep.tunnel.packet.C2SPacket

import scala.concurrent.ExecutionContext.Implicits.global

object TestMqtt {

  def main(args: Array[String]): Unit = {
    val serverSecretKey = X25519PrivateKey.randomKey
    val server = MServer(new SubState("epic state", 2), serverSecretKey)
    println(server.topic)
    val client = MClient[SubState]
    server.connect()
    client.connect()
    println("sending")
    val response = client.request(serverSecretKey.derivePublicKey, new PingPacket, 5000L)
    println(response)
  }

  class State(var message: String)
  class SubState(message: String, var extraData: Int) extends State(message)

  case class PongPacket(state: String) extends Serializable

  class PingPacket extends C2SPacket[State] {
    override type Response = PongPacket
    override def respond(state: State): PingPacket.this.Response = PongPacket(s"response: ${state.message}")
  }

}

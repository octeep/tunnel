import xyz.octeep.tunnel.crypto.X25519.X25519PrivateKey
import xyz.octeep.tunnel.mqtt.{MClient, MServer}
import xyz.octeep.tunnel.network.{ICEServerState, ICEUtil}

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext.Implicits.global

object TestServer {

  def main(args: Array[String]): Unit = {
    val state = new ICEServerState(new InetSocketAddress("localhost", 5565))
    val identity = X25519PrivateKey.randomKey
    val server = MServer(state, identity)
    server.connect()
    val client = MClient[ICEServerState]
    client.connect()
    ICEUtil.connect(client, identity.derivePublicKey, 10000).get.foreach { socket =>
      println("writein")
      socket.getOutputStream.write("big sus energy".getBytes())
      socket.getOutputStream.flush()
    }
  }

}

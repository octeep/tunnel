package xyz.octeep.tunnel.mqtt

import org.eclipse.paho.mqttv5.client.{IMqttToken, MqttCallback, MqttClient, MqttDisconnectResponse}
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.eclipse.paho.mqttv5.common.{MqttException, MqttMessage}
import xyz.octeep.tunnel.crypto.X25519.X25519PrivateKey
import xyz.octeep.tunnel.crypto._
import xyz.octeep.tunnel.packet.{C2SPacket, Serializer}

import java.security.GeneralSecurityException
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._

object MServer {
  def apply[S: TypeTag](state: S, identity: X25519PrivateKey)(implicit ec: ExecutionContext) =
    new MServer[S](state, newClient, identity)
}

class MServer[+S: TypeTag] private(val state: S, val mqttClient: MqttClient, val identity: X25519PrivateKey)(implicit ec: ExecutionContext) extends AutoCloseable {
  val topic: String = toServerTopic(identity.derivePublicKey)

  this.mqttClient.setCallback(new MqttCallback {
    override def disconnected(disconnectResponse: MqttDisconnectResponse): Unit = ()
    override def mqttErrorOccurred(exception: MqttException): Unit = ()
    override def deliveryComplete(token: IMqttToken): Unit = ()
    override def connectComplete(reconnect: Boolean, serverURI: String): Unit = ()
    override def authPacketArrived(reasonCode: Int, properties: MqttProperties): Unit = ()
    override def messageArrived(topic: String, message: MqttMessage): Unit =
      (for {
        decryptedResult <- identity.decryptFrom(message.getPayload)
        deserialized <- Serializer.deserializePacket[S, C2SPacket[S]](decryptedResult.plaintext)
        topic = toClientTopic(decryptedResult)
        _ = Future {
          val response = deserialized.respond(state)
          encrypt(decryptedResult.key, Serializer.serialize(response)).map { encryptedResponse =>
            mqttClient.publish(topic, createMessage(encryptedResponse))
          }
        }
      } yield ()).recover {
        case e: ClassCastException =>
          println(s"Server received unsupported packet: $e")
        case e: ClassNotFoundException =>
          println(s"Server received invalidly serialized packet: $e")
        case e: GeneralSecurityException =>
          println(s"Server received invalidly encrypted packet: $e")
      }
  })

  def connect(): Unit = {
    this.mqttClient.connect(options.build())
    this.mqttClient.subscribe(topic, 1)
  }

  override def close(): Unit = {
    this.mqttClient.disconnectForcibly()
    this.mqttClient.close()
  }

}

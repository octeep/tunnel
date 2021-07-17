package xyz.octeep.tunnel.mqtt

import org.eclipse.paho.mqttv5.client.{IMqttToken, MqttCallback, MqttClient, MqttDisconnectResponse}
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.eclipse.paho.mqttv5.common.{MqttException, MqttMessage}
import xyz.octeep.tunnel.crypto.X25519.X25519PublicKey
import xyz.octeep.tunnel.crypto._
import xyz.octeep.tunnel.packet.{C2SPacket, Serializer}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters._
import scala.util.Try

object MClient {
  def apply[S] = new MClient[S](newClient)
}

class MClient[S] private(val mqttClient: MqttClient) extends AutoCloseable {

  private val channels = new ConcurrentHashMap[String, AtomicReference[MqttMessage]]().asScala

  mqttClient.setCallback(new MqttCallback {
    override def disconnected(disconnectResponse: MqttDisconnectResponse): Unit = ()
    override def mqttErrorOccurred(exception: MqttException): Unit = ()
    override def deliveryComplete(token: IMqttToken): Unit = ()
    override def connectComplete(reconnect: Boolean, serverURI: String): Unit = ()
    override def authPacketArrived(reasonCode: Int, properties: MqttProperties): Unit = ()
    override def messageArrived(topic: String, message: MqttMessage): Unit =
      channels.get(topic).map { monitor =>
        monitor.set(message)
        monitor.synchronized(monitor.notify())
      }
  })

  def connect(): Unit = {
    this.mqttClient.connect(options.build())
  }

  def request(target: X25519PublicKey, request: C2SPacket[S], timeoutMillis: Long): Try[Option[request.Response]] =
    target.encryptTo(Serializer.serialize(request)).map { encryptedResult =>
      val clientTopic = toClientTopic(encryptedResult)
      this.mqttClient.subscribe(clientTopic, 1)
      val mqttMessage = createMessage(encryptedResult.ciphertext)
      val topic = toServerTopic(target)
      val monitor = new AtomicReference[MqttMessage](null)
      this.channels.put(clientTopic, monitor)
      this.mqttClient.publish(topic, mqttMessage)
      monitor.synchronized(monitor.wait(timeoutMillis))
      this.channels.remove(topic)
      for {
        response <- Option(monitor.get())
        decrypted <- decrypt(encryptedResult.key, response.getPayload).toOption
        deserialized <- Serializer.deserialize[request.Response](decrypted).toOption
      } yield deserialized
    }

  override def close(): Unit = {
    this.mqttClient.disconnectForcibly()
    this.mqttClient.close()
  }

}

package xyz.octeep.tunnel

import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.client.{MqttClient, MqttConnectionOptionsBuilder}
import org.eclipse.paho.mqttv5.common.MqttMessage
import xyz.octeep.tunnel.crypto.X25519.{CryptoResult, X25519PublicKey}
import xyz.octeep.tunnel.crypto._

import java.util.Base64

package object mqtt {
  def toServerTopic(pk: X25519PublicKey): String = "r_" + encoder.encodeToString(sha256(pk.publicKeyBytes))

  def toClientTopic(result: CryptoResult): String = "s_" + encoder.encodeToString(sha256(result.key))

  def newClient = new MqttClient(MQTT_SERVER_URI, encoder.encodeToString(randomBytes(32)), new MemoryPersistence)

  def createMessage(payload: Array[Byte], qos: Int = 1): MqttMessage = {
    val message = new MqttMessage()
    message.setQos(qos)
    message.setPayload(payload)
    message
  }

  val MQTT_SERVER_URI = "tcp://broker.hivemq.com"

  val options: MqttConnectionOptionsBuilder =
    new MqttConnectionOptionsBuilder()
      .automaticReconnect(true)
      .connectionTimeout(10)
      .cleanStart(true)

  val encoder: Base64.Encoder = Base64.getUrlEncoder.withoutPadding
}

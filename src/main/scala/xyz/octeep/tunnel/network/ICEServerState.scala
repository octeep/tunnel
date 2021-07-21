package xyz.octeep.tunnel.network

import org.ice4j.ice.{Agent, IceProcessingState, NominationStrategy}
import org.ice4j.pseudotcp.{PseudoTcpSocket, PseudoTcpSocketFactory}
import xyz.octeep.ice.SdpUtils
import xyz.octeep.tunnel.crypto.randomBytes
import xyz.octeep.tunnel.network.ICEConnectionStartPacket.ICEResponse
import xyz.octeep.tunnel.network.ICEServerState.{EncryptionSpec, inputStream, outputStream, randomEncryptionSpec}

import java.beans.PropertyChangeEvent
import java.io.{InputStream, OutputStream}
import java.net.{InetSocketAddress, Socket}
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.{Cipher, CipherInputStream, CipherOutputStream}
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala

abstract class ICEServerState(val enableEncryption: Boolean)(implicit ec: ExecutionContext) {

  private val onlineIncomingConnections = new ConcurrentHashMap[Long, Option[Socket]]().asScala

  def targetAddress(): InetSocketAddress
  def createAgent(): Agent = ICEUtil.createAgent()

  private def startSocket(packet: ICEConnectionStartPacket, encryptionSpec: Option[EncryptionSpec])(agent: Agent) = {
    val dataStream = agent.getStream("data")
    val udpComponent = dataStream.getComponents.get(0)
    val task = for {
      usedPair <- Option(udpComponent.getSelectedPair)
      remoteCandidate = usedPair.getRemoteCandidate
      datagramSocket <- Option(usedPair.getDatagramSocket)
    } yield Future {
      val socket = new PseudoTcpSocketFactory().createSocket(datagramSocket)
      socket.setConversationID(packet.conversationID)
      socket.setMTU(1500)
      socket.setDebugName("L")
      socket.accept(remoteCandidate.getTransportAddress, 5000)
      handleIncomingConnection(packet, encryptionSpec, socket)
    }
    task.getOrElse(Future.unit)
  }

  def acceptConnection(packet: ICEConnectionStartPacket): Option[ICEResponse] =
    Option.unless(this.onlineIncomingConnections.contains(packet.conversationID)) {
      this.onlineIncomingConnections.put(packet.conversationID, None)
      val localAgent = createAgent()
      localAgent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO)
      localAgent.setControlling(true)
      SdpUtils.parseSDP(localAgent, packet.sdp)
      val encryptionSpec = Option.when(this.enableEncryption)(randomEncryptionSpec())
      localAgent.addStateChangeListener(new ICEPropertyChangeListener(startSocket(packet, encryptionSpec)))
      localAgent.addStateChangeListener((evt: PropertyChangeEvent) =>
        if (evt.getNewValue == IceProcessingState.FAILED) closeConnection(packet.conversationID))
      localAgent.startConnectivityEstablishment()
      val sdp = SdpUtils.createSDPDescription(localAgent)
      ICEResponse(sdp, encryptionSpec)
    }

  def closeConnection(conversationID: Long): Unit = {
    this.onlineIncomingConnections.get(conversationID).flatten.foreach(_.close())
    this.onlineIncomingConnections.remove(conversationID)
  }

  def createSocket(packet: ICEConnectionStartPacket): Socket = {
    val socket = new Socket()
    socket.setTcpNoDelay(true)
    socket.setReceiveBufferSize(65536)
    socket.setSendBufferSize(1024)
    socket.connect(this.targetAddress())
    socket
  }

  def handleIncomingConnection(packet: ICEConnectionStartPacket, encryptionSpec: Option[EncryptionSpec], pseudoSocket: PseudoTcpSocket): Unit = {
    try {
      val socket = createSocket(packet)
      this.onlineIncomingConnections.put(pseudoSocket.getConversationID, Some(socket))
      Future(inputStream(encryptionSpec)(socket.getInputStream).transferTo(pseudoSocket.getOutputStream))
      pseudoSocket.getInputStream.transferTo(outputStream(encryptionSpec)(socket.getOutputStream))
    } finally {
      closeConnection(pseudoSocket.getConversationID)
      pseudoSocket.close()
    }
  }

}

object ICEServerState {

  case class EncryptionSpec(key: Array[Byte], iv: Array[Byte]) extends Serializable

  def outputStream(option: Option[EncryptionSpec])(outputStream: OutputStream): OutputStream =
    option.map { spec =>
      val cipher = Cipher.getInstance("AES/CTR/NoPadding")
      val keySpec = new SecretKeySpec(spec.key, "AES")
      val paramSpec = new IvParameterSpec(spec.iv)
      cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec)
      new CipherOutputStream(outputStream, cipher)
    }.getOrElse(outputStream)

  def inputStream(option: Option[EncryptionSpec])(inputStream: InputStream): InputStream =
    option.map { spec =>
      val cipher = Cipher.getInstance("AES/CTR/NoPadding")
      val keySpec = new SecretKeySpec(spec.key, "AES")
      val paramSpec = new IvParameterSpec(spec.iv)
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec)
      new CipherInputStream(inputStream, cipher)
    }.getOrElse(inputStream)

  private[network] def randomEncryptionSpec() =
    EncryptionSpec(randomBytes(16), randomBytes(16))

}
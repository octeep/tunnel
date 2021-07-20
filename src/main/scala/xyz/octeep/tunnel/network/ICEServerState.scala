package xyz.octeep.tunnel.network

import org.ice4j.ice.{Agent, IceProcessingState, NominationStrategy}
import org.ice4j.pseudotcp.{PseudoTcpSocket, PseudoTcpSocketFactory}
import xyz.octeep.ice.SdpUtils

import java.beans.PropertyChangeEvent
import java.net.{InetSocketAddress, Socket}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

class ICEServerState(targetAddress: InetSocketAddress)(implicit ec: ExecutionContext) {

  private val onlineIncomingConnections = ConcurrentHashMap.newKeySet[Long]()

  private def startSocket(packet: ICEPacket)(agent: Agent) = {
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
      handleIncomingConnection(socket)
    }
    task.getOrElse(Future.unit)
  }

  def acceptConnection(packet: ICEPacket): Option[String] =
    Option.when(this.onlineIncomingConnections.add(packet.conversationID)) {
      val localAgent = ICEUtil.createAgent()
      localAgent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO)
      localAgent.setControlling(true)
      SdpUtils.parseSDP(localAgent, packet.sdp)
      localAgent.addStateChangeListener(new ICEPropertyChangeListener(startSocket(packet)))
      localAgent.addStateChangeListener((evt: PropertyChangeEvent) =>
        if (evt.getNewValue == IceProcessingState.FAILED) this.onlineIncomingConnections.remove(packet.conversationID))
      localAgent.startConnectivityEstablishment()
      SdpUtils.createSDPDescription(localAgent)
    }

  def handleIncomingConnection(pseudoSocket: PseudoTcpSocket): Unit = {
    val socket = new Socket()
    try {
      socket.setTcpNoDelay(true)
      socket.setReceiveBufferSize(65536)
      socket.setSendBufferSize(1024)
      socket.connect(this.targetAddress)
      Future(socket.getInputStream.transferTo(pseudoSocket.getOutputStream))
      pseudoSocket.getInputStream.transferTo(socket.getOutputStream)
    } finally {
      this.onlineIncomingConnections.remove(pseudoSocket.getConversationID)
      socket.close()
    }
  }

}

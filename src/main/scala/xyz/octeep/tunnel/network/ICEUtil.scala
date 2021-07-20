package xyz.octeep.tunnel.network

import org.ice4j.ice.harvest.{StunCandidateHarvester, UPNPHarvester}
import org.ice4j.ice.{Agent, IceMediaStream}
import org.ice4j.pseudotcp.{PseudoTcpSocket, PseudoTcpSocketFactory}
import org.ice4j.{Transport, TransportAddress}
import xyz.octeep.ice.SdpUtils
import xyz.octeep.tunnel.crypto.X25519.X25519PublicKey
import xyz.octeep.tunnel.mqtt.MClient
import xyz.octeep.tunnel.network.ICEServerState.EncryptionSpec

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}

object ICEUtil {

  case class Connector(pseudoTcpSocket: PseudoTcpSocket, encryptionSpec: Option[EncryptionSpec])

  def connect(client: MClient[ICEServerState], target: X25519PublicKey, timeout: Long)(implicit ec: ExecutionContext): Try[Option[Connector]] = Try {
    val remoteAgent = createAgent()
    val socketRef = new AtomicReference[Option[PseudoTcpSocket]](None)
    val connectionID = randomConnectionID
    remoteAgent.addStateChangeListener(new ICEPropertyChangeListener(handleChange(connectionID, socketRef)))
    remoteAgent.setControlling(false)
    val remoteSDP = SdpUtils.createSDPDescription(remoteAgent)
    val now = System.currentTimeMillis()
    val response = client.request(target, new ICEConnectionStartPacket(connectionID, remoteSDP), timeout).get
    val elapsedTime = System.currentTimeMillis() - now
    val remainingTimeout = timeout - elapsedTime
    response match {
      case Some(result) if remainingTimeout > 0 =>
        SdpUtils.parseSDP(remoteAgent, result.sdp)
        remoteAgent.startConnectivityEstablishment()
        socketRef.synchronized {
          socketRef.wait(remainingTimeout)
          socketRef.get()
        }.map(Connector(_, result.encryptionSpec))
      case _ => None
    }
  }

  private def handleChange(connID: Long, ref: AtomicReference[Option[PseudoTcpSocket]])(agent: Agent)(implicit ec: ExecutionContext) = {
    val dataStream = agent.getStream("data")
    val udpComponent = dataStream.getComponents.get(0)
    val result = for {
      usedPair <- Option(udpComponent.getSelectedPair)
      remoteCandidate = usedPair.getRemoteCandidate
      dgramSocket <- Option(usedPair.getDatagramSocket)
    } yield Future {
      val socket = new PseudoTcpSocketFactory().createSocket(dgramSocket)
      socket.setConversationID(connID)
      socket.setMTU(1500)
      socket.setDebugName("R")
      socket.setTcpNoDelay(true)
      socket.setReceiveBufferSize(65536)
      socket.setSendBufferSize(1024)
      socket.connect(remoteCandidate.getTransportAddress, 5000)
      ref.synchronized {
        ref.set(Some(socket))
        ref.notifyAll()
      }
    }
    result.getOrElse(Future.unit)
  }

  private val minPort = 49152
  private val random = new Random()

  def randomPort(): Int = {
    val i = random.nextInt(60000 - minPort)
    i + minPort
  }

  def randomConnectionID: Long =
    Math.abs(random.nextInt()).toLong

  def createAgent(pTcpPort: Int = ICEUtil.randomPort()): Agent = {
    val agent = new Agent()
    agent.addCandidateHarvester(new StunCandidateHarvester(new TransportAddress("stun.nextcloud.com", 443, Transport.UDP)))
    agent.addCandidateHarvester(new StunCandidateHarvester(new TransportAddress("stun.nottingham.ac.uk", 3478, Transport.UDP)))
    agent.addCandidateHarvester(new UPNPHarvester)
    createStream(pTcpPort, "data", agent)
    agent
  }

  def createStream(pTcpPort: Int, streamName: String, agent: Agent): IceMediaStream = {
    val stream = agent.createMediaStream(streamName)
    agent.createComponent(stream, Transport.UDP, pTcpPort, pTcpPort, pTcpPort + 1000)
    stream
  }

}

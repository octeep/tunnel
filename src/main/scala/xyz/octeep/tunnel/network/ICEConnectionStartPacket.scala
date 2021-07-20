package xyz.octeep.tunnel.network

import xyz.octeep.tunnel.network.ICEConnectionStartPacket.ICEResponse
import xyz.octeep.tunnel.packet.C2SPacket

import scala.util.Try

case class ICEConnectionStartPacket(conversationID: Long, sdp: String) extends C2SPacket[ICEServerState, ICEResponse] {
  override def respond(state: ICEServerState): Option[ICEResponse] =
    Try(state.acceptConnection(this)).toOption.flatten.map(ICEResponse)
}

object ICEConnectionStartPacket {

  case class ICEResponse(sdp: String) extends Serializable

}

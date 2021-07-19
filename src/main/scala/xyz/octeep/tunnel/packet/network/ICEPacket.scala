package xyz.octeep.tunnel.packet.network

import xyz.octeep.tunnel.packet.C2SPacket
import xyz.octeep.tunnel.packet.network.ICEPacket.ICEResponse

import scala.util.Try

case class ICEPacket(conversationID: Long, sdp: String) extends C2SPacket[ICEServerState] {
  override type Response = ICEResponse
  override def respond(state: ICEServerState): Option[ICEResponse] =
    Try(state.acceptConnection(this)).toOption.flatten.map(ICEResponse)
}

object ICEPacket {

  case class ICEResponse(sdp: String) extends Serializable

}
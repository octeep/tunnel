package xyz.octeep.tunnel.network

import xyz.octeep.tunnel.network.ICEConnectionStartPacket.ICEResponse
import xyz.octeep.tunnel.packet.C2SPacket

import scala.util.Try
import xyz.octeep.tunnel.network.ICEServerState.EncryptionSpec

case class ICEConnectionStartPacket(conversationID: Long, sdp: String) extends C2SPacket[ICEServerState, ICEResponse] {
  override def respond(state: ICEServerState): Option[ICEResponse] =
    Try(state.acceptConnection(this)).toOption.flatten
}

object ICEConnectionStartPacket {

  case class ICEResponse(sdp: String, encryptionSpec: Option[EncryptionSpec]) extends Serializable

}

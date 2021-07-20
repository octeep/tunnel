package xyz.octeep.tunnel.network

import xyz.octeep.tunnel.packet.C2SPacket

class ICEConnectionStopPacket(conversationID: Long) extends C2SPacket[ICEServerState] {
  override type Response = Nothing
  override def respond(state: ICEServerState): Option[Nothing] = {
    state.closeConnection(conversationID)
    None
  }
}

package xyz.octeep.tunnel.packet

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

abstract class C2SPacket[-S: TypeTag] extends Serializable {
  type Response <: Serializable
  def respond(state: S): Option[Response]

  def stateTag: universe.Type = typeOf[S]
}

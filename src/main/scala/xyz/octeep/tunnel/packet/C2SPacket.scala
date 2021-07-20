package xyz.octeep.tunnel.packet

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

abstract class C2SPacket[-S: TypeTag, +R <: Serializable : TypeTag] extends Serializable {
  def respond(state: S): Option[R]

  def stateTag: universe.Type = typeOf[S]
  def responseTag: universe.Type = typeOf[R]
}

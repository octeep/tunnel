package xyz.octeep.tunnel.packet

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

abstract class C2SPacket[S: TypeTag] extends Serializable {
  type Response <: Serializable
  def respond(state: S): Response

  def stateTag: universe.TypeTag[S] = typeTag[S]
}

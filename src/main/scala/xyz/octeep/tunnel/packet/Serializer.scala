package xyz.octeep.tunnel.packet


import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.reflect.runtime.universe._
import scala.util.{Try, Using}

object Serializer {

  def serialize(obj: Serializable): Array[Byte] =
    Using.Manager { use =>
      val baos = use(new ByteArrayOutputStream())
      val stream = use(new ObjectOutputStream(baos))
      stream.writeObject(obj)
      baos.toByteArray
    }.get

  def deserialize[T <: Serializable](body: Array[Byte]): Try[T] =
    Using.Manager { use =>
      val bais = use(new ByteArrayInputStream(body))
      val stream = use(new ObjectInputStream(bais))
      stream.readObject().asInstanceOf[T]
    }

  def deserializePacket[S, T <: C2SPacket[S]](body: Array[Byte])(implicit stateTag: TypeTag[S]): Try[T] =
    deserialize[T](body).map {
      case x if x.stateTag == stateTag => x
      case x => throw new ClassCastException(s"Invalid matching state type: ${x.stateTag} != $stateTag")
    }

}

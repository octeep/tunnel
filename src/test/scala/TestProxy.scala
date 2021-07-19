import java.net.{InetSocketAddress, ServerSocket, Socket}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TestProxy {

  def main(args: Array[String]): Unit = {
    val server = new ServerSocket(25567)
    while (true) {
      val client = server.accept()
      Future {
        val cubecraft = new Socket()
        cubecraft.setTcpNoDelay(true)
        cubecraft.setReceiveBufferSize(65536)
        cubecraft.setSendBufferSize(1024)
        cubecraft.connect(new InetSocketAddress("play.cubecraft.net", 25565))
        Future(cubecraft.getInputStream.transferTo(client.getOutputStream))
        client.getInputStream.transferTo(cubecraft.getOutputStream)
      }
    }
  }

}

package xyz.octeep.tunnel

import org.bitcoinj.core.Base58
import xyz.octeep.tunnel.crypto.X25519.{X25519PrivateKey, X25519PublicKey}
import xyz.octeep.tunnel.mqtt.{MClient, MServer}
import xyz.octeep.tunnel.network.{ICEServerState, ICEUtil}

import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import java.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Start {

  def startServer(group: Main.MainGroup.ServerArgGroup): Unit =
    Try(Base64.getDecoder.decode(group.secretKey)).toOption match {
      case Some(secretKey) if secretKey.length == 32 =>
        group.endpoint.split(':') match {
          case Array(ip, port) =>
            port.toIntOption match {
              case Some(value) if value > 0 && value < 65536 =>
                try {
                  val address = InetAddress.getAllByName(ip)(0)
                  val socketAddress = new InetSocketAddress(address, value)
                  startServer(X25519PrivateKey(secretKey), socketAddress)
                } catch {
                  case _: Exception =>
                    println("Unable to resolve address.")
                }
              case Some(_) => println("Port number should be > 0 and < 65536.")
              case None => println("Invalid port number.")
            }
          case _ => println("Endpoint should be ip:port.")
        }
      case Some(_) =>
        println("Secret key size is not 32 bytes.")
      case None =>
        println("Invalid base64 string.")
    }

  private def startServer(secretKey: X25519PrivateKey, address: InetSocketAddress): Unit = {
    val state = new ICEServerState(address)
    val server = MServer(state, secretKey)
    server.connect()
    println(s"Your server ID is: ${Base58.encodeChecked(secretKey.derivePublicKey.publicKeyBytes)}")
  }

  def startClient(group: Main.MainGroup.ClientArgGroup): Unit =
    Try(Base58.decodeChecked(group.serverID)).toOption match {
      case Some(serverID) =>
          if(group.port > 0 && group.port < 65536)
            startClient(X25519PublicKey(serverID), group.port)
          else
            println("Port number should be > 0 and < 65536.")
      case None => println("Invalid server ID. Check if you have a typo.")
    }

  private def startClient(target: X25519PublicKey, localPort: Int): Unit = {
    println("Starting client")
    val server = new ServerSocket()
    server.bind(new InetSocketAddress("127.0.0.1", localPort))
    val mClient = MClient[ICEServerState]
    mClient.connect()
    println(s"Listening for connection at 127.0.0.1:$localPort")
    while (!server.isClosed) {
      val incoming = server.accept()
      println(s"Connection accepted from ${incoming.getRemoteSocketAddress}")
      Future {
        ICEUtil.connect(mClient, target, 15000L) match {
          case Failure(exception) =>
            println(s"Failed to establish connection to remote server: $exception")
          case Success(None) =>
            println("Remote server didn't respond / responded with invalid data.")
          case Success(Some(pSocket)) =>
            Future(pSocket.getInputStream.transferTo(incoming.getOutputStream))
            incoming.getInputStream.transferTo(pSocket.getOutputStream)
        }
      }
    }
  }

}

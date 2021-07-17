package xyz.octeep.tunnel

import org.whispersystems.curve25519.OpportunisticCurve25519Provider

import java.security.{MessageDigest, SecureRandom}
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import scala.util.Try

package object crypto {

  def sha256(text: Array[Byte]): Array[Byte] = sha256Digest.digest(text)

  def encrypt(key: Array[Byte], plaintext: Array[Byte]): Try[Array[Byte]] = Try {
    val iv = randomBytes(12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val keySpec = new SecretKeySpec(key, "AES")
    val parameterSpec = new GCMParameterSpec(96, iv)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec)
    val cipherText = cipher.doFinal(plaintext)
    val concatedCipherText = new Array[Byte](iv.length + cipherText.length)
    System.arraycopy(iv, 0, concatedCipherText, 0, iv.length)
    System.arraycopy(cipherText, 0, concatedCipherText, 12, cipherText.length)
    concatedCipherText
  }

  def decrypt(key: Array[Byte], concatedCipherText: Array[Byte]): Try[Array[Byte]] = Try {
    val iv = new Array[Byte](12)
    val cipherText = new Array[Byte](concatedCipherText.length - 12)
    System.arraycopy(concatedCipherText, 0, iv, 0, iv.length)
    System.arraycopy(concatedCipherText, 12, cipherText, 0, cipherText.length)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val keySpec = new SecretKeySpec(key, "AES")
    val parameterSpec = new GCMParameterSpec(96, iv)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec)
    cipher.doFinal(cipherText)
  }

  def randomBytes(size: Int): Array[Byte] = {
    val body = new Array[Byte](size)
    secureRandom.nextBytes(body)
    body
  }

  private def sha256Digest = MessageDigest.getInstance("SHA-256")

  private[crypto] var provider = {
    val constructor = classOf[OpportunisticCurve25519Provider].getDeclaredConstructor()
    constructor.setAccessible(true)
    constructor.newInstance()
  }

  val secureRandom = new SecureRandom

}

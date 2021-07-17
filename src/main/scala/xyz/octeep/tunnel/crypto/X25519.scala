package xyz.octeep.tunnel.crypto

import org.whispersystems.curve25519.Curve25519

import scala.util.Try

object X25519 {

  private val BACKEND = Curve25519.getInstance(Curve25519.BEST)

  case class X25519PublicKey(publicKeyBytes: Array[Byte]) {
    if (publicKeyBytes.length != 32) throw new IllegalArgumentException("array size must be 32")

    def encryptTo(plaintext: Array[Byte]): Try[EncryptResult] = {
      val ephemeralKey = X25519PrivateKey.randomKey
      val ephemeralPublicKey = ephemeralKey.derivePublicKey
      val secret = ephemeralKey.dh(this)
      encrypt(secret, plaintext).map { cipherText =>
        val concatedCipherText = new Array[Byte](cipherText.length + 32)
        System.arraycopy(ephemeralPublicKey.publicKeyBytes, 0, concatedCipherText, 0, ephemeralPublicKey.publicKeyBytes.length)
        System.arraycopy(cipherText, 0, concatedCipherText, 32, cipherText.length)
        EncryptResult(concatedCipherText, secret)
      }
    }
  }

  object X25519PrivateKey {
    def randomKey = new X25519PrivateKey(BACKEND.generateKeyPair.getPrivateKey)
    def apply(privateKey: Array[Byte]): X25519PrivateKey = {
      if (privateKey.length != 32) throw new IllegalArgumentException("array size must be 32")
      val privateKeyBytes = provider.generatePrivateKey(privateKey)
      new X25519PrivateKey(privateKeyBytes)
    }
  }

  case class X25519PrivateKey private(privateKeyBytes: Array[Byte]) {
    def dh(publicKey: X25519PublicKey): Array[Byte] =
      provider.calculateAgreement(privateKeyBytes, publicKey.publicKeyBytes)

    def derivePublicKey: X25519PublicKey = {
      val publicKey = provider.generatePublicKey(privateKeyBytes)
      X25519PublicKey(publicKey)
    }

    def decryptFrom(concatedCipherText: Array[Byte]): Try[DecryptResult] = {
      val ephemeralPublicKey = new Array[Byte](32)
      val cipherText = new Array[Byte](concatedCipherText.length - 32)
      System.arraycopy(concatedCipherText, 0, ephemeralPublicKey, 0, ephemeralPublicKey.length)
      System.arraycopy(concatedCipherText, 32, cipherText, 0, cipherText.length)
      val secret = this.dh(X25519PublicKey(ephemeralPublicKey))
      decrypt(secret, cipherText).map(DecryptResult(_, secret))
    }
  }

  sealed abstract class CryptoResult(val key: Array[Byte])
  case class EncryptResult(ciphertext: Array[Byte], override val key: Array[Byte]) extends CryptoResult(key)
  case class DecryptResult(plaintext: Array[Byte], override val key: Array[Byte]) extends CryptoResult(key)

}

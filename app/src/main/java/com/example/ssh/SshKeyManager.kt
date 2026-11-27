package com.example.ssh

import android.util.Base64
import com.example.data.model.SshKeyEntity
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

enum class KeyAlgorithmOption(val label: String, val jschType: Int, val keySize: Int) {
  RSA_2048("RSA 2048", KeyPair.RSA, 2048),
  RSA_4096("RSA 4096", KeyPair.RSA, 4096),
  ECDSA_256("ECDSA 256", KeyPair.ECDSA, 256),
}

object SshKeyManager {
  fun generateKeyPair(
    name: String,
    option: KeyAlgorithmOption,
    passphrase: String = "",
    comment: String = "grassssh@android",
  ): SshKeyEntity {
    val jsch = JSch()
    val keyPair = KeyPair.genKeyPair(jsch, option.jschType, option.keySize)
    try {
      val privOut = ByteArrayOutputStream()
      val pubOut = ByteArrayOutputStream()
      if (passphrase.isNotEmpty()) {
        keyPair.writePrivateKey(privOut, passphrase.toByteArray(Charsets.UTF_8))
      } else {
        keyPair.writePrivateKey(privOut)
      }
      keyPair.writePublicKey(pubOut, comment)

      val privPem = privOut.toString(Charsets.UTF_8.name())
      val pubOpenSsh = pubOut.toString(Charsets.UTF_8.name()).trim()
      val fingerprint = computeSha256Fingerprint(pubOpenSsh, keyPair.publicKeyBlob)

      return SshKeyEntity(
        name = name.trim(),
        algorithm = option.label,
        privateKeyPem = privPem,
        publicKeyOpenSsh = pubOpenSsh,
        fingerprintSha256 = fingerprint,
        hasPassphrase = passphrase.isNotEmpty(),
      )
    } finally {
      keyPair.dispose()
    }
  }

  fun importPrivateKey(
    name: String,
    privateKeyPem: String,
    passphrase: String = "",
  ): Result<SshKeyEntity> = runCatching {
    val trimmed = privateKeyPem.trim()
    require(trimmed.contains("PRIVATE KEY")) {
      "Input does not look like a valid PEM or OpenSSH private key."
    }

    val jsch = JSch()
    val privBytes = trimmed.toByteArray(Charsets.UTF_8)
    val keyPair = KeyPair.load(jsch, privBytes, null)
    try {
      val isEncrypted = keyPair.isEncrypted
      if (isEncrypted && passphrase.isNotEmpty()) {
        val unlocked = keyPair.decrypt(passphrase.toByteArray(Charsets.UTF_8))
        require(unlocked) { "Incorrect passphrase for encrypted private key." }
      }

      val pubOut = ByteArrayOutputStream()
      var publicKeyOpenSsh = ""
      var fingerprint = "Encrypted (unlocked on connect)"
      if (!keyPair.isEncrypted) {
        keyPair.writePublicKey(pubOut, "imported@grassssh")
        publicKeyOpenSsh = pubOut.toString(Charsets.UTF_8.name()).trim()
        fingerprint = computeSha256Fingerprint(publicKeyOpenSsh, keyPair.publicKeyBlob)
      }

      val algoLabel = when (keyPair.keyType) {
        KeyPair.RSA -> "RSA (Imported)"
        KeyPair.ECDSA -> "ECDSA (Imported)"
        KeyPair.ED25519 -> "Ed25519 (Imported)"
        KeyPair.DSA -> "DSA (Imported)"
        else -> "SSH Key (Imported)"
      }

      SshKeyEntity(
        name = name.trim(),
        algorithm = algoLabel,
        privateKeyPem = trimmed,
        publicKeyOpenSsh = publicKeyOpenSsh,
        fingerprintSha256 = fingerprint,
        hasPassphrase = isEncrypted,
      )
    } finally {
      keyPair.dispose()
    }
  }

  fun computeSha256FingerprintFromBlob(blob: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(blob)
    val encoded = Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
    return "SHA256:$encoded"
  }

  private fun computeSha256Fingerprint(openSshPub: String, blob: ByteArray?): String {
    if (blob != null && blob.isNotEmpty()) {
      return computeSha256FingerprintFromBlob(blob)
    }
    val parts = openSshPub.split(Regex("\\s+"))
    if (parts.size >= 2) {
      val decoded = Base64.decode(parts[1], Base64.DEFAULT)
      return computeSha256FingerprintFromBlob(decoded)
    }
    return "SHA256:unknown"
  }
}

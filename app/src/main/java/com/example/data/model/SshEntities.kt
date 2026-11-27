package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AuthType(val label: String) {
  PASSWORD("Password"),
  PRIVATE_KEY("SSH Key"),
  ASK_ON_CONNECT("Ask Every Time"),
}

@Entity(tableName = "ssh_hosts")
data class SshHostEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val alias: String,
  val hostname: String,
  val port: Int = 22,
  val username: String,
  val authType: AuthType = AuthType.PASSWORD,
  val password: String = "",
  val keyId: Long? = null,
  val groupName: String = "General",
  val startupCommand: String = "",
  val lastConnectedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val algorithm: String,
  val privateKeyPem: String,
  val publicKeyOpenSsh: String,
  val fingerprintSha256: String,
  val hasPassphrase: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "known_hosts")
data class KnownHostEntity(
  @PrimaryKey val hostKeyId: String,
  val hostname: String,
  val port: Int,
  val keyType: String,
  val fingerprintSha256: String,
  val base64Key: String,
  val trustedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "snippets")
data class SnippetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val command: String,
  val category: String = "System",
  val createdAt: Long = System.currentTimeMillis(),
)

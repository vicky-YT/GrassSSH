package com.example.data.repository

import com.example.data.local.KnownHostDao
import com.example.data.local.SnippetDao
import com.example.data.local.SshHostDao
import com.example.data.local.SshKeyDao
import com.example.data.model.KnownHostEntity
import com.example.data.model.SnippetEntity
import com.example.data.model.SshHostEntity
import com.example.data.model.SshKeyEntity
import kotlinx.coroutines.flow.Flow

class SshRepository(
  private val hostDao: SshHostDao,
  private val keyDao: SshKeyDao,
  private val knownHostDao: KnownHostDao,
  private val snippetDao: SnippetDao,
) {
  val hosts: Flow<List<SshHostEntity>> = hostDao.observeAllHosts()
  val keys: Flow<List<SshKeyEntity>> = keyDao.observeAllKeys()
  val knownHosts: Flow<List<KnownHostEntity>> = knownHostDao.observeAllKnownHosts()
  val snippets: Flow<List<SnippetEntity>> = snippetDao.observeAllSnippets()

  suspend fun getHostById(id: Long): SshHostEntity? = hostDao.getHostById(id)

  suspend fun saveHost(host: SshHostEntity): Long = hostDao.insertHost(host)

  suspend fun markHostConnected(id: Long) {
    hostDao.markConnected(id, System.currentTimeMillis())
  }

  suspend fun deleteHost(id: Long) = hostDao.deleteHostById(id)

  suspend fun getKeyById(id: Long): SshKeyEntity? = keyDao.getKeyById(id)

  suspend fun saveKey(key: SshKeyEntity): Long = keyDao.insertKey(key)

  suspend fun deleteKey(id: Long) = keyDao.deleteKeyById(id)

  suspend fun getKnownHost(hostKeyId: String): KnownHostEntity? = knownHostDao.getKnownHost(hostKeyId)

  suspend fun trustKnownHost(knownHost: KnownHostEntity) = knownHostDao.upsertKnownHost(knownHost)

  suspend fun forgetKnownHost(hostKeyId: String) = knownHostDao.deleteKnownHost(hostKeyId)

  suspend fun saveSnippet(snippet: SnippetEntity): Long = snippetDao.insertSnippet(snippet)

  suspend fun deleteSnippet(id: Long) = snippetDao.deleteSnippetById(id)
}

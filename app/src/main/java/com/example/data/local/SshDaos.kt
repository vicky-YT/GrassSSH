package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.KnownHostEntity
import com.example.data.model.SnippetEntity
import com.example.data.model.SshHostEntity
import com.example.data.model.SshKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SshHostDao {
  @Query("SELECT * FROM ssh_hosts ORDER BY COALESCE(lastConnectedAt, 0) DESC, createdAt DESC")
  fun observeAllHosts(): Flow<List<SshHostEntity>>

  @Query("SELECT * FROM ssh_hosts WHERE id = :id LIMIT 1")
  suspend fun getHostById(id: Long): SshHostEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHost(host: SshHostEntity): Long

  @Update
  suspend fun updateHost(host: SshHostEntity)

  @Query("UPDATE ssh_hosts SET lastConnectedAt = :timestamp WHERE id = :id")
  suspend fun markConnected(id: Long, timestamp: Long)

  @Query("DELETE FROM ssh_hosts WHERE id = :id")
  suspend fun deleteHostById(id: Long)
}

@Dao
interface SshKeyDao {
  @Query("SELECT * FROM ssh_keys ORDER BY createdAt DESC")
  fun observeAllKeys(): Flow<List<SshKeyEntity>>

  @Query("SELECT * FROM ssh_keys WHERE id = :id LIMIT 1")
  suspend fun getKeyById(id: Long): SshKeyEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertKey(key: SshKeyEntity): Long

  @Query("DELETE FROM ssh_keys WHERE id = :id")
  suspend fun deleteKeyById(id: Long)
}

@Dao
interface KnownHostDao {
  @Query("SELECT * FROM known_hosts ORDER BY trustedAt DESC")
  fun observeAllKnownHosts(): Flow<List<KnownHostEntity>>

  @Query("SELECT * FROM known_hosts WHERE hostKeyId = :hostKeyId LIMIT 1")
  suspend fun getKnownHost(hostKeyId: String): KnownHostEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertKnownHost(knownHost: KnownHostEntity)

  @Query("DELETE FROM known_hosts WHERE hostKeyId = :hostKeyId")
  suspend fun deleteKnownHost(hostKeyId: String)
}

@Dao
interface SnippetDao {
  @Query("SELECT * FROM snippets ORDER BY category ASC, createdAt DESC")
  fun observeAllSnippets(): Flow<List<SnippetEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSnippet(snippet: SnippetEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(snippets: List<SnippetEntity>)

  @Query("DELETE FROM snippets WHERE id = :id")
  suspend fun deleteSnippetById(id: Long)
}

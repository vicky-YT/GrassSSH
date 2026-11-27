package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AuthType
import com.example.data.model.KnownHostEntity
import com.example.data.model.SnippetEntity
import com.example.data.model.SshHostEntity
import com.example.data.model.SshKeyEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SshTypeConverters {
  @TypeConverter
  fun fromAuthType(value: AuthType): String = value.name

  @TypeConverter
  fun toAuthType(value: String): AuthType =
    runCatching { AuthType.valueOf(value) }.getOrDefault(AuthType.PASSWORD)
}

@Database(
  entities = [
    SshHostEntity::class,
    SshKeyEntity::class,
    KnownHostEntity::class,
    SnippetEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
@TypeConverters(SshTypeConverters::class)
abstract class GrassSshDatabase : RoomDatabase() {
  abstract fun hostDao(): SshHostDao
  abstract fun keyDao(): SshKeyDao
  abstract fun knownHostDao(): KnownHostDao
  abstract fun snippetDao(): SnippetDao

  companion object {
    @Volatile
    private var INSTANCE: GrassSshDatabase? = null

    fun getInstance(context: Context): GrassSshDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
      }
    }

    private fun buildDatabase(appContext: Context): GrassSshDatabase {
      lateinit var db: GrassSshDatabase
      db = Room.databaseBuilder(appContext, GrassSshDatabase::class.java, "grass_ssh.db")
        .addCallback(object : Callback() {
          override fun onCreate(sqliteDb: SupportSQLiteDatabase) {
            super.onCreate(sqliteDb)
            CoroutineScope(Dispatchers.IO).launch {
              db.snippetDao().insertAll(defaultSnippets())
            }
          }
        })
        .build()
      return db
    }

    private fun defaultSnippets(): List<SnippetEntity> = listOf(
      SnippetEntity(
        title = "System Uptime & Load",
        command = "uptime && uname -a",
        category = "System",
      ),
      SnippetEntity(
        title = "Disk Space Usage",
        command = "df -h",
        category = "System",
      ),
      SnippetEntity(
        title = "Memory & Swap",
        command = "free -m",
        category = "System",
      ),
      SnippetEntity(
        title = "Docker Running Containers",
        command = "docker ps --format \"table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\"",
        category = "Docker",
      ),
      SnippetEntity(
        title = "Listening TCP/UDP Ports",
        command = "ss -tulnp",
        category = "Network",
      ),
      SnippetEntity(
        title = "Failed Systemd Units",
        command = "systemctl --failed",
        category = "System",
      ),
      SnippetEntity(
        title = "Tail System Journal",
        command = "journalctl -n 50 --no-pager",
        category = "Logs",
      ),
      SnippetEntity(
        title = "Git Repo Status & Log",
        command = "git status -sb && git log -n 5 --oneline",
        category = "Git",
      ),
    )
  }
}

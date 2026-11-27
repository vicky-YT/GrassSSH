package com.example

import android.content.Context
import com.example.ai.GrassSshAiRepository
import com.example.data.local.GrassSshDatabase
import com.example.data.local.SettingsRepository
import com.example.data.repository.SshRepository
import com.example.ssh.SshSessionManager

class AppContainer(context: Context) {
  private val database = GrassSshDatabase.getInstance(context)

  val sshRepository: SshRepository = SshRepository(
    hostDao = database.hostDao(),
    keyDao = database.keyDao(),
    knownHostDao = database.knownHostDao(),
    snippetDao = database.snippetDao(),
  )

  val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)

  val sessionManager: SshSessionManager = SshSessionManager(sshRepository)

  val aiRepository: GrassSshAiRepository = GrassSshAiRepository()
}

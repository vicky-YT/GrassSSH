package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.ai.AiChatMessage
import com.example.ai.AiSystemUiState
import com.example.ai.BuildPlanUiState
import com.example.ai.ConsoleErrorDetector
import com.example.ai.ConsoleFixPlan
import com.example.ai.ConsoleFixUiState
import com.example.ai.GeneratedBuildPlan
import com.example.ai.GrassSshAiRepository
import com.example.data.local.SettingsRepository
import com.example.data.local.TerminalSettings
import com.example.data.model.AuthType
import com.example.data.model.KnownHostEntity
import com.example.data.model.SnippetEntity
import com.example.data.model.SshHostEntity
import com.example.data.model.SshKeyEntity
import com.example.data.repository.SshRepository
import com.example.ssh.KeyAlgorithmOption
import com.example.ssh.LiveSshSession
import com.example.ssh.SshInputSanitizer
import com.example.ssh.SshKeyManager
import com.example.ssh.SshSessionManager
import com.example.ssh.TcpProbeResult
import com.example.ui.theme.TerminalThemePreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val AI_VM_TAG = "GrassSshAiViewModel"

class MainViewModel(
  private val repository: SshRepository,
  private val settingsRepository: SettingsRepository,
  private val sessionManager: SshSessionManager,
  private val aiRepository: GrassSshAiRepository,
) : ViewModel() {

  val hosts: StateFlow<List<SshHostEntity>> = repository.hosts.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList(),
  )

  val keys: StateFlow<List<SshKeyEntity>> = repository.keys.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList(),
  )

  val knownHosts: StateFlow<List<KnownHostEntity>> = repository.knownHosts.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList(),
  )

  val snippets: StateFlow<List<SnippetEntity>> = repository.snippets.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList(),
  )

  val settings: StateFlow<TerminalSettings> = settingsRepository.settings.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = TerminalSettings(),
  )

  val sessions: StateFlow<List<LiveSshSession>> = sessionManager.sessions
  val activeSessionId: StateFlow<String?> = sessionManager.activeSessionId

  private val _probeResults = MutableStateFlow<Map<Long, TcpProbeResult>>(emptyMap())
  val probeResults: StateFlow<Map<Long, TcpProbeResult>> = _probeResults.asStateFlow()

  private val _probingHostIds = MutableStateFlow<Set<Long>>(emptySet())
  val probingHostIds: StateFlow<Set<Long>> = _probingHostIds.asStateFlow()

  private val _isGeneratingKey = MutableStateFlow(false)
  val isGeneratingKey: StateFlow<Boolean> = _isGeneratingKey.asStateFlow()

  private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
  val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

  private val _aiUiState = MutableStateFlow<AiSystemUiState>(AiSystemUiState.Idle)
  val aiUiState: StateFlow<AiSystemUiState> = _aiUiState.asStateFlow()

  private val _buildPlanState = MutableStateFlow<BuildPlanUiState>(BuildPlanUiState.Idle)
  val buildPlanState: StateFlow<BuildPlanUiState> = _buildPlanState.asStateFlow()

  private val _consoleFixState = MutableStateFlow<ConsoleFixUiState>(ConsoleFixUiState.Idle)
  val consoleFixState: StateFlow<ConsoleFixUiState> = _consoleFixState.asStateFlow()

  private val _includeAiTerminalContext = MutableStateFlow(true)
  val includeAiTerminalContext: StateFlow<Boolean> = _includeAiTerminalContext.asStateFlow()

  private var aiStreamJob: Job? = null
  private var buildPlanJob: Job? = null
  private var consoleFixJob: Job? = null

  private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

  fun notifyMessage(message: String) {
    _uiMessages.tryEmit(message)
  }

  fun setIncludeAiTerminalContext(enabled: Boolean) {
    _includeAiTerminalContext.value = enabled
  }

  fun sendAiPrompt(prompt: String) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isEmpty()) return

    aiStreamJob?.cancel()
    val priorHistory = _aiMessages.value
    val userMessage = AiChatMessage(
      id = UUID.randomUUID().toString(),
      isUser = true,
      text = cleanPrompt,
    )
    val assistantId = UUID.randomUUID().toString()
    val assistantPlaceholder = AiChatMessage(
      id = assistantId,
      isUser = false,
      text = "",
      isStreaming = true,
    )
    _aiMessages.value = priorHistory + userMessage + assistantPlaceholder
    _aiUiState.value = AiSystemUiState.Loading

    val activeSess = sessionManager.getSession(activeSessionId.value)
    val terminalContext = if (_includeAiTerminalContext.value && activeSess != null) {
      val plainOutput = activeSess.copyPlainOutput()
      if (plainOutput.isNotBlank()) {
        "Session: ${activeSess.hostConfig.username}@${activeSess.hostConfig.hostname}:${activeSess.hostConfig.port} (${activeSess.status.value.label})\n$plainOutput"
      } else {
        null
      }
    } else {
      null
    }
    val customInstructions = settings.value.customAiInstructions

    aiStreamJob = viewModelScope.launch {
      var latestText = ""
      try {
        aiRepository.streamResponse(
          history = priorHistory,
          userPrompt = cleanPrompt,
          terminalContext = terminalContext,
          customInstructions = customInstructions,
        ).collect { partial ->
          latestText = partial
          _aiUiState.value = AiSystemUiState.Streaming(partial)
          _aiMessages.update { list ->
            list.map { item ->
              if (item.id == assistantId) item.copy(text = partial, isStreaming = true) else item
            }
          }
        }
        val commands = GrassSshAiRepository.extractShellCommands(latestText)
        _aiMessages.update { list ->
          list.map { item ->
            if (item.id == assistantId) {
              item.copy(
                text = latestText,
                extractedCommands = commands,
                isStreaming = false,
              )
            } else {
              item
            }
          }
        }
        _aiUiState.value = AiSystemUiState.Success(latestText)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(AI_VM_TAG, "GrassSSH AI System failed", e)
        _aiMessages.update { list ->
          list.filterNot { it.id == assistantId && it.text.isBlank() }
            .map { item -> if (item.id == assistantId) item.copy(isStreaming = false) else item }
        }
        _aiUiState.value = AiSystemUiState.Error(
          e.localizedMessage ?: "GrassSSH AI System encountered an error.",
        )
      }
    }
  }

  fun clearAiChat() {
    aiStreamJob?.cancel()
    _aiMessages.value = emptyList()
    _aiUiState.value = AiSystemUiState.Idle
  }

  fun generateBuildPlan(prompt: String) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isEmpty()) return

    buildPlanJob?.cancel()
    _buildPlanState.value = BuildPlanUiState.Generating
    val activeSess = sessionManager.getSession(activeSessionId.value)
    val terminalContext = if (_includeAiTerminalContext.value) {
      activeSess?.copyPlainOutput()?.takeIf { it.isNotBlank() }
    } else {
      null
    }
    val customInstructions = settings.value.customAiInstructions

    buildPlanJob = viewModelScope.launch {
      try {
        val plan = aiRepository.generateBuildPlan(
          prompt = cleanPrompt,
          customInstructions = customInstructions,
          terminalContext = terminalContext,
          sshUsername = activeSess?.hostConfig?.username,
        )
        _buildPlanState.value = BuildPlanUiState.Ready(plan)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(AI_VM_TAG, "GrassSSH AI System build plan failed", e)
        _buildPlanState.value = BuildPlanUiState.Error(
          e.localizedMessage ?: "Failed to generate build plan.",
        )
      }
    }
  }

  fun clearBuildPlan() {
    buildPlanJob?.cancel()
    _buildPlanState.value = BuildPlanUiState.Idle
  }

  fun diagnoseConsoleError(customHint: String? = null) {
    val activeSess = sessionManager.getSession(activeSessionId.value)
    val plainOutput = activeSess?.copyPlainOutput()?.takeIf { it.isNotBlank() }
    if (plainOutput == null) {
      _consoleFixState.value = ConsoleFixUiState.Error("No console output found in the active session yet.")
      return
    }
    val fullContext = "Session: ${activeSess.hostConfig.username}@${activeSess.hostConfig.hostname}:${activeSess.hostConfig.port} (${activeSess.status.value.label})\n$plainOutput"
    consoleFixJob?.cancel()
    _consoleFixState.value = ConsoleFixUiState.Diagnosing
    val customInstructions = settings.value.customAiInstructions

    consoleFixJob = viewModelScope.launch {
      try {
        val fixPlan = aiRepository.diagnoseAndFixConsoleError(
          terminalContext = fullContext,
          customHint = customHint,
          customInstructions = customInstructions,
          sshUsername = activeSess.hostConfig.username,
        )
        _consoleFixState.value = ConsoleFixUiState.Ready(fixPlan)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(AI_VM_TAG, "GrassSSH AI console fix failed", e)
        _consoleFixState.value = ConsoleFixUiState.Error(
          e.localizedMessage ?: "Failed to diagnose console error.",
        )
      }
    }
  }

  fun clearConsoleFix() {
    consoleFixJob?.cancel()
    _consoleFixState.value = ConsoleFixUiState.Idle
  }

  fun executeFullBuildPlan(plan: GeneratedBuildPlan, onNavigateToTerminal: () -> Unit) {
    val active = sessionManager.getSession(activeSessionId.value)
    if (active == null) {
      notifyMessage("Connect to an SSH session first to build on your VPS")
      return
    }
    onNavigateToTerminal()
    val atomicScript = ConsoleErrorDetector.buildAtomicExecutionCommand(
      title = plan.planTitle,
      steps = plan.steps,
      verificationCommand = plan.verificationCommand,
    )
    active.sendLine(atomicScript)
    notifyMessage("Running '${plan.planTitle}' safely (${plan.steps.size} steps)")
  }

  fun executeFixPlan(plan: ConsoleFixPlan, onNavigateToTerminal: () -> Unit) {
    val active = sessionManager.getSession(activeSessionId.value)
    if (active == null) {
      notifyMessage("Connect to an SSH session first to run this fix")
      return
    }
    onNavigateToTerminal()
    val atomicScript = ConsoleErrorDetector.buildAtomicExecutionCommand(
      title = plan.errorTitle,
      steps = plan.fixSteps,
      verificationCommand = plan.verificationCommand,
    )
    active.sendLine(atomicScript)
    notifyMessage("Applying fix '${plan.errorTitle}' (${plan.fixSteps.size} steps)")
  }

  fun saveBuildPlanAsSnippet(plan: GeneratedBuildPlan) {
    val combinedCommand = plan.steps.joinToString("\n") { it.command }
    viewModelScope.launch {
      repository.saveSnippet(
        SnippetEntity(
          title = plan.planTitle,
          command = combinedCommand,
          category = "System",
        )
      )
      notifyMessage("Saved '${plan.planTitle}' to Snippets")
    }
  }

  fun setCustomAiInstructions(instructions: String) {
    viewModelScope.launch {
      settingsRepository.setCustomAiInstructions(instructions)
      notifyMessage("Updated GrassSSH AI System instructions")
    }
  }

  fun runCommandInActiveSession(command: String, onNavigateToTerminal: () -> Unit) {
    val active = sessionManager.getSession(activeSessionId.value)
    if (active == null) {
      notifyMessage("Connect to an SSH session first to run commands")
      return
    }
    active.sendLine(command)
    onNavigateToTerminal()
  }

  fun quickConnect(rawInput: String, onSessionOpened: () -> Unit) {
    val parsed = parseQuickConnectTarget(rawInput, settings.value.defaultUsername)
    if (parsed == null) {
      notifyMessage("Enter host or user@host:port (e.g. root@192.168.1.10:22)")
      return
    }
    val (user, host, port) = parsed
    val transientHost = SshHostEntity(
      id = 0,
      alias = "$user@$host",
      hostname = host,
      port = port,
      username = user,
      authType = AuthType.ASK_ON_CONNECT,
    )
    sessionManager.openSession(transientHost, sshKey = null)
    onSessionOpened()
  }

  fun connectHost(host: SshHostEntity, onSessionOpened: () -> Unit) {
    viewModelScope.launch {
      val cleanHost = host.copy(
        hostname = SshInputSanitizer.sanitizeHostname(host.hostname),
        username = SshInputSanitizer.sanitizeUsername(host.username),
      )
      if (cleanHost != host && cleanHost.id > 0L) {
        repository.saveHost(cleanHost)
      }
      val key = cleanHost.keyId?.let { repository.getKeyById(it) }
      sessionManager.openSession(cleanHost, key)
      onSessionOpened()
    }
  }

  fun selectSession(sessionId: String) = sessionManager.selectSession(sessionId)

  fun closeSession(sessionId: String) = sessionManager.closeSession(sessionId)

  fun saveHost(host: SshHostEntity) {
    viewModelScope.launch {
      val sanitized = host.copy(
        hostname = SshInputSanitizer.sanitizeHostname(host.hostname),
        username = SshInputSanitizer.sanitizeUsername(host.username),
      )
      repository.saveHost(sanitized)
      notifyMessage("Saved host '${sanitized.alias.ifBlank { sanitized.hostname }}'")
    }
  }

  fun deleteHost(host: SshHostEntity) {
    viewModelScope.launch {
      repository.deleteHost(host.id)
      notifyMessage("Deleted '${host.alias.ifBlank { host.hostname }}'")
    }
  }

  fun probeHost(host: SshHostEntity) {
    if (_probingHostIds.value.contains(host.id)) return
    viewModelScope.launch {
      _probingHostIds.update { it + host.id }
      val result = sessionManager.probeTcpReachability(
        SshInputSanitizer.sanitizeHostname(host.hostname),
        host.port,
      )
      _probeResults.update { it + (host.id to result) }
      _probingHostIds.update { it - host.id }
    }
  }

  fun generateKey(name: String, option: KeyAlgorithmOption, passphrase: String) {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) {
      notifyMessage("Key name is required")
      return
    }
    viewModelScope.launch {
      _isGeneratingKey.value = true
      val result = withContext(Dispatchers.Default) {
        runCatching { SshKeyManager.generateKeyPair(cleanName, option, passphrase) }
      }
      _isGeneratingKey.value = false
      result.onSuccess { entity ->
        repository.saveKey(entity)
        notifyMessage("Generated ${option.label} key '$cleanName'")
      }.onFailure { err ->
        notifyMessage("Key generation failed: ${err.message ?: "Unknown error"}")
      }
    }
  }

  fun importKey(name: String, pemText: String, passphrase: String) {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) {
      notifyMessage("Key name is required")
      return
    }
    viewModelScope.launch {
      val result = withContext(Dispatchers.Default) {
        SshKeyManager.importPrivateKey(cleanName, pemText, passphrase)
      }
      result.onSuccess { entity ->
        repository.saveKey(entity)
        notifyMessage("Imported key '$cleanName'")
      }.onFailure { err ->
        notifyMessage(err.message ?: "Failed to import SSH key")
      }
    }
  }

  fun deleteKey(key: SshKeyEntity) {
    viewModelScope.launch {
      repository.deleteKey(key.id)
      notifyMessage("Deleted key '${key.name}'")
    }
  }

  fun saveSnippet(snippet: SnippetEntity) {
    viewModelScope.launch {
      repository.saveSnippet(snippet)
      notifyMessage("Saved snippet '${snippet.title}'")
    }
  }

  fun deleteSnippet(snippet: SnippetEntity) {
    viewModelScope.launch {
      repository.deleteSnippet(snippet.id)
      notifyMessage("Removed snippet '${snippet.title}'")
    }
  }

  fun runSnippetInActiveSession(snippet: SnippetEntity, onNavigateToTerminal: () -> Unit) {
    val active = sessionManager.getSession(activeSessionId.value)
    if (active == null) {
      notifyMessage("Open or connect an SSH session first to run snippets")
      return
    }
    active.sendLine(snippet.command)
    onNavigateToTerminal()
  }

  fun forgetKnownHost(hostKeyId: String) {
    viewModelScope.launch {
      repository.forgetKnownHost(hostKeyId)
      notifyMessage("Removed $hostKeyId from known hosts")
    }
  }

  fun setThemePreset(preset: TerminalThemePreset) {
    viewModelScope.launch { settingsRepository.setThemePreset(preset) }
  }

  fun setFontSizeSp(sizeSp: Int) {
    viewModelScope.launch { settingsRepository.setFontSizeSp(sizeSp) }
  }

  fun setKeepScreenOn(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setKeepScreenOn(enabled) }
  }

  fun setVibrateOnKey(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setVibrateOnKey(enabled) }
  }

  fun setDefaultUsername(username: String) {
    viewModelScope.launch { settingsRepository.setDefaultUsername(username) }
  }

  companion object {
    fun parseQuickConnectTarget(raw: String, fallbackUser: String): Triple<String, String, Int>? {
      val trimmed = raw.trim().removePrefix("ssh ").trim()
      if (trimmed.isEmpty() || trimmed.contains(' ')) return null
      val userAndRest = trimmed.split('@', limit = 2)
      val user: String
      val hostPortPart: String
      if (userAndRest.size == 2) {
        user = userAndRest[0].trim()
        hostPortPart = userAndRest[1].trim()
      } else {
        user = fallbackUser.ifBlank { "root" }
        hostPortPart = userAndRest[0].trim()
      }
      if (user.isEmpty() || hostPortPart.isEmpty()) return null

      // Support IPv4/hostname:port and bracketed [IPv6]:port
      val host: String
      val port: Int
      if (hostPortPart.startsWith("[")) {
        val closeIdx = hostPortPart.indexOf(']')
        if (closeIdx <= 1) return null
        host = hostPortPart.substring(1, closeIdx)
        val remainder = hostPortPart.substring(closeIdx + 1)
        port = if (remainder.startsWith(":")) {
          remainder.drop(1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        } else 22
      } else {
        val colonCount = hostPortPart.count { it == ':' }
        if (colonCount == 1) {
          val parts = hostPortPart.split(':', limit = 2)
          host = parts[0].trim()
          port = parts[1].trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        } else {
          host = hostPortPart
          port = 22
        }
      }
      val cleanUser = SshInputSanitizer.sanitizeUsername(user)
      val cleanHost = SshInputSanitizer.sanitizeHostname(host)
      if (cleanUser.isEmpty() || cleanHost.isEmpty()) return null
      return Triple(cleanUser, cleanHost, port)
    }

    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return MainViewModel(
            repository = container.sshRepository,
            settingsRepository = container.settingsRepository,
            sessionManager = container.sessionManager,
            aiRepository = container.aiRepository,
          ) as T
        }
      }
  }
}

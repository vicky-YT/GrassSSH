package com.example.ssh

import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.example.data.model.AuthType
import com.example.data.model.KnownHostEntity
import com.example.data.model.SshHostEntity
import com.example.data.model.SshKeyEntity
import com.example.data.repository.SshRepository
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SshInputSanitizer {
  fun sanitizeHostname(raw: String): String {
    return raw
      .replace('\u00A0', ' ')
      .replace("\u200B", "")
      .replace("\uFEFF", "")
      .trim()
      .removePrefix("ssh://")
      .removePrefix("sftp://")
      .trimEnd('/')
      .trimEnd('.')
      .trim()
  }

  fun sanitizeUsername(raw: String): String {
    return raw
      .replace('\u00A0', ' ')
      .replace("\u200B", "")
      .replace("\uFEFF", "")
      .trim()
  }
}

enum class SessionStatus(val label: String) {
  CONNECTING("Connecting"),
  AWAITING_HOST_KEY("Verify Host"),
  AWAITING_CREDENTIAL("Auth Prompt"),
  CONNECTED("Connected"),
  DISCONNECTED("Disconnected"),
  ERROR("Error"),
}

data class HostKeyPrompt(
  val hostname: String,
  val port: Int,
  val keyType: String,
  val fingerprintSha256: String,
  val base64Key: String,
  val keyChangedWarning: Boolean,
  val deferred: CompletableDeferred<Boolean>,
)

data class CredentialPrompt(
  val title: String,
  val message: String,
  val isPassphrase: Boolean,
  val deferred: CompletableDeferred<String?>,
)

data class SftpEntry(
  val filename: String,
  val fullPath: String,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val permissions: String,
)

data class SftpUiState(
  val currentPath: String = ".",
  val entries: List<SftpEntry> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
  val previewFileName: String? = null,
  val previewContent: String? = null,
)

data class TcpProbeResult(
  val reachable: Boolean,
  val latencyMs: Long? = null,
  val error: String? = null,
)

class LiveSshSession(
  val sessionId: String,
  rawHostConfig: SshHostEntity,
  private val sshKey: SshKeyEntity?,
  private val repository: SshRepository,
  private val onConnectedCallback: suspend (Long) -> Unit,
) {
  val hostConfig: SshHostEntity = rawHostConfig.copy(
    hostname = SshInputSanitizer.sanitizeHostname(rawHostConfig.hostname),
    username = SshInputSanitizer.sanitizeUsername(rawHostConfig.username),
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _status = MutableStateFlow(SessionStatus.CONNECTING)
  val status: StateFlow<SessionStatus> = _status.asStateFlow()

  private val _terminalTitle = MutableStateFlow(
    hostConfig.alias.ifBlank { "${hostConfig.username}@${hostConfig.hostname}" }
  )
  val terminalTitle: StateFlow<String> = _terminalTitle.asStateFlow()

  private val _lines = MutableStateFlow<List<AnnotatedString>>(emptyList())
  val lines: StateFlow<List<AnnotatedString>> = _lines.asStateFlow()

  private val _activeLine = MutableStateFlow(AnnotatedString(""))
  val activeLine: StateFlow<AnnotatedString> = _activeLine.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _pendingHostKeyPrompt = MutableStateFlow<HostKeyPrompt?>(null)
  val pendingHostKeyPrompt: StateFlow<HostKeyPrompt?> = _pendingHostKeyPrompt.asStateFlow()

  private val _pendingCredentialPrompt = MutableStateFlow<CredentialPrompt?>(null)
  val pendingCredentialPrompt: StateFlow<CredentialPrompt?> = _pendingCredentialPrompt.asStateFlow()

  private val _sftpState = MutableStateFlow(SftpUiState())
  val sftpState: StateFlow<SftpUiState> = _sftpState.asStateFlow()

  private val parser = AnsiTerminalParser(
    onTitleChanged = { newTitle -> _terminalTitle.value = newTitle },
  )

  private var jschSession: Session? = null
  private var shellChannel: ChannelShell? = null
  private var shellOut: OutputStream? = null
  private var connectionJob: Job? = null
  private var cols = 80
  private var rows = 24

  fun start() {
    connectionJob?.cancel()
    connectionJob = scope.launch {
      connectInternal()
    }
  }

  private suspend fun connectInternal() {
    _status.value = SessionStatus.CONNECTING
    _errorMessage.value = null
    appendSystemLine(
      "[GrassSSH] Connecting to ${hostConfig.username}@${hostConfig.hostname}:${hostConfig.port}...",
      Color(0xFF34D399),
    )

    if (hostConfig.hostname.isBlank()) {
      val msg = "Hostname or IP address is empty."
      _errorMessage.value = msg
      _status.value = SessionStatus.ERROR
      appendSystemLine("[GrassSSH Error] $msg", Color(0xFFFB7185))
      return
    }

    try {
      val jsch = JSch()

      // Load private key if configured
      if (hostConfig.authType == AuthType.PRIVATE_KEY && sshKey != null) {
        val privBytes = sshKey.privateKeyPem.toByteArray(Charsets.UTF_8)
        val pubBytes = if (sshKey.publicKeyOpenSsh.isNotBlank()) {
          sshKey.publicKeyOpenSsh.toByteArray(Charsets.UTF_8)
        } else {
          null
        }
        jsch.addIdentity(sshKey.name, privBytes, pubBytes, null)
        appendSystemLine(
          "[GrassSSH] Loaded SSH key '${sshKey.name}' (${sshKey.algorithm}).",
          Color(0xFF86EFAC),
        )
      }

      // Track the key offered by the server for TOFU verification
      var offeredKeyBytes: ByteArray? = null
      var offeredKeyType = "ssh-rsa"
      var keyChangedWarning = false

      jsch.hostKeyRepository = object : HostKeyRepository {
        override fun check(host: String?, key: ByteArray?): Int {
          if (key == null) return HostKeyRepository.NOT_INCLUDED
          offeredKeyBytes = key
          val hostKey = runCatching { HostKey(host, key) }.getOrNull()
          offeredKeyType = hostKey?.type ?: "ssh-key"
          val encoded = Base64.encodeToString(key, Base64.NO_WRAP)
          val id = "${hostConfig.hostname}:${hostConfig.port}"
          val known = runBlocking { repository.getKnownHost(id) }
          return when {
            known == null -> {
              keyChangedWarning = false
              HostKeyRepository.NOT_INCLUDED
            }
            known.base64Key == encoded -> HostKeyRepository.OK
            else -> {
              keyChangedWarning = true
              HostKeyRepository.CHANGED
            }
          }
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID(): String = "grassssh-room-known-hosts"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
      }

      val session = jsch.getSession(hostConfig.username, hostConfig.hostname, hostConfig.port)
      jschSession = session

      if (hostConfig.authType == AuthType.PASSWORD && hostConfig.password.isNotEmpty()) {
        session.setPassword(hostConfig.password)
      }

      session.setConfig("StrictHostKeyChecking", "ask")
      session.setConfig(
        "PreferredAuthentications",
        when (hostConfig.authType) {
          AuthType.PRIVATE_KEY -> "publickey,keyboard-interactive,password"
          else -> "password,keyboard-interactive,publickey"
        },
      )

      var enteredPassword: String? = null
      var enteredPassphrase: String? = null
      var triedSavedPasswordInKbi = false

      session.userInfo = object : UserInfo, UIKeyboardInteractive {
        override fun getPassphrase(): String? = enteredPassphrase
        override fun getPassword(): String? = enteredPassword

        override fun promptPassword(message: String?): Boolean {
          _status.value = SessionStatus.AWAITING_CREDENTIAL
          val deferred = CompletableDeferred<String?>()
          _pendingCredentialPrompt.value = CredentialPrompt(
            title = "SSH Password Required",
            message = message ?: "Enter password for ${hostConfig.username}@${hostConfig.hostname}",
            isPassphrase = false,
            deferred = deferred,
          )
          val result = runBlocking { deferred.await() }
          _pendingCredentialPrompt.value = null
          _status.value = SessionStatus.CONNECTING
          enteredPassword = result
          return result != null
        }

        override fun promptPassphrase(message: String?): Boolean {
          _status.value = SessionStatus.AWAITING_CREDENTIAL
          val deferred = CompletableDeferred<String?>()
          _pendingCredentialPrompt.value = CredentialPrompt(
            title = "Key Passphrase Required",
            message = message ?: "Unlock private key '${sshKey?.name ?: "SSH Key"}'",
            isPassphrase = true,
            deferred = deferred,
          )
          val result = runBlocking { deferred.await() }
          _pendingCredentialPrompt.value = null
          _status.value = SessionStatus.CONNECTING
          enteredPassphrase = result
          return result != null
        }

        override fun promptKeyboardInteractive(
          destination: String?,
          name: String?,
          instruction: String?,
          prompt: Array<out String>?,
          echo: BooleanArray?,
        ): Array<String>? {
          if (prompt == null || prompt.isEmpty()) return emptyArray()
          if (
            !triedSavedPasswordInKbi &&
            prompt.size == 1 &&
            hostConfig.authType == AuthType.PASSWORD &&
            hostConfig.password.isNotEmpty()
          ) {
            triedSavedPasswordInKbi = true
            return arrayOf(hostConfig.password)
          }
          val responses = ArrayList<String>(prompt.size)
          for (i in prompt.indices) {
            _status.value = SessionStatus.AWAITING_CREDENTIAL
            val deferred = CompletableDeferred<String?>()
            val promptLabel = prompt[i].ifBlank { "Password" }
            val detail = listOfNotNull(
              name?.takeIf { it.isNotBlank() },
              instruction?.takeIf { it.isNotBlank() },
              promptLabel,
            ).joinToString(" — ")
            _pendingCredentialPrompt.value = CredentialPrompt(
              title = "Authentication Prompt",
              message = detail,
              isPassphrase = false,
              deferred = deferred,
            )
            val result = runBlocking { deferred.await() }
            _pendingCredentialPrompt.value = null
            _status.value = SessionStatus.CONNECTING
            if (result == null) return null
            responses.add(result)
          }
          return responses.toTypedArray()
        }

        override fun promptYesNo(message: String?): Boolean {
          val bytes = offeredKeyBytes ?: return false
          val base64Key = Base64.encodeToString(bytes, Base64.NO_WRAP)
          val fingerprint = SshKeyManager.computeSha256FingerprintFromBlob(bytes)
          _status.value = SessionStatus.AWAITING_HOST_KEY
          val deferred = CompletableDeferred<Boolean>()
          _pendingHostKeyPrompt.value = HostKeyPrompt(
            hostname = hostConfig.hostname,
            port = hostConfig.port,
            keyType = offeredKeyType,
            fingerprintSha256 = fingerprint,
            base64Key = base64Key,
            keyChangedWarning = keyChangedWarning,
            deferred = deferred,
          )
          val accepted = runBlocking { deferred.await() }
          _pendingHostKeyPrompt.value = null
          _status.value = SessionStatus.CONNECTING
          if (accepted) {
            runBlocking {
              repository.trustKnownHost(
                KnownHostEntity(
                  hostKeyId = "${hostConfig.hostname}:${hostConfig.port}",
                  hostname = hostConfig.hostname,
                  port = hostConfig.port,
                  keyType = offeredKeyType,
                  fingerprintSha256 = fingerprint,
                  base64Key = base64Key,
                )
              )
            }
          }
          return accepted
        }

        override fun showMessage(message: String?) {
          if (!message.isNullOrBlank()) {
            appendSystemLine("[Server] $message", Color(0xFFFBBF24))
          }
        }
      }

      session.serverAliveInterval = 15_000
      session.serverAliveCountMax = 3
      session.connect(15_000)

      val channel = session.openChannel("shell") as ChannelShell
      shellChannel = channel
      channel.setPtyType("xterm-256color")
      channel.setPtySize(cols, rows, 0, 0)

      val inputStream = channel.inputStream
      shellOut = channel.outputStream
      channel.connect(10_000)

      _status.value = SessionStatus.CONNECTED
      appendSystemLine(
        "[GrassSSH] Connected via SSH-2.0 (${session.serverVersion ?: "OpenSSH"}).",
        Color(0xFF34D399),
      )

      if (hostConfig.id > 0L) {
        onConnectedCallback(hostConfig.id)
      }

      if (hostConfig.startupCommand.isNotBlank()) {
        sendLine(hostConfig.startupCommand)
      }

      // Read terminal stream until EOF or channel closed
      val buffer = ByteArray(4096)
      while (channel.isConnected && !channel.isClosed) {
        val bytesRead = inputStream.read(buffer)
        if (bytesRead == -1) break
        if (bytesRead > 0) {
          val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
          parser.feed(chunk)
          publishTerminalSnapshot()
        }
      }

      val exitStatus = channel.exitStatus
      if (_status.value != SessionStatus.DISCONNECTED) {
        _status.value = SessionStatus.DISCONNECTED
        appendSystemLine(
          "[GrassSSH] Session closed (exit code $exitStatus).",
          Color(0xFFFBBF24),
        )
      }
    } catch (e: Exception) {
      val root = e.cause ?: e
      val msg = when {
        root is UnknownHostException || e is UnknownHostException ->
          "Cannot resolve '${hostConfig.hostname}'. Check the IP/domain for typos or internet access."
        root is ConnectException || e is ConnectException ->
          "Connection refused by ${hostConfig.hostname}:${hostConfig.port}. Verify the SSH port and firewall."
        root is SocketTimeoutException || e is SocketTimeoutException ->
          "Timed out connecting to ${hostConfig.hostname}:${hostConfig.port}. Check firewall / security group rules."
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
      }
      _errorMessage.value = msg
      _status.value = SessionStatus.ERROR
      appendSystemLine("[GrassSSH Error] $msg", Color(0xFFFB7185))
    }
  }

  fun respondHostKey(accept: Boolean) {
    _pendingHostKeyPrompt.value?.deferred?.complete(accept)
  }

  fun respondCredential(secret: String?) {
    _pendingCredentialPrompt.value?.deferred?.complete(secret)
  }

  fun sendLine(command: String) {
    sendBytes((command + "\r").toByteArray(Charsets.UTF_8))
  }

  fun sendText(text: String) {
    sendBytes(text.toByteArray(Charsets.UTF_8))
  }

  fun sendBytes(bytes: ByteArray) {
    scope.launch {
      runCatching {
        shellOut?.apply {
          write(bytes)
          flush()
        }
      }
    }
  }

  fun sendCtrlChar(letter: Char) {
    val upper = letter.uppercaseChar()
    val code = (upper.code - 'A'.code + 1).coerceIn(1, 26)
    sendBytes(byteArrayOf(code.toByte()))
  }

  fun resizePty(newCols: Int, newRows: Int) {
    val safeCols = newCols.coerceIn(40, 220)
    val safeRows = newRows.coerceIn(12, 80)
    if (safeCols == cols && safeRows == rows) return
    cols = safeCols
    rows = safeRows
    scope.launch {
      runCatching {
        shellChannel?.setPtySize(cols, rows, 0, 0)
      }
    }
  }

  fun clearTerminal() {
    parser.clear()
    publishTerminalSnapshot()
  }

  fun copyPlainOutput(): String = parser.snapshotAllPlainText()

  fun disconnect() {
    _status.value = SessionStatus.DISCONNECTED
    _pendingHostKeyPrompt.value?.deferred?.complete(false)
    _pendingCredentialPrompt.value?.deferred?.complete(null)
    scope.launch {
      runCatching { shellChannel?.disconnect() }
      runCatching { jschSession?.disconnect() }
      shellChannel = null
      jschSession = null
      shellOut = null
    }
  }

  fun reconnect() {
    disconnect()
    start()
  }

  // SFTP operations on the live SSH session
  fun loadSftpDirectory(path: String = _sftpState.value.currentPath) {
    val session = jschSession ?: run {
      _sftpState.value = _sftpState.value.copy(error = "SSH session is not connected.")
      return
    }
    if (!session.isConnected) {
      _sftpState.value = _sftpState.value.copy(error = "SSH session is disconnected.")
      return
    }
    scope.launch {
      _sftpState.value = _sftpState.value.copy(isLoading = true, error = null)
      var sftp: ChannelSftp? = null
      try {
        sftp = session.openChannel("sftp") as ChannelSftp
        sftp.connect(8000)
        val targetPath = if (path == ".") sftp.pwd() else path
        @Suppress("UNCHECKED_CAST")
        val rawEntries = sftp.ls(targetPath) as VectorLikeLs
        val parsed = rawEntries
          .filter { it.filename != "." && it.filename != ".." }
          .map { item ->
            val attrs = item.attrs
            val full = if (targetPath == "/") "/${item.filename}" else "$targetPath/${item.filename}"
            SftpEntry(
              filename = item.filename,
              fullPath = full,
              isDirectory = attrs.isDir,
              sizeBytes = attrs.size,
              permissions = attrs.permissionsString ?: "",
            )
          }
          .sortedWith(compareByDescending<SftpEntry> { it.isDirectory }.thenBy { it.filename.lowercase() })

        _sftpState.value = _sftpState.value.copy(
          currentPath = targetPath,
          entries = parsed,
          isLoading = false,
          error = null,
        )
      } catch (e: Exception) {
        _sftpState.value = _sftpState.value.copy(
          isLoading = false,
          error = e.message ?: "Failed to list SFTP directory",
        )
      } finally {
        runCatching { sftp?.disconnect() }
      }
    }
  }

  fun createSftpFolder(folderName: String) {
    val clean = folderName.trim()
    if (clean.isEmpty()) return
    val session = jschSession ?: return
    val base = _sftpState.value.currentPath
    val target = if (base == "/") "/$clean" else "$base/$clean"
    scope.launch {
      var sftp: ChannelSftp? = null
      try {
        sftp = session.openChannel("sftp") as ChannelSftp
        sftp.connect(8000)
        sftp.mkdir(target)
        loadSftpDirectory(base)
      } catch (e: Exception) {
        _sftpState.value = _sftpState.value.copy(error = e.message ?: "Could not create folder")
      } finally {
        runCatching { sftp?.disconnect() }
      }
    }
  }

  fun deleteSftpEntry(entry: SftpEntry) {
    val session = jschSession ?: return
    val base = _sftpState.value.currentPath
    scope.launch {
      var sftp: ChannelSftp? = null
      try {
        sftp = session.openChannel("sftp") as ChannelSftp
        sftp.connect(8000)
        if (entry.isDirectory) sftp.rmdir(entry.fullPath) else sftp.rm(entry.fullPath)
        loadSftpDirectory(base)
      } catch (e: Exception) {
        _sftpState.value = _sftpState.value.copy(error = e.message ?: "Delete failed")
      } finally {
        runCatching { sftp?.disconnect() }
      }
    }
  }

  fun previewSftpFile(entry: SftpEntry) {
    if (entry.isDirectory) {
      loadSftpDirectory(entry.fullPath)
      return
    }
    val session = jschSession ?: return
    scope.launch {
      _sftpState.value = _sftpState.value.copy(isLoading = true, error = null)
      var sftp: ChannelSftp? = null
      try {
        sftp = session.openChannel("sftp") as ChannelSftp
        sftp.connect(8000)
        val input = sftp.get(entry.fullPath)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        val maxBytes = 64 * 1024
        while (total < maxBytes) {
          val read = input.read(buf, 0, minOf(buf.size, maxBytes - total))
          if (read <= 0) break
          out.write(buf, 0, read)
          total += read
        }
        val text = out.toString(Charsets.UTF_8.name()) +
          if (entry.sizeBytes > maxBytes) "\n\n... [Preview truncated at 64 KB]" else ""
        _sftpState.value = _sftpState.value.copy(
          isLoading = false,
          previewFileName = entry.filename,
          previewContent = text,
        )
      } catch (e: Exception) {
        _sftpState.value = _sftpState.value.copy(
          isLoading = false,
          error = e.message ?: "Failed to read file",
        )
      } finally {
        runCatching { sftp?.disconnect() }
      }
    }
  }

  fun dismissSftpPreview() {
    _sftpState.value = _sftpState.value.copy(previewFileName = null, previewContent = null)
  }

  private fun appendSystemLine(message: String, color: Color) {
    parser.appendSystemMessage(message, color)
    publishTerminalSnapshot()
  }

  private fun publishTerminalSnapshot() {
    _lines.value = parser.snapshotHistory()
    _activeLine.value = parser.snapshotActiveLine()
  }
}

private typealias VectorLikeLs = java.util.Vector<ChannelSftp.LsEntry>

class SshSessionManager(
  private val repository: SshRepository,
) {
  private val sessionMap = ConcurrentHashMap<String, LiveSshSession>()

  private val _sessions = MutableStateFlow<List<LiveSshSession>>(emptyList())
  val sessions: StateFlow<List<LiveSshSession>> = _sessions.asStateFlow()

  private val _activeSessionId = MutableStateFlow<String?>(null)
  val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

  fun openSession(host: SshHostEntity, sshKey: SshKeyEntity?): String {
    val id = UUID.randomUUID().toString()
    val live = LiveSshSession(
      sessionId = id,
      rawHostConfig = host,
      sshKey = sshKey,
      repository = repository,
      onConnectedCallback = { hostId ->
        repository.markHostConnected(hostId)
      },
    )
    sessionMap[id] = live
    _sessions.value = sessionMap.values.toList()
    _activeSessionId.value = id
    live.start()
    return id
  }

  fun selectSession(sessionId: String) {
    if (sessionMap.containsKey(sessionId)) {
      _activeSessionId.value = sessionId
    }
  }

  fun closeSession(sessionId: String) {
    sessionMap.remove(sessionId)?.disconnect()
    val remaining = sessionMap.values.toList()
    _sessions.value = remaining
    if (_activeSessionId.value == sessionId) {
      _activeSessionId.value = remaining.lastOrNull()?.sessionId
    }
  }

  fun getSession(sessionId: String?): LiveSshSession? = sessionId?.let { sessionMap[it] }

  suspend fun probeTcpReachability(hostname: String, port: Int): TcpProbeResult =
    withContext(Dispatchers.IO) {
      val cleanHost = SshInputSanitizer.sanitizeHostname(hostname)
      val start = System.currentTimeMillis()
      try {
        Socket().use { socket ->
          socket.connect(InetSocketAddress(cleanHost, port), 3500)
        }
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1)
        TcpProbeResult(reachable = true, latencyMs = elapsed)
      } catch (e: Exception) {
        TcpProbeResult(
          reachable = false,
          error = e.message ?: "Unreachable",
        )
      }
    }
}

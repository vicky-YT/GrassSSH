package com.example.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GrassSshAiSystem"
private const val PRIMARY_MODEL = "gemini-3.8-flash"
private const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
private const val MODEL_REQUEST_TIMEOUT_MS = 120_000L
private const val MAX_TERMINAL_CONTEXT_LINES = 50

data class AiChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val extractedCommands: List<String> = emptyList(),
  val isStreaming: Boolean = false,
)

sealed interface AiSystemUiState {
  data object Idle : AiSystemUiState
  data object Loading : AiSystemUiState
  data class Streaming(val text: String) : AiSystemUiState
  data class Success(val text: String) : AiSystemUiState
  data class Error(val message: String) : AiSystemUiState
}

data class BuildPlanStep(
  val title: String,
  val command: String,
  val explanation: String,
  val isDestructive: Boolean = false,
)

data class GeneratedBuildPlan(
  val planTitle: String,
  val summary: String,
  val environmentAssumptions: String = "",
  val requiresSudo: Boolean,
  val steps: List<BuildPlanStep>,
  val verificationCommand: String = "",
)

sealed interface BuildPlanUiState {
  data object Idle : BuildPlanUiState
  data object Generating : BuildPlanUiState
  data class Ready(val plan: GeneratedBuildPlan) : BuildPlanUiState
  data class Error(val message: String) : BuildPlanUiState
}

data class ConsoleFixPlan(
  val errorTitle: String,
  val rootCause: String,
  val confidence: String,
  val requiresSudo: Boolean,
  val fixSteps: List<BuildPlanStep>,
  val verificationCommand: String = "",
)

sealed interface ConsoleFixUiState {
  data object Idle : ConsoleFixUiState
  data object Diagnosing : ConsoleFixUiState
  data class Ready(val fixPlan: ConsoleFixPlan) : ConsoleFixUiState
  data class Error(val message: String) : ConsoleFixUiState
}

class GrassSshAiRepository(
  private val appContext: Context,
) {

  private val defaultRequestOptions = RequestOptions(timeoutInMillis = MODEL_REQUEST_TIMEOUT_MS)

  private val chatSystemInstruction by lazy {
    content {
      text(
        """
        You are GrassSSH AI System, a senior Linux, SSH, DevOps, and Site Reliability Engineering assistant built into the GrassSSH Android terminal client.
        - Always identify yourself as GrassSSH AI System when asked your name or role.
        - When terminal output or an error is attached:
          1. Pinpoint the exact root cause concisely first.
          2. Provide idempotent, production-safe fix commands inside fenced ```bash blocks so the user can run them with one tap.
          3. Include a quick verification command to confirm the fix worked.
        - Never suggest interactive editors (nano, vim, vi) or interactive prompts; always use non-interactive flags (-y, --noconfirm) and heredocs (cat <<'EOF').
        - Respect the connected SSH user: if user is root, avoid redundant sudo; if non-root, use sudo only where required.
        - Warn explicitly before destructive commands (rm -rf, mkfs, reboot, firewall flush).
        """.trimIndent()
      )
    }
  }

  private fun createChatModel(modelName: String) =
    Firebase.ai.generativeModel(
      modelName = modelName,
      generationConfig = generationConfig {
        temperature = 0.3f
        thinkingConfig = thinkingConfig {
          thinkingLevel = ThinkingLevel.LOW
        }
      },
      requestOptions = defaultRequestOptions,
      systemInstruction = chatSystemInstruction,
    )

  private val chatModel by lazy { createChatModel(PRIMARY_MODEL) }
  private val chatFallbackModel by lazy { createChatModel(FALLBACK_MODEL) }

  private val buildPlanSchema by lazy {
    Schema.obj(
      properties = mapOf(
        "planTitle" to Schema.string(description = "Short professional title of the project, service, or setup being built"),
        "summary" to Schema.string(description = "1-2 sentence architectural overview of the build"),
        "environmentAssumptions" to Schema.string(description = "Target OS/runtime assumptions and ports/paths used"),
        "requiresSudo" to Schema.boolean(description = "True if any step requires sudo or root privileges"),
        "steps" to Schema.array(
          description = "Ordered, idempotent bash steps ready to execute sequentially via set -e",
          items = Schema.obj(
            properties = mapOf(
              "title" to Schema.string(description = "Short action title for this step"),
              "command" to Schema.string(description = "Valid non-interactive bash command or heredoc that succeeds cleanly without user prompts"),
              "explanation" to Schema.string(description = "Brief explanation of why this step is needed"),
              "isDestructive" to Schema.boolean(description = "True if command deletes files, resets firewall, or stops core services"),
            ),
          ),
        ),
        "verificationCommand" to Schema.string(description = "Final non-interactive command verifying the build works (e.g., curl healthcheck, systemctl is-active, or test run)"),
      ),
      optionalProperties = listOf("environmentAssumptions", "verificationCommand"),
    )
  }

  private val builderSystemInstruction by lazy {
    content {
      text(
        """
        You are GrassSSH AI System Pro Builder, an expert Linux & DevOps automation architect.
        Given any user build request, design a complete, production-grade, error-free bash implementation plan that executes cleanly over an SSH PTY via `set -e`.
        Mandatory engineering standards:
        1. Idempotency & Dependency Safety: Check for required runtimes/tools first (`command -v node >/dev/null 2>&1 || ...`) and install missing packages cleanly with non-interactive flags (`DEBIAN_FRONTEND=noninteractive apt-get update -qq && apt-get install -y ...`).
        2. Zero Stubs / Real Working Code: When writing code, APIs, systemd units, Nginx configs, Dockerfiles, or scripts, write COMPLETE, valid, working contents via `cat <<'EOF' > path` inside `mkdir -p` directories. Never leave TODO placeholders.
        3. Non-Blocking Background Services: If launching a server/daemon (Node, Python, Uvicorn, Go, etc.), start it cleanly in background (or systemd/nohup with logs redirected to a file + small `sleep 1` + health check) so the SSH shell does NOT hang forever.
        4. Zero Interactive Prompts: Never use `nano`, `vim`, `less`, or interactive wizards.
        5. Respect SSH User Context: Omit `sudo` when SSH user is `root`; include `sudo` for privileged system operations when non-root.
        6. Mandatory Verification: Always provide a concrete `verificationCommand` that proves the app/service/script works without errors.
        """.trimIndent()
      )
    }
  }

  private fun createBuilderModel(modelName: String) =
    Firebase.ai.generativeModel(
      modelName = modelName,
      generationConfig = generationConfig {
        temperature = 0.2f
        responseMimeType = "application/json"
        responseSchema = buildPlanSchema
        thinkingConfig = thinkingConfig {
          thinkingLevel = ThinkingLevel.LOW
        }
      },
      requestOptions = defaultRequestOptions,
      systemInstruction = builderSystemInstruction,
    )

  private val builderModel by lazy { createBuilderModel(PRIMARY_MODEL) }
  private val builderFallbackModel by lazy { createBuilderModel(FALLBACK_MODEL) }

  private val consoleFixSchema by lazy {
    Schema.obj(
      properties = mapOf(
        "errorTitle" to Schema.string(description = "Short title naming the exact console error detected"),
        "rootCause" to Schema.string(description = "Clear explanation of why this console error occurred"),
        "confidence" to Schema.enumeration(
          values = listOf("High", "Medium"),
          description = "Confidence level in the diagnosis based on terminal output",
        ),
        "requiresSudo" to Schema.boolean(description = "True if any fix step uses sudo or root privileges"),
        "fixSteps" to Schema.array(
          description = "Minimal, safe, idempotent bash steps that resolve the error without breaking existing state",
          items = Schema.obj(
            properties = mapOf(
              "title" to Schema.string(description = "Short step title"),
              "command" to Schema.string(description = "Clean non-interactive bash command to fix the issue"),
              "explanation" to Schema.string(description = "Why this fixes the root cause"),
              "isDestructive" to Schema.boolean(description = "True if step stops a process, removes locks/files, or alters firewall"),
            ),
          ),
        ),
        "verificationCommand" to Schema.string(description = "Safe command to verify the error is completely resolved"),
      ),
      optionalProperties = listOf("verificationCommand"),
    )
  }

  private val fixErrorSystemInstruction by lazy {
    content {
      text(
        """
        You are GrassSSH AI System Error Resolver, an elite Linux/SSH/DevOps troubleshooter.
        Analyze the live console output and diagnose the exact root cause of any failure (missing packages, permission errors, port conflicts EADDRINUSE, broken apt/dpkg locks, Nginx/systemd config errors, Python/Node dependency errors, Docker daemon issues, or SSH errors).
        Rules for error-free fixes:
        - Produce minimal, safe, idempotent bash steps that execute cleanly via `set -e` without failing mid-way.
        - Never guess destructive cleanups like `rm -rf /`; only target exact broken locks/PIDs/configs seen in the log.
        - For apt locks, verify process state safely before unlocking/reconfiguring (`dpkg --configure -a`).
        - For port conflicts, inspect/restart the conflicting service cleanly rather than blind kills when possible.
        - Always provide a `verificationCommand` to confirm the problem is fixed.
        """.trimIndent()
      )
    }
  }

  private fun createFixErrorModel(modelName: String) =
    Firebase.ai.generativeModel(
      modelName = modelName,
      generationConfig = generationConfig {
        temperature = 0.15f
        responseMimeType = "application/json"
        responseSchema = consoleFixSchema
        thinkingConfig = thinkingConfig {
          thinkingLevel = ThinkingLevel.LOW
        }
      },
      requestOptions = defaultRequestOptions,
      systemInstruction = fixErrorSystemInstruction,
    )

  private val fixErrorModel by lazy { createFixErrorModel(PRIMARY_MODEL) }
  private val fixErrorFallbackModel by lazy { createFixErrorModel(FALLBACK_MODEL) }

  private fun isNetworkAvailable(): Boolean {
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private fun isTransientHighDemandOrTimeout(error: Throwable): Boolean {
    if (error is TimeoutCancellationException) return true
    val msg = error.localizedMessage.orEmpty() + " " + (error.cause?.localizedMessage.orEmpty())
    val lower = msg.lowercase()
    return lower.contains("high demand") ||
      lower.contains("overloaded") ||
      lower.contains("temporarily") ||
      lower.contains("try again") ||
      lower.contains("unavailable") ||
      lower.contains("resource exhausted") ||
      lower.contains("503") ||
      lower.contains("429")
  }

  private fun ensureReady() {
    if (FirebaseApp.getApps(appContext).isEmpty()) {
      runCatching { FirebaseApp.initializeApp(appContext) }
    }
    if (FirebaseApp.getApps(appContext).isEmpty()) {
      throw IllegalStateException(
        "Firebase is not initialized in this APK (google-services config missing or invalid). Rebuild via GitHub Actions with app/google-services.json present.",
      )
    }
    if (!isNetworkAvailable()) {
      throw IllegalStateException("No internet connection detected. Connect to Wi-Fi or mobile data and retry.")
    }
  }

  private fun formatFailureMessage(prefix: String, error: Throwable): String {
    val raw = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
    return "$prefix: $raw"
  }

  fun streamResponse(
    history: List<AiChatMessage>,
    userPrompt: String,
    terminalContext: String?,
    customInstructions: String = "",
  ): Flow<String> = flow {
    ensureReady()

    val chatHistory = history
      .filter { it.text.isNotBlank() && !it.isStreaming }
      .takeLast(10)
      .map { msg ->
        content(role = if (msg.isUser) "user" else "model") {
          text(msg.text)
        }
      }

    val fullPrompt = buildString {
      if (customInstructions.isNotBlank()) {
        appendLine("=== Custom GrassSSH AI System Preferences ===")
        appendLine(customInstructions)
        appendLine()
      }
      if (!terminalContext.isNullOrBlank()) {
        appendLine("=== Live JSch Terminal Context (Last $MAX_TERMINAL_CONTEXT_LINES Lines) ===")
        appendLine(limitTerminalContext(terminalContext))
        appendLine("=== End Terminal Context ===")
        appendLine()
      }
      append(userPrompt)
    }

    val chat = chatModel.startChat(history = chatHistory)
    val accumulated = StringBuilder()

    try {
      withTimeout(MODEL_REQUEST_TIMEOUT_MS) {
        chat.sendMessageStream(fullPrompt).collect { chunk ->
          val piece = chunk.text.orEmpty()
          if (piece.isNotEmpty()) {
            accumulated.append(piece)
            emit(accumulated.toString())
          }
        }
      }
      if (accumulated.isBlank()) {
        throw IllegalStateException("GrassSSH AI System returned an empty response.")
      }
    } catch (e: CancellationException) {
      if (e !is TimeoutCancellationException) throw e
      if (accumulated.isNotBlank()) {
        Log.w(TAG, "Stream ended after partial response; keeping received text.", e)
        return@flow
      }
      Log.w(TAG, "Primary stream timed out at 120s; falling back to $FALLBACK_MODEL.", e)
      runFallbackStreamOrUnary(chatHistory, fullPrompt, emitChunk = { emit(it) })
    } catch (e: Exception) {
      if (accumulated.isBlank() && isTransientHighDemandOrTimeout(e)) {
        Log.w(TAG, "Primary model hit transient/high-demand error; falling back to $FALLBACK_MODEL.", e)
        runFallbackStreamOrUnary(chatHistory, fullPrompt, emitChunk = { emit(it) })
      } else {
        Log.e(TAG, "GrassSSH AI System inference failed", e)
        throw IllegalStateException(formatFailureMessage("GrassSSH AI System failed", e), e)
      }
    }
  }

  private suspend fun runFallbackStreamOrUnary(
    chatHistory: List<com.google.firebase.ai.type.Content>,
    fullPrompt: String,
    emitChunk: suspend (String) -> Unit,
  ) {
    val fallbackAccumulated = StringBuilder()
    try {
      val fallbackChat = chatFallbackModel.startChat(history = chatHistory)
      withTimeout(MODEL_REQUEST_TIMEOUT_MS) {
        fallbackChat.sendMessageStream(fullPrompt).collect { chunk ->
          val piece = chunk.text.orEmpty()
          if (piece.isNotEmpty()) {
            fallbackAccumulated.append(piece)
            emitChunk(fallbackAccumulated.toString())
          }
        }
      }
      if (fallbackAccumulated.isBlank()) {
        val unary = withTimeout(MODEL_REQUEST_TIMEOUT_MS) {
          chatFallbackModel.startChat(history = chatHistory).sendMessage(fullPrompt)
        }
        val unaryText = unary.text?.takeIf { it.isNotBlank() }
          ?: throw IllegalStateException("GrassSSH AI System returned an empty response.")
        emitChunk(unaryText)
      }
    } catch (fallbackTimeout: TimeoutCancellationException) {
      if (fallbackAccumulated.isNotBlank()) return
      Log.e(TAG, "Fallback model timed out", fallbackTimeout)
      throw IllegalStateException(
        "GrassSSH AI System timed out. Check internet / Play Services connectivity and retry.",
        fallbackTimeout,
      )
    } catch (cancel: CancellationException) {
      throw cancel
    } catch (fallbackErr: Exception) {
      Log.e(TAG, "Fallback model failed", fallbackErr)
      throw IllegalStateException(formatFailureMessage("GrassSSH AI System failed", fallbackErr), fallbackErr)
    }
  }

  suspend fun generateBuildPlan(
    prompt: String,
    customInstructions: String = "",
    terminalContext: String? = null,
    sshUsername: String? = null,
  ): GeneratedBuildPlan {
    ensureReady()

    val composedPrompt = buildString {
      if (!sshUsername.isNullOrBlank()) {
        appendLine("Connected SSH User: $sshUsername (${if (sshUsername == "root") "root privileges, omit sudo" else "non-root user, use sudo when privileged"})")
      }
      if (customInstructions.isNotBlank()) {
        appendLine("Server / User Preferences: $customInstructions")
      }
      if (!terminalContext.isNullOrBlank()) {
        appendLine("Recent SSH Terminal Output (Last $MAX_TERMINAL_CONTEXT_LINES Lines):")
        appendLine(limitTerminalContext(terminalContext))
      }
      appendLine("Build Request: $prompt")
    }

    try {
      val response = withTimeout(MODEL_REQUEST_TIMEOUT_MS) {
        builderModel.generateContent(composedPrompt)
      }
      val jsonText = response.text?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("GrassSSH AI System returned an empty build plan.")
      return parseBuildPlanJson(jsonText)
    } catch (e: CancellationException) {
      if (e !is TimeoutCancellationException) throw e
      Log.w(TAG, "Primary builder timed out; trying $FALLBACK_MODEL", e)
      return generateBuildPlanFallback(composedPrompt)
    } catch (e: Exception) {
      if (isTransientHighDemandOrTimeout(e)) {
        Log.w(TAG, "Primary builder hit transient/high-demand error; trying $FALLBACK_MODEL", e)
        return generateBuildPlanFallback(composedPrompt)
      }
      Log.e(TAG, "GrassSSH AI System build plan failed", e)
      throw IllegalStateException(formatFailureMessage("Build plan failed", e), e)
    }
  }

  private suspend fun generateBuildPlanFallback(
    composedPrompt: String,
  ): GeneratedBuildPlan {
    try {
      val response = withTimeout(MODEL_REQUEST_TIMEOUT_MS) {
        builderFallbackModel.generateContent(composedPrompt)
      }
      val jsonText = response.text?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("GrassSSH AI System returned an empty build plan.")
      return parseBuildPlanJson(jsonText)
    } catch (e: TimeoutCancellationException) {
      throw IllegalStateException("Build generation timed out. Check internet / Play Services connectivity and retry.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      throw IllegalStateException(formatFailureMessage("Build plan failed", e), e)
    }
  }

  suspend fun diagnoseAndFixConsoleError(
    terminalContext: String,
    customHint: String? = null,
    customInstructions: String = "",
    sshUsername: String? = null,
  ): ConsoleFixPlan {
    ensureReady()

    val composedPrompt = buildString {
      if (!sshUsername.isNullOrBlank()) {
        appendLine("Connected SSH User: $sshUsername (${if (sshUsername == "root") "root privileges, omit sudo" else "non-root user, use sudo when privileged"})")
      }
      if (customInstructions.isNotBlank()) {
        appendLine("Server / User Preferences: $customInstructions")
      }
      if (!customHint.isNullOrBlank()) {
        appendLine("Detected Error / User Note: $customHint")
      }
      appendLine("=== Console Output to Diagnose & Fix (Last $MAX_TERMINAL_CONTEXT_LINES Lines) ===")
      appendLine(limitTerminalContext(terminalContext))
    }

    try {
      val response = withTimeout(MODEL_REQUEST_TIMEOUT_MS) {
        fixErrorModel.generateContent(composedPrompt)
      }
      val jsonText = response.text?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("GrassSSH AI System returned an empty fix plan.")
      return parseConsoleFixJson(jsonText)
    } catch (e: CancellationException) {
      if (e !is TimeoutCancellationException) throw e
      Log.w(TAG, "Primary fix model timed out; trying $FALLBACK_MODEL", e)
      return diagnoseConsoleErrorFallback(composedPrompt)
    } catch (e: Exception) {
      if (isTransientHighDemandOrTimeout(e)) {
        Log.w(TAG, "Primary fix model hit transient/high-demand error; trying $FALLBACK_MODEL", e)
        return diagnoseConsoleErrorFallback(composedPrompt)
      }
      Log.e(TAG, "GrassSSH AI System console fix failed", e)
      throw IllegalStateException(formatFailureMessage("Console fix failed", e), e)
    }
  }

  private suspend fun diagnoseConsoleErrorFallback(
    composedPrompt: String,
  ): ConsoleFixPlan {
    try {
      val response = withTimeout(MODEL_REQUEST_TIMEOUT_MS) {
        fixErrorFallbackModel.generateContent(composedPrompt)
      }
      val jsonText = response.text?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("GrassSSH AI System returned an empty fix plan.")
      return parseConsoleFixJson(jsonText)
    } catch (e: TimeoutCancellationException) {
      throw IllegalStateException("Error diagnosis timed out. Check internet / Play Services connectivity and retry.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      throw IllegalStateException(formatFailureMessage("Console fix failed", e), e)
    }
  }

  companion object {
    private val CODE_BLOCK_REGEX = Regex("```(?:bash|sh|shell|zsh)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    fun limitTerminalContext(
      terminalContext: String,
      maxLines: Int = MAX_TERMINAL_CONTEXT_LINES,
    ): String {
      if (terminalContext.isBlank() || maxLines <= 0) return ""
      return terminalContext
        .trimEnd()
        .lines()
        .takeLast(maxLines)
        .joinToString("\n")
    }

    fun extractShellCommands(markdownText: String): List<String> {
      val commands = LinkedHashSet<String>()
      CODE_BLOCK_REGEX.findAll(markdownText).forEach { match ->
        val block = match.groupValues[1].trim()
        if (block.isNotEmpty()) {
          val lines = block.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
          if (lines.size == 1) {
            commands.add(lines.first().removePrefix("$ "))
          } else if (lines.isNotEmpty()) {
            val joined = lines.joinToString(" && ") { it.removePrefix("$ ") }
            if (joined.length <= 180) {
              commands.add(joined)
            } else {
              lines.take(4).forEach { commands.add(it.removePrefix("$ ")) }
            }
          }
        }
      }
      return commands.toList()
    }

    fun parseBuildPlanJson(jsonString: String): GeneratedBuildPlan {
      val root = JSONObject(jsonString)
      val planTitle = root.optString("planTitle", "Custom Build Plan").ifBlank { "Custom Build Plan" }
      val summary = root.optString("summary", "")
      val environmentAssumptions = root.optString("environmentAssumptions", "")
      val requiresSudo = root.optBoolean("requiresSudo", false)
      val verificationCommand = root.optString("verificationCommand", "").trim()
      val stepsArray = root.getJSONArray("steps")
      val steps = (0 until stepsArray.length()).mapNotNull { idx ->
        val obj = stepsArray.getJSONObject(idx)
        val cmd = obj.optString("command", "").trim()
        if (cmd.isEmpty()) return@mapNotNull null
        BuildPlanStep(
          title = obj.optString("title", "Step ${idx + 1}"),
          command = cmd,
          explanation = obj.optString("explanation", ""),
          isDestructive = obj.optBoolean("isDestructive", false),
        )
      }
      require(steps.isNotEmpty()) { "Build plan contained no executable steps." }
      return GeneratedBuildPlan(
        planTitle = planTitle,
        summary = summary,
        environmentAssumptions = environmentAssumptions,
        requiresSudo = requiresSudo,
        steps = steps,
        verificationCommand = verificationCommand,
      )
    }

    fun parseConsoleFixJson(jsonString: String): ConsoleFixPlan {
      val root = JSONObject(jsonString)
      val errorTitle = root.optString("errorTitle", "Console Error Fix").ifBlank { "Console Error Fix" }
      val rootCause = root.optString("rootCause", "")
      val confidence = root.optString("confidence", "High").ifBlank { "High" }
      val requiresSudo = root.optBoolean("requiresSudo", false)
      val verificationCommand = root.optString("verificationCommand", "").trim()
      val stepsArray = root.getJSONArray("fixSteps")
      val steps = (0 until stepsArray.length()).mapNotNull { idx ->
        val obj = stepsArray.getJSONObject(idx)
        val cmd = obj.optString("command", "").trim()
        if (cmd.isEmpty()) return@mapNotNull null
        BuildPlanStep(
          title = obj.optString("title", "Fix Step ${idx + 1}"),
          command = cmd,
          explanation = obj.optString("explanation", ""),
          isDestructive = obj.optBoolean("isDestructive", false),
        )
      }
      require(steps.isNotEmpty()) { "Fix plan contained no executable steps." }
      return ConsoleFixPlan(
        errorTitle = errorTitle,
        rootCause = rootCause,
        confidence = confidence,
        requiresSudo = requiresSudo,
        fixSteps = steps,
        verificationCommand = verificationCommand,
      )
    }
  }
}

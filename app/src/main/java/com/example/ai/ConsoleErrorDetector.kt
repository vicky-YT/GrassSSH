package com.example.ai

import androidx.compose.ui.text.AnnotatedString

data class DetectedConsoleError(
  val summaryLine: String,
  val category: String,
  val contextSnippet: String,
)

object ConsoleErrorDetector {

  private val ERROR_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("""\[GrassSSH Error\].+""", RegexOption.IGNORE_CASE) to "SSH Connection Error",
    Regex("""(?:^|\s)(?:bash|sh|zsh):\s+.*command not found""", RegexOption.IGNORE_CASE) to "Missing Command / Package",
    Regex("""No such file or directory""", RegexOption.IGNORE_CASE) to "Missing File or Path",
    Regex("""Permission denied|EACCES|Operation not permitted|must be root""", RegexOption.IGNORE_CASE) to "Permission / Sudo",
    Regex("""EADDRINUSE|address already in use|port is already allocated""", RegexOption.IGNORE_CASE) to "Port Already in Use",
    Regex("""Unable to locate package|E: Could not get lock|dpkg was interrupted|apt-get.*error""", RegexOption.IGNORE_CASE) to "Package Manager Error",
    Regex("""ModuleNotFoundError|ImportError|Cannot find module|npm ERR!|pip.*error|SyntaxError|Traceback \(most recent call last\)""", RegexOption.IGNORE_CASE) to "Runtime / Dependency Error",
    Regex("""Connection refused|Connection timed out|Could not resolve hostname|UnknownHostException|Network is unreachable""", RegexOption.IGNORE_CASE) to "Network / DNS Error",
    Regex("""nginx: \[emerg\]|Failed to start .*\.service|Unit .* could not be found|systemd.*failed""", RegexOption.IGNORE_CASE) to "Service / Config Error",
    Regex("""docker:.*error|Cannot connect to the Docker daemon""", RegexOption.IGNORE_CASE) to "Docker Error",
    Regex("""fatal:|ERROR:|exited with code [1-9]\d*|FAILED at step""", RegexOption.IGNORE_CASE) to "Command Failed",
  )

  fun detectLatestError(lines: List<AnnotatedString>): DetectedConsoleError? {
    val tail = lines
      .map { it.text.trimEnd() }
      .filter { it.isNotBlank() }
      .takeLast(30)
    if (tail.isEmpty()) return null

    // Scan from most recent line backwards
    for (idx in tail.lastIndex downTo 0) {
      val line = tail[idx].trim()
      // Ignore shell prompt echo lines or GrassSSH status info lines
      if (line.startsWith("[GrassSSH] Connecting") || line.startsWith("[GrassSSH] Connected")) continue
      for ((regex, category) in ERROR_PATTERNS) {
        if (regex.containsMatchIn(line)) {
          val start = (idx - 5).coerceAtLeast(0)
          val snippet = tail.subList(start, tail.size).joinToString("\n")
          return DetectedConsoleError(
            summaryLine = line.take(140),
            category = category,
            contextSnippet = snippet.takeLast(2500),
          )
        }
      }
    }
    return null
  }

  /**
   * Builds a clean, sequential, fail-fast bash execution script so multi-step builds/fixes
   * wait for each step to complete, halt cleanly on error, and avoid mangling multiline heredocs.
   */
  fun buildAtomicExecutionCommand(
    title: String,
    steps: List<BuildPlanStep>,
    verificationCommand: String? = null,
  ): String {
    val safeTag = "GRASS_EOF_${System.currentTimeMillis()}"
    val totalSteps = steps.size
    val scriptBody = buildString {
      appendLine("set -e")
      appendLine("echo '==> [GrassSSH] Starting: ${escapeSingleQuotes(title)}'")
      steps.forEachIndexed { index, step ->
        val stepNum = index + 1
        val stepLabel = escapeSingleQuotes(step.title)
        appendLine("echo '==> [$stepNum/$totalSteps] $stepLabel'")
        appendLine(step.command.trim())
      }
      val verify = verificationCommand?.trim().orEmpty()
      if (verify.isNotEmpty()) {
        appendLine("echo '==> [Verify] Checking result...'")
        appendLine(verify)
      }
      appendLine("echo '==> [GrassSSH] Completed successfully!'")
    }
    val scriptPath = "/tmp/grassssh_exec_${System.currentTimeMillis()}.sh"
    return buildString {
      appendLine("cat <<'$safeTag' > $scriptPath")
      append(scriptBody)
      appendLine(safeTag)
      append("bash $scriptPath; _gs_rc=\$?; rm -f $scriptPath; [ \$_gs_rc -eq 0 ] || echo \"[GrassSSH Error] Failed with exit code \$_gs_rc\"")
    }
  }

  private fun escapeSingleQuotes(input: String): String =
    input.replace("'", "'\\''")
}

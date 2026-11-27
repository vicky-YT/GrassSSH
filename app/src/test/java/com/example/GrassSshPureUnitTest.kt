package com.example

import androidx.compose.ui.text.AnnotatedString
import com.example.ai.BuildPlanStep
import com.example.ai.ConsoleErrorDetector
import com.example.ai.GrassSshAiRepository
import com.example.ssh.AnsiTerminalParser
import com.example.ssh.KeyAlgorithmOption
import com.example.ssh.SshInputSanitizer
import com.example.ssh.SshKeyManager
import com.example.ui.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrassSshPureUnitTest {

  @Test
  fun `console error detector catches real shell failures and ignores connect info`() {
    val cleanLines = listOf(
      AnnotatedString("[GrassSSH] Connecting to root@3.107.175.202:22..."),
      AnnotatedString("Welcome to Ubuntu 24.04 LTS"),
    )
    assertNull(ConsoleErrorDetector.detectLatestError(cleanLines))

    val errorLines = listOf(
      AnnotatedString("root@vps:~# node server.js"),
      AnnotatedString("bash: node: command not found"),
    )
    val detected = ConsoleErrorDetector.detectLatestError(errorLines)
    assertNotNull(detected)
    assertEquals("Missing Command / Package", detected?.category)
    assertTrue(detected?.summaryLine?.contains("command not found") == true)

    val portConflict = listOf(
      AnnotatedString("Error: listen EADDRINUSE: address already in use :::3000"),
    )
    assertEquals("Port Already in Use", ConsoleErrorDetector.detectLatestError(portConflict)?.category)
  }

  @Test
  fun `atomic execution builder generates fail-fast set -e script with verification`() {
    val script = ConsoleErrorDetector.buildAtomicExecutionCommand(
      title = "Setup Node API",
      steps = listOf(
        BuildPlanStep("Create dir", "mkdir -p ~/api", "dir", false),
        BuildPlanStep("Create file", "echo ok > ~/api/app.txt", "file", false),
      ),
      verificationCommand = "cat ~/api/app.txt",
    )
    assertTrue(script.contains("set -e"))
    assertTrue(script.contains("[1/2] Create dir"))
    assertTrue(script.contains("[2/2] Create file"))
    assertTrue(script.contains("cat ~/api/app.txt"))
    assertTrue(script.contains("rm -f /tmp/grassssh_exec_"))
  }

  @Test
  fun `sanitization, keygen, ansi parser, and command extraction work accurately`() {
    assertEquals(
      Triple("root", "3.107.175.202", 38688),
      MainViewModel.parseQuickConnectTarget("root@3.107.175.202.:38688", "root"),
    )
    assertEquals("3.107.175.202", SshInputSanitizer.sanitizeHostname(" ssh://3.107.175.202. "))

    val parser = AnsiTerminalParser()
    parser.feed("Loading 10%\rLoading 100%\n")
    assertEquals("Loading 100%", parser.snapshotHistory().first().text)

    val extracted = GrassSshAiRepository.extractShellCommands("```bash\nsudo systemctl restart nginx\n```")
    assertEquals(listOf("sudo systemctl restart nginx"), extracted)
  }
}

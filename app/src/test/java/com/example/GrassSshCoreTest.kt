package com.example

import androidx.compose.ui.graphics.Color
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GrassSshCoreTest {

  @Test
  fun `parseQuickConnectTarget parses user host and port variants`() {
    assertEquals(
      Triple("ubuntu", "192.168.1.40", 2222),
      MainViewModel.parseQuickConnectTarget("ubuntu@192.168.1.40:2222", "root"),
    )
    assertEquals(
      Triple("root", "3.107.175.202", 38688),
      MainViewModel.parseQuickConnectTarget("root@3.107.175.202.:38688", "root"),
    )
    assertEquals(
      "3.107.175.202",
      SshInputSanitizer.sanitizeHostname("  ssh://3.107.175.202.\u00A0 "),
    )
    assertEquals(
      Triple("admin", "prod.example.com", 22),
      MainViewModel.parseQuickConnectTarget("ssh admin@prod.example.com", "root"),
    )
    assertEquals(
      Triple("root", "10.0.0.5", 22),
      MainViewModel.parseQuickConnectTarget("10.0.0.5", "root"),
    )
    assertEquals(
      Triple("deploy", "::1", 2200),
      MainViewModel.parseQuickConnectTarget("deploy@[::1]:2200", "root"),
    )
    assertNull(MainViewModel.parseQuickConnectTarget("   ", "root"))
    assertNull(MainViewModel.parseQuickConnectTarget("user@host:99999", "root"))
  }

  @Test
  fun `ansi parser handles colors carriage return and osc title`() {
    var capturedTitle = ""
    val parser = AnsiTerminalParser(onTitleChanged = { capturedTitle = it })
    parser.feed("\u001B]0;user@node: ~\u0007")
    assertEquals("user@node: ~", capturedTitle)

    parser.feed("Loading 10%\rLoading 100%\n")
    val history = parser.snapshotHistory()
    assertEquals(1, history.size)
    assertEquals("Loading 100%", history[0].text)

    parser.feed("\u001B[32mOK\u001B[0m")
    val active = parser.snapshotActiveLine()
    assertEquals("OK", active.text)
    assertTrue(active.spanStyles.any { it.item.color == Color(0xFF22C55E) })
  }

  @Test
  fun `ssh key manager generates valid rsa keypair and fingerprint`() {
    val entity = SshKeyManager.generateKeyPair("test-rsa", KeyAlgorithmOption.RSA_2048)
    assertNotNull(entity)
    assertTrue(entity.privateKeyPem.contains("PRIVATE KEY"))
    assertTrue(entity.publicKeyOpenSsh.startsWith("ssh-rsa "))
    assertTrue(entity.fingerprintSha256.startsWith("SHA256:"))
  }

  @Test
  fun `grassssh ai repository extracts shell commands from bash blocks`() {
    val markdown = """
      Check your SSH daemon and firewall ports:
      ```bash
      sudo systemctl status ssh
      ```
      Then inspect listening ports:
      ```sh
      $ ss -tulnp
      ```
    """.trimIndent()
    val commands = GrassSshAiRepository.extractShellCommands(markdown)
    assertEquals(listOf("sudo systemctl status ssh", "ss -tulnp"), commands)
  }

  @Test
  fun `grassssh ai repository parses structured build plan json`() {
    val json = """
      {
        "planTitle": "Node Hello API",
        "summary": "Sets up an Express API on port 3000",
        "requiresSudo": false,
        "steps": [
          {
            "title": "Create directory",
            "command": "mkdir -p ~/myapp && cd ~/myapp",
            "explanation": "Creates project directory",
            "isDestructive": false
          }
        ]
      }
    """.trimIndent()
    val plan = GrassSshAiRepository.parseBuildPlanJson(json)
    assertEquals("Node Hello API", plan.planTitle)
    assertEquals(false, plan.requiresSudo)
    assertEquals(1, plan.steps.size)
    assertEquals("mkdir -p ~/myapp && cd ~/myapp", plan.steps[0].command)
  }
}

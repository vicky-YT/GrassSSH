package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.AiChatMessage
import com.example.ai.AiLanguageMode
import com.example.ai.AiSystemUiState
import com.example.ai.BuildPlanUiState
import com.example.ai.ConsoleErrorDetector
import com.example.ai.ConsoleFixPlan
import com.example.ai.ConsoleFixUiState
import com.example.ai.GeneratedBuildPlan
import com.example.data.local.TerminalSettings
import com.example.data.model.SnippetEntity
import com.example.ssh.CredentialPrompt
import com.example.ssh.HostKeyPrompt
import com.example.ssh.LiveSshSession
import com.example.ssh.SessionStatus
import com.example.ssh.SftpEntry
import com.example.ui.components.SshTerminalView
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPhosphor
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
  sessions: List<LiveSshSession>,
  activeSessionId: String?,
  settings: TerminalSettings,
  snippets: List<SnippetEntity>,
  aiMessages: List<AiChatMessage>,
  aiUiState: AiSystemUiState,
  buildPlanState: BuildPlanUiState,
  consoleFixState: ConsoleFixUiState,
  includeAiTerminalContext: Boolean,
  onToggleIncludeAiContext: (Boolean) -> Unit,
  onSelectAiLanguageMode: (AiLanguageMode) -> Unit,
  onSendAiPrompt: (String, Bitmap?) -> Unit,
  onDismissAiError: () -> Unit = {},
  onClearAiChat: () -> Unit,
  onGenerateBuildPlan: (String) -> Unit,
  onClearBuildPlan: () -> Unit,
  onDiagnoseConsoleError: (String?, Bitmap?) -> Unit,
  onClearConsoleFix: () -> Unit,
  onRunBuildPlan: (GeneratedBuildPlan) -> Unit,
  onRunFixPlan: (ConsoleFixPlan) -> Unit,
  onSaveBuildPlanAsSnippet: (GeneratedBuildPlan) -> Unit,
  onSaveCustomAiInstructions: (String) -> Unit,
  onSelectSession: (String) -> Unit,
  onCloseSession: (String) -> Unit,
  onChangeFontSize: (Int) -> Unit,
  onNavigateToHosts: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeSession = sessions.firstOrNull { it.sessionId == activeSessionId } ?: sessions.lastOrNull()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

  if (activeSession == null) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(38.dp),
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.no_active_session_title),
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.no_active_session_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
          onClick = onNavigateToHosts,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("empty_terminal_browse_hosts"),
        ) {
          Text(stringResource(R.string.go_to_hosts))
        }
      }
    }
    return
  }

  val status by activeSession.status.collectAsStateWithLifecycle()
  val terminalLines by activeSession.lines.collectAsStateWithLifecycle()
  val terminalTitle by activeSession.terminalTitle.collectAsStateWithLifecycle()
  val hostKeyPrompt by activeSession.pendingHostKeyPrompt.collectAsStateWithLifecycle()
  val credentialPrompt by activeSession.pendingCredentialPrompt.collectAsStateWithLifecycle()
  val sftpState by activeSession.sftpState.collectAsStateWithLifecycle()

  var commandInput by remember(activeSession.sessionId) { mutableStateOf("") }
  var ctrlArmed by remember { mutableStateOf(false) }
  var showSftpSheet by remember { mutableStateOf(false) }
  var showSnippetsSheet by remember { mutableStateOf(false) }
  var showAiPanel by remember { mutableStateOf(false) }
  var aiPanelExpanded by remember { mutableStateOf(false) }
  var forcedAiMode by remember { mutableStateOf<AiPanelMode?>(null) }
  var aiModeTriggerKey by remember { mutableStateOf(0) }
  var dismissedErrorLine by remember(activeSession.sessionId) { mutableStateOf<String?>(null) }

  val terminalContextPreview = remember(terminalLines) {
    terminalLines.lastOrNull { it.text.isNotBlank() }?.text?.trim()
  }
  val detectedError = remember(terminalLines) {
    ConsoleErrorDetector.detectLatestError(terminalLines)
  }

  BackHandler(enabled = showAiPanel) {
    if (aiPanelExpanded) {
      aiPanelExpanded = false
    } else {
      showAiPanel = false
    }
  }

  // Keep screen on while viewing an active session if preference is enabled
  DisposableEffect(settings.keepScreenOn, status) {
    val window = (context as? Activity)?.window
    if (settings.keepScreenOn && status == SessionStatus.CONNECTED) {
      window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    onDispose {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  val theme = settings.themePreset

  fun triggerHaptic() {
    if (settings.vibrateOnKey) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .imePadding(),
    containerColor = theme.background,
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      // Multi-session tabs
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.surface)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        sessions.forEach { sess ->
          val sessStatus by sess.status.collectAsStateWithLifecycle()
          val isSelected = sess.sessionId == activeSession.sessionId
          val dotColor = when (sessStatus) {
            SessionStatus.CONNECTED -> EmeraldPhosphor
            SessionStatus.CONNECTING,
            SessionStatus.AWAITING_HOST_KEY,
            SessionStatus.AWAITING_CREDENTIAL -> AmberWarning
            SessionStatus.DISCONNECTED,
            SessionStatus.ERROR -> CoralError
          }
          Surface(
            onClick = { onSelectSession(sess.sessionId) },
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) theme.accent.copy(alpha = 0.18f) else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              if (isSelected) theme.accent.copy(alpha = 0.6f) else theme.foreground.copy(alpha = 0.15f),
            ),
            modifier = Modifier.testTag("session_tab_${sess.sessionId}"),
          ) {
            Row(
              modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(dotColor),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = sess.hostConfig.alias.ifBlank { sess.hostConfig.hostname },
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                color = theme.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              IconButton(
                onClick = { onCloseSession(sess.sessionId) },
                modifier = Modifier.size(28.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close session tab",
                  tint = theme.foreground.copy(alpha = 0.7f),
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }

      // Terminal Action Toolbar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.surface.copy(alpha = 0.88f))
          .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = terminalTitle,
            style = MaterialTheme.typography.labelLarge,
            color = theme.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${activeSession.hostConfig.username}@${activeSession.hostConfig.hostname}:${activeSession.hostConfig.port} • ${status.label}",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = theme.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { onChangeFontSize((settings.fontSizeSp - 1).coerceAtLeast(10)) },
            modifier = Modifier.size(36.dp),
          ) {
            Text(
              text = "A-",
              fontFamily = JetBrainsMonoFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = theme.foreground,
            )
          }
          IconButton(
            onClick = { onChangeFontSize((settings.fontSizeSp + 1).coerceAtMost(20)) },
            modifier = Modifier.size(36.dp),
          ) {
            Text(
              text = "A+",
              fontFamily = JetBrainsMonoFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = theme.foreground,
            )
          }
          Surface(
            onClick = {
              showAiPanel = !showAiPanel
              if (!showAiPanel) aiPanelExpanded = false
            },
            shape = RoundedCornerShape(10.dp),
            color = if (showAiPanel) theme.accent else theme.accent.copy(alpha = 0.16f),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              theme.accent.copy(alpha = if (showAiPanel) 1f else 0.5f),
            ),
            modifier = Modifier
              .padding(end = 2.dp)
              .testTag("terminal_ai_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Toggle GrassSSH AI chat panel",
                tint = if (showAiPanel) Color(0xFF042217) else theme.accent,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = "AI",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (showAiPanel) Color(0xFF042217) else theme.accent,
              )
            }
          }
          IconButton(
            onClick = {
              showSftpSheet = true
              activeSession.loadSftpDirectory()
            },
            enabled = status == SessionStatus.CONNECTED,
            modifier = Modifier
              .size(38.dp)
              .testTag("terminal_sftp_button"),
          ) {
            Icon(
              imageVector = Icons.Default.FolderOpen,
              contentDescription = stringResource(R.string.sftp_browser),
              tint = if (status == SessionStatus.CONNECTED) theme.accent else theme.foreground.copy(alpha = 0.3f),
              modifier = Modifier.size(19.dp),
            )
          }
          IconButton(
            onClick = { showSnippetsSheet = true },
            modifier = Modifier
              .size(38.dp)
              .testTag("terminal_snippets_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Code,
              contentDescription = stringResource(R.string.snippets_sheet_title),
              tint = theme.foreground,
              modifier = Modifier.size(19.dp),
            )
          }
          IconButton(
            onClick = {
              val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clip.setPrimaryClip(ClipData.newPlainText("GrassSSH Output", activeSession.copyPlainOutput()))
              onNotify("Terminal buffer copied")
            },
            modifier = Modifier.size(38.dp),
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = stringResource(R.string.copy_output),
              tint = theme.foreground,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = { activeSession.clearTerminal() },
            modifier = Modifier.size(38.dp),
          ) {
            Icon(
              imageVector = Icons.Default.CleaningServices,
              contentDescription = stringResource(R.string.clear_terminal),
              tint = theme.foreground,
              modifier = Modifier.size(18.dp),
            )
          }
          if (status == SessionStatus.DISCONNECTED || status == SessionStatus.ERROR) {
            IconButton(
              onClick = { activeSession.reconnect() },
              modifier = Modifier.size(38.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.reconnect),
                tint = EmeraldPhosphor,
                modifier = Modifier.size(19.dp),
              )
            }
          } else {
            IconButton(
              onClick = { activeSession.disconnect() },
              modifier = Modifier.size(38.dp),
            ) {
              Icon(
                imageVector = Icons.Default.LinkOff,
                contentDescription = stringResource(R.string.disconnect),
                tint = CoralError,
                modifier = Modifier.size(19.dp),
              )
            }
          }
        }
      }

      HorizontalDivider(color = theme.foreground.copy(alpha = 0.12f))

      // Live Terminal + Dedicated 'GrassSSH AI' Chat Panel (adaptive split on wide/portrait)
      BoxWithConstraints(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        val wideSplit = maxWidth >= 720.dp
        if (showAiPanel && wideSplit) {
          Row(modifier = Modifier.fillMaxSize()) {
            if (!aiPanelExpanded) {
              SshTerminalView(
                session = activeSession,
                theme = theme,
                fontSizeSp = settings.fontSizeSp,
                modifier = Modifier
                  .weight(0.55f)
                  .fillMaxHeight(),
              )
              VerticalDivider(color = theme.accent.copy(alpha = 0.45f), thickness = 1.dp)
            }
            Surface(
              color = MaterialTheme.colorScheme.background,
              modifier = Modifier
                .weight(if (aiPanelExpanded) 1f else 0.45f)
                .fillMaxHeight()
                .testTag("terminal_grassssh_ai_panel"),
            ) {
              GrassSshAiPanel(
                messages = aiMessages,
                aiUiState = aiUiState,
                buildPlanState = buildPlanState,
                consoleFixState = consoleFixState,
                customAiInstructions = settings.customAiInstructions,
                aiLanguageMode = settings.aiLanguageMode,
                activeSessionLabel = "${activeSession.hostConfig.username}@${activeSession.hostConfig.hostname}",
                hasActiveSession = status == SessionStatus.CONNECTED,
                includeTerminalContext = includeAiTerminalContext,
                onToggleIncludeContext = onToggleIncludeAiContext,
                onSelectLanguageMode = onSelectAiLanguageMode,
                onSendPrompt = onSendAiPrompt,
                onDismissAiError = onDismissAiError,
                onClearChat = onClearAiChat,
                onGenerateBuildPlan = onGenerateBuildPlan,
                onClearBuildPlan = onClearBuildPlan,
                onDiagnoseConsoleError = onDiagnoseConsoleError,
                onClearConsoleFix = onClearConsoleFix,
                onRunCommand = { cmd ->
                  activeSession.sendLine(cmd)
                  aiPanelExpanded = false
                },
                onRunBuildPlan = { plan ->
                  onRunBuildPlan(plan)
                  aiPanelExpanded = false
                },
                onRunFixPlan = { plan ->
                  onRunFixPlan(plan)
                  aiPanelExpanded = false
                },
                onSaveBuildPlanAsSnippet = onSaveBuildPlanAsSnippet,
                onSaveCustomInstructions = onSaveCustomAiInstructions,
                onNotify = onNotify,
                initialMode = AiPanelMode.ASK,
                forcedMode = forcedAiMode,
                modeTriggerKey = aiModeTriggerKey,
                hasTerminalContextSource = true,
                terminalContextPreview = terminalContextPreview,
                titleOverride = "GrassSSH AI",
                isExpanded = aiPanelExpanded,
                onToggleExpand = { aiPanelExpanded = !aiPanelExpanded },
                onCollapse = {
                  showAiPanel = false
                  aiPanelExpanded = false
                },
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        } else if (showAiPanel) {
          Column(modifier = Modifier.fillMaxSize()) {
            if (!aiPanelExpanded) {
              SshTerminalView(
                session = activeSession,
                theme = theme,
                fontSizeSp = settings.fontSizeSp,
                modifier = Modifier
                  .weight(0.42f)
                  .fillMaxWidth(),
              )
              HorizontalDivider(color = theme.accent.copy(alpha = 0.5f), thickness = 1.5.dp)
            }
            Surface(
              color = MaterialTheme.colorScheme.background,
              modifier = Modifier
                .weight(if (aiPanelExpanded) 1f else 0.58f)
                .fillMaxWidth()
                .testTag("terminal_grassssh_ai_panel"),
            ) {
              GrassSshAiPanel(
                messages = aiMessages,
                aiUiState = aiUiState,
                buildPlanState = buildPlanState,
                consoleFixState = consoleFixState,
                customAiInstructions = settings.customAiInstructions,
                aiLanguageMode = settings.aiLanguageMode,
                activeSessionLabel = "${activeSession.hostConfig.username}@${activeSession.hostConfig.hostname}",
                hasActiveSession = status == SessionStatus.CONNECTED,
                includeTerminalContext = includeAiTerminalContext,
                onToggleIncludeContext = onToggleIncludeAiContext,
                onSelectLanguageMode = onSelectAiLanguageMode,
                onSendPrompt = onSendAiPrompt,
                onDismissAiError = onDismissAiError,
                onClearChat = onClearAiChat,
                onGenerateBuildPlan = onGenerateBuildPlan,
                onClearBuildPlan = onClearBuildPlan,
                onDiagnoseConsoleError = onDiagnoseConsoleError,
                onClearConsoleFix = onClearConsoleFix,
                onRunCommand = { cmd ->
                  activeSession.sendLine(cmd)
                  aiPanelExpanded = false
                },
                onRunBuildPlan = { plan ->
                  onRunBuildPlan(plan)
                  aiPanelExpanded = false
                },
                onRunFixPlan = { plan ->
                  onRunFixPlan(plan)
                  aiPanelExpanded = false
                },
                onSaveBuildPlanAsSnippet = onSaveBuildPlanAsSnippet,
                onSaveCustomInstructions = onSaveCustomAiInstructions,
                onNotify = onNotify,
                initialMode = AiPanelMode.ASK,
                forcedMode = forcedAiMode,
                modeTriggerKey = aiModeTriggerKey,
                hasTerminalContextSource = true,
                terminalContextPreview = terminalContextPreview,
                titleOverride = "GrassSSH AI",
                isExpanded = aiPanelExpanded,
                onToggleExpand = { aiPanelExpanded = !aiPanelExpanded },
                onCollapse = {
                  showAiPanel = false
                  aiPanelExpanded = false
                },
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        } else {
          SshTerminalView(
            session = activeSession,
            theme = theme,
            fontSizeSp = settings.fontSizeSp,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      if (!showAiPanel && detectedError != null && detectedError.summaryLine != dismissedErrorLine) {
        Surface(
          color = CoralError.copy(alpha = 0.16f),
          border = androidx.compose.foundation.BorderStroke(1.dp, CoralError.copy(alpha = 0.55f)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("console_error_autofix_banner"),
          shape = RoundedCornerShape(10.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.BugReport,
              contentDescription = null,
              tint = CoralError,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = detectedError.category,
                style = MaterialTheme.typography.labelSmall,
                color = CoralError,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = detectedError.summaryLine,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = theme.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              onClick = {
                forcedAiMode = AiPanelMode.FIX
                aiModeTriggerKey += 1
                showAiPanel = true
                onDiagnoseConsoleError(detectedError.summaryLine, null)
              },
              shape = RoundedCornerShape(8.dp),
              color = theme.accent,
              modifier = Modifier.testTag("autofix_banner_action"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = Color(0xFF042217),
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Fix with AI",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF042217),
                )
              }
            }
            IconButton(
              onClick = { dismissedErrorLine = detectedError.summaryLine },
              modifier = Modifier.size(28.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss error suggestion",
                tint = theme.foreground.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp),
              )
            }
          }
        }
      }

      if (!showAiPanel) {
      // SSH Special Keys Accessory Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.surface)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TerminalKeyChip(
          label = "CTRL",
          active = ctrlArmed,
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            ctrlArmed = !ctrlArmed
          },
          modifier = Modifier.testTag("key_ctrl"),
        )
        TerminalKeyChip(
          label = "ESC",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendBytes(byteArrayOf(0x1B))
          },
        )
        TerminalKeyChip(
          label = "TAB",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            if (commandInput.isNotEmpty()) {
              activeSession.sendText(commandInput + "\t")
              commandInput = ""
            } else {
              activeSession.sendBytes(byteArrayOf(0x09))
            }
          },
        )
        TerminalKeyChip(
          label = "^C",
          accent = CoralError,
          fg = CoralError,
          onClick = {
            triggerHaptic()
            activeSession.sendCtrlChar('C')
          },
          modifier = Modifier.testTag("key_ctrl_c"),
        )
        TerminalKeyChip(
          label = "^D",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendCtrlChar('D')
          },
        )
        TerminalKeyChip(
          label = "^L",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendCtrlChar('L')
          },
        )
        TerminalKeyChip(
          label = "↑",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendText("\u001B[A")
          },
        )
        TerminalKeyChip(
          label = "↓",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendText("\u001B[B")
          },
        )
        TerminalKeyChip(
          label = "←",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendText("\u001B[D")
          },
        )
        TerminalKeyChip(
          label = "→",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendText("\u001B[C")
          },
        )
        TerminalKeyChip(
          label = "HOME",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendText("\u001B[H")
          },
        )
        TerminalKeyChip(
          label = "END",
          accent = theme.accent,
          fg = theme.foreground,
          onClick = {
            triggerHaptic()
            activeSession.sendText("\u001B[F")
          },
        )
        listOf("|", "~", "/", "-").forEach { sym ->
          TerminalKeyChip(
            label = sym,
            accent = theme.accent,
            fg = theme.foreground,
            onClick = {
              triggerHaptic()
              commandInput += sym
            },
          )
        }
      }

      // Command Input Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.surface)
          .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = commandInput,
          onValueChange = { updated ->
            if (ctrlArmed && updated.length > commandInput.length) {
              val typedChar = updated.last()
              if (typedChar.isLetter()) {
                activeSession.sendCtrlChar(typedChar)
                ctrlArmed = false
                return@OutlinedTextField
              }
            }
            commandInput = updated
          },
          placeholder = {
            Text(
              text = if (ctrlArmed) "CTRL armed: tap a letter key (e.g. c, d, z)..."
              else stringResource(R.string.terminal_input_hint),
              fontFamily = JetBrainsMonoFamily,
              fontSize = 13.sp,
              color = theme.foreground.copy(alpha = 0.5f),
            )
          },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = JetBrainsMonoFamily,
            color = theme.foreground,
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = theme.accent,
            unfocusedBorderColor = theme.foreground.copy(alpha = 0.25f),
            cursorColor = theme.cursor,
            focusedContainerColor = theme.background,
            unfocusedContainerColor = theme.background,
          ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              triggerHaptic()
              activeSession.sendLine(commandInput)
              commandInput = ""
            },
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("terminal_command_input"),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          onClick = {
            triggerHaptic()
            activeSession.sendLine(commandInput)
            commandInput = ""
          },
          shape = RoundedCornerShape(12.dp),
          color = theme.accent,
          modifier = Modifier
            .size(48.dp)
            .testTag("terminal_send_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = stringResource(R.string.send),
              tint = Color(0xFF042217),
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
      } // end if (!showAiPanel)
    }
  }

  // Host Key Verification Dialog (TOFU)
  hostKeyPrompt?.let { prompt ->
    HostKeyVerificationDialog(
      prompt = prompt,
      onAccept = { activeSession.respondHostKey(true) },
      onReject = { activeSession.respondHostKey(false) },
    )
  }

  // Interactive Password / Passphrase Prompt Dialog
  credentialPrompt?.let { prompt ->
    CredentialPromptDialog(
      prompt = prompt,
      onSubmit = { secret -> activeSession.respondCredential(secret) },
      onCancel = { activeSession.respondCredential(null) },
    )
  }

  // SFTP File Explorer Bottom Sheet
  if (showSftpSheet) {
    ModalBottomSheet(onDismissRequest = { showSftpSheet = false }) {
      SftpExplorerSheet(
        sftpState = sftpState,
        onRefresh = { activeSession.loadSftpDirectory() },
        onNavigateUp = {
          val cur = sftpState.currentPath
          if (cur != "/" && cur.contains('/')) {
            val parent = cur.substringBeforeLast('/').ifEmpty { "/" }
            activeSession.loadSftpDirectory(parent)
          }
        },
        onOpenEntry = { entry -> activeSession.previewSftpFile(entry) },
        onDeleteEntry = { entry -> activeSession.deleteSftpEntry(entry) },
        onCreateFolder = { name -> activeSession.createSftpFolder(name) },
        onDismissPreview = { activeSession.dismissSftpPreview() },
      )
    }
  }

  // Quick Snippets Bottom Sheet
  if (showSnippetsSheet) {
    var sheetCategory by remember { mutableStateOf("All") }
    var sheetQuery by remember { mutableStateOf("") }
    val sheetCategories = remember(snippets) {
      listOf("All") + snippets.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    val sheetFiltered = remember(snippets, sheetCategory, sheetQuery) {
      val q = sheetQuery.trim()
      snippets.filter { s ->
        val catOk = sheetCategory == "All" || s.category.equals(sheetCategory, ignoreCase = true)
        val qOk = q.isEmpty() || s.title.contains(q, true) || s.command.contains(q, true)
        catOk && qOk
      }
    }
    ModalBottomSheet(onDismissRequest = { showSnippetsSheet = false }) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
      ) {
        Text(
          text = stringResource(R.string.snippets_sheet_title),
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = sheetQuery,
          onValueChange = { sheetQuery = it },
          placeholder = { Text("Filter snippets...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          sheetCategories.forEach { cat ->
            FilterChip(
              selected = sheetCategory == cat,
              onClick = { sheetCategory = cat },
              label = { Text(cat) },
            )
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
          modifier = Modifier.height(320.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(bottom = 24.dp),
        ) {
          items(sheetFiltered, key = { it.id }) { snippet ->
            Surface(
              onClick = {
                activeSession.sendLine(snippet.command)
                showSnippetsSheet = false
              },
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = snippet.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    text = snippet.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = snippet.command,
                  fontFamily = JetBrainsMonoFamily,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TerminalKeyChip(
  label: String,
  accent: Color,
  fg: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  active: Boolean = false,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = if (active) accent else fg.copy(alpha = 0.08f),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (active) accent else fg.copy(alpha = 0.18f),
    ),
    modifier = modifier.height(36.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.padding(horizontal = 11.dp),
    ) {
      Text(
        text = label,
        fontFamily = JetBrainsMonoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (active) Color(0xFF042217) else fg,
      )
    }
  }
}

@Composable
private fun HostKeyVerificationDialog(
  prompt: HostKeyPrompt,
  onAccept: () -> Unit,
  onReject: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onReject,
    icon = {
      Icon(
        imageVector = if (prompt.keyChangedWarning) Icons.Default.Warning else Icons.Default.Security,
        contentDescription = null,
        tint = if (prompt.keyChangedWarning) CoralError else EmeraldPhosphor,
      )
    },
    title = {
      Text(
        text = if (prompt.keyChangedWarning) "Host Key Changed!" else "Verify Host Fingerprint",
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (prompt.keyChangedWarning) {
          Text(
            text = "WARNING: The remote host key for ${prompt.hostname}:${prompt.port} does not match the previously trusted fingerprint. Only accept if you intentionally reinstalled or rotated the server.",
            style = MaterialTheme.typography.bodySmall,
            color = CoralError,
          )
        } else {
          Text(
            text = "First connection to ${prompt.hostname}:${prompt.port} (${prompt.keyType}). Confirm this server fingerprint before continuing:",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Text(
            text = prompt.fingerprintSha256,
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onAccept,
        colors = if (prompt.keyChangedWarning) {
          ButtonDefaults.buttonColors(containerColor = CoralError)
        } else {
          ButtonDefaults.buttonColors()
        },
        modifier = Modifier.testTag("trust_host_key_button"),
      ) {
        Text("Trust & Connect")
      }
    },
    dismissButton = {
      TextButton(onClick = onReject) {
        Text("Reject")
      }
    },
  )
}

@Composable
private fun CredentialPromptDialog(
  prompt: CredentialPrompt,
  onSubmit: (String) -> Unit,
  onCancel: () -> Unit,
) {
  var secret by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(prompt.title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = prompt.message,
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = secret,
          onValueChange = { secret = it },
          label = { Text(if (prompt.isPassphrase) "Key Passphrase" else "Password") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("credential_prompt_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmit(secret) },
        modifier = Modifier.testTag("credential_prompt_submit"),
      ) {
        Text("Authenticate")
      }
    },
    dismissButton = {
      TextButton(onClick = onCancel) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
private fun SftpExplorerSheet(
  sftpState: com.example.ssh.SftpUiState,
  onRefresh: () -> Unit,
  onNavigateUp: () -> Unit,
  onOpenEntry: (SftpEntry) -> Unit,
  onDeleteEntry: (SftpEntry) -> Unit,
  onCreateFolder: (String) -> Unit,
  onDismissPreview: () -> Unit,
) {
  var showNewFolderDialog by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.sftp_browser),
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = sftpState.currentPath,
          fontFamily = JetBrainsMonoFamily,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row {
        IconButton(onClick = onNavigateUp) {
          Icon(Icons.Default.ArrowUpward, contentDescription = "Parent directory")
        }
        IconButton(onClick = { showNewFolderDialog = true }) {
          Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
        }
        IconButton(onClick = onRefresh) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh directory")
        }
      }
    }

    sftpState.error?.let { err ->
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = err,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (sftpState.isLoading) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(220.dp),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(360.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(sftpState.entries, key = { it.fullPath }) { entry ->
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenEntry(entry) },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = entry.filename,
                  fontFamily = JetBrainsMonoFamily,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${entry.permissions} • ${if (entry.isDirectory) "Directory" else "${entry.sizeBytes} B"}",
                  fontFamily = JetBrainsMonoFamily,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(onClick = { onDeleteEntry(entry) }) {
                Icon(
                  imageVector = Icons.Default.DeleteOutline,
                  contentDescription = "Delete remote item",
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }
    }
  }

  if (showNewFolderDialog) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showNewFolderDialog = false },
      title = { Text("Create Remote Folder") },
      text = {
        OutlinedTextField(
          value = folderName,
          onValueChange = { folderName = it },
          label = { Text("Folder name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onCreateFolder(folderName)
            showNewFolderDialog = false
          },
        ) {
          Text("Create")
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewFolderDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  if (sftpState.previewFileName != null && sftpState.previewContent != null) {
    AlertDialog(
      onDismissRequest = onDismissPreview,
      title = {
        Text(
          text = sftpState.previewFileName,
          fontFamily = JetBrainsMonoFamily,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      text = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        ) {
          SelectionContainer {
            Text(
              text = sftpState.previewContent,
              fontFamily = JetBrainsMonoFamily,
              fontSize = 12.sp,
            )
          }
        }
      },
      confirmButton = {
        OutlinedButton(onClick = onDismissPreview) {
          Text("Close")
        }
      },
    )
  }
}

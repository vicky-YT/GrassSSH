package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AiChatMessage
import com.example.ai.AiSystemUiState
import com.example.ai.BuildPlanStep
import com.example.ai.BuildPlanUiState
import com.example.ai.ConsoleFixPlan
import com.example.ai.ConsoleFixUiState
import com.example.ai.GeneratedBuildPlan
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPhosphor
import com.example.ui.theme.JetBrainsMonoFamily

private val AI_QUICK_PROMPTS = listOf(
  "Explain output" to "Explain the attached shell session output clearly, highlight any errors or warnings, and tell me what it means.",
  "Suggest command" to "Based on my current shell session output, suggest the most helpful next Linux/SSH commands to run.",
  "Fix SSH error" to "Diagnose the error in my current session output and give me exact fix commands.",
  "Server health check" to "Give me safe one-liner Linux commands to check CPU, RAM, disk space, and top processes.",
)

private val BUILD_PRESET_PROMPTS = listOf(
  "Node.js API" to "Create a Node.js Express hello-world API server in ~/myapp on port 3000 with npm init and start it in background",
  "Python FastAPI" to "Create a Python virtual environment in ~/api with FastAPI and Uvicorn serving a health endpoint on port 8000",
  "Nginx Proxy" to "Install Nginx and configure a reverse proxy site forwarding port 80 to localhost:3000",
  "Backup Script" to "Write an executable bash script ~/backup.sh that archives /var/www with timestamp and prints archive size",
)

enum class AiPanelMode {
  ASK,
  BUILD,
  FIX,
}

@Composable
fun GrassSshAiScreen(
  messages: List<AiChatMessage>,
  aiUiState: AiSystemUiState,
  buildPlanState: BuildPlanUiState,
  consoleFixState: ConsoleFixUiState,
  customAiInstructions: String,
  activeSessionLabel: String?,
  hasActiveSession: Boolean,
  includeTerminalContext: Boolean,
  onToggleIncludeContext: (Boolean) -> Unit,
  onSendPrompt: (String) -> Unit,
  onClearChat: () -> Unit,
  onGenerateBuildPlan: (String) -> Unit,
  onClearBuildPlan: () -> Unit,
  onDiagnoseConsoleError: (String?) -> Unit,
  onClearConsoleFix: () -> Unit,
  onRunCommand: (String) -> Unit,
  onRunBuildPlan: (GeneratedBuildPlan) -> Unit,
  onRunFixPlan: (ConsoleFixPlan) -> Unit,
  onSaveBuildPlanAsSnippet: (GeneratedBuildPlan) -> Unit,
  onSaveCustomInstructions: (String) -> Unit,
  onNotify: (String) -> Unit,
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .imePadding(),
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      GrassSshAiPanel(
        messages = messages,
        aiUiState = aiUiState,
        buildPlanState = buildPlanState,
        consoleFixState = consoleFixState,
        customAiInstructions = customAiInstructions,
        activeSessionLabel = activeSessionLabel,
        hasActiveSession = hasActiveSession,
        includeTerminalContext = includeTerminalContext,
        onToggleIncludeContext = onToggleIncludeContext,
        onSendPrompt = onSendPrompt,
        onClearChat = onClearChat,
        onGenerateBuildPlan = onGenerateBuildPlan,
        onClearBuildPlan = onClearBuildPlan,
        onDiagnoseConsoleError = onDiagnoseConsoleError,
        onClearConsoleFix = onClearConsoleFix,
        onRunCommand = onRunCommand,
        onRunBuildPlan = onRunBuildPlan,
        onRunFixPlan = onRunFixPlan,
        onSaveBuildPlanAsSnippet = onSaveBuildPlanAsSnippet,
        onSaveCustomInstructions = onSaveCustomInstructions,
        onNotify = onNotify,
        onNavigateBack = onNavigateBack,
        hasTerminalContextSource = activeSessionLabel != null,
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 720.dp),
      )
    }
  }
}

@Composable
fun GrassSshAiPanel(
  messages: List<AiChatMessage>,
  aiUiState: AiSystemUiState,
  buildPlanState: BuildPlanUiState,
  consoleFixState: ConsoleFixUiState,
  customAiInstructions: String,
  activeSessionLabel: String?,
  hasActiveSession: Boolean,
  includeTerminalContext: Boolean,
  onToggleIncludeContext: (Boolean) -> Unit,
  onSendPrompt: (String) -> Unit,
  onClearChat: () -> Unit,
  onGenerateBuildPlan: (String) -> Unit,
  onClearBuildPlan: () -> Unit,
  onDiagnoseConsoleError: (String?) -> Unit,
  onClearConsoleFix: () -> Unit,
  onRunCommand: (String) -> Unit,
  onRunBuildPlan: (GeneratedBuildPlan) -> Unit,
  onRunFixPlan: (ConsoleFixPlan) -> Unit,
  onSaveBuildPlanAsSnippet: (GeneratedBuildPlan) -> Unit,
  onSaveCustomInstructions: (String) -> Unit,
  onNotify: (String) -> Unit,
  onNavigateBack: (() -> Unit)? = null,
  initialMode: AiPanelMode = AiPanelMode.BUILD,
  forcedMode: AiPanelMode? = null,
  modeTriggerKey: Int = 0,
  hasTerminalContextSource: Boolean = hasActiveSession,
  terminalContextPreview: String? = null,
  titleOverride: String? = null,
  isExpanded: Boolean = false,
  onToggleExpand: (() -> Unit)? = null,
  onCollapse: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var mode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
  var promptInput by rememberSaveable { mutableStateOf("") }
  var buildPromptInput by rememberSaveable { mutableStateOf("") }
  var fixHintInput by rememberSaveable { mutableStateOf("") }
  var showCustomizeDialog by remember { mutableStateOf(false) }
  var pendingConfirmPlan by remember { mutableStateOf<GeneratedBuildPlan?>(null) }
  var pendingConfirmFixPlan by remember { mutableStateOf<ConsoleFixPlan?>(null) }

  LaunchedEffect(modeTriggerKey, forcedMode) {
    if (modeTriggerKey > 0 && forcedMode != null) {
      mode = forcedMode
    }
  }

  val listState = rememberLazyListState()
  val isChatBusy = aiUiState is AiSystemUiState.Loading || aiUiState is AiSystemUiState.Streaming
  val isBuildBusy = buildPlanState is BuildPlanUiState.Generating
  val isFixBusy = consoleFixState is ConsoleFixUiState.Diagnosing

  LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
    if (messages.isNotEmpty() && mode == AiPanelMode.ASK) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Column(modifier = modifier) {
    // Branded GrassSSH AI System Header
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
              MaterialTheme.colorScheme.surface,
            )
          )
        )
        .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          if (onNavigateBack != null) {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer)
              .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = titleOverride ?: stringResource(R.string.ai_system_name),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = if (customAiInstructions.isNotBlank()) "Customized • ${stringResource(R.string.ai_system_subtitle)}"
              else stringResource(R.string.ai_system_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { showCustomizeDialog = true },
            modifier = Modifier.testTag("customize_ai_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = "Customize GrassSSH AI System",
              tint = if (customAiInstructions.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if ((mode == AiPanelMode.ASK && messages.isNotEmpty()) ||
            (mode == AiPanelMode.BUILD && buildPlanState !is BuildPlanUiState.Idle) ||
            (mode == AiPanelMode.FIX && consoleFixState !is ConsoleFixUiState.Idle)
          ) {
            IconButton(
              onClick = {
                when (mode) {
                  AiPanelMode.ASK -> onClearChat()
                  AiPanelMode.BUILD -> onClearBuildPlan()
                  AiPanelMode.FIX -> onClearConsoleFix()
                }
              },
            ) {
              Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = "Clear",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (onToggleExpand != null) {
            IconButton(
              onClick = onToggleExpand,
              modifier = Modifier.testTag("ai_panel_expand_button"),
            ) {
              Icon(
                imageVector = if (isExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                contentDescription = if (isExpanded) "Restore split view" else "Expand AI panel",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (onCollapse != null) {
            IconButton(
              onClick = onCollapse,
              modifier = Modifier.testTag("ai_panel_close_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close GrassSSH AI panel",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    // Mode Switcher + Context Chip
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilterChip(
        selected = mode == AiPanelMode.FIX,
        onClick = { mode = AiPanelMode.FIX },
        leadingIcon = {
          Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        label = { Text("Fix Console") },
        modifier = Modifier.testTag("ai_mode_fix"),
      )
      FilterChip(
        selected = mode == AiPanelMode.BUILD,
        onClick = { mode = AiPanelMode.BUILD },
        leadingIcon = {
          Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        label = { Text("Pro Build") },
        modifier = Modifier.testTag("ai_mode_build"),
      )
      FilterChip(
        selected = mode == AiPanelMode.ASK,
        onClick = { mode = AiPanelMode.ASK },
        leadingIcon = {
          Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        label = { Text("Ask AI") },
        modifier = Modifier.testTag("ai_mode_ask"),
      )
      if (hasTerminalContextSource && activeSessionLabel != null) {
        FilterChip(
          selected = includeTerminalContext,
          onClick = { onToggleIncludeContext(!includeTerminalContext) },
          leadingIcon = {
            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          label = {
            Text(
              text = if (includeTerminalContext) "Shell Output: $activeSessionLabel" else "Attach Shell Output",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          modifier = Modifier.testTag("ai_context_toggle"),
        )
      }
    }

    if ((mode == AiPanelMode.ASK || mode == AiPanelMode.FIX) &&
      includeTerminalContext && !terminalContextPreview.isNullOrBlank()
    ) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 2.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = EmeraldPhosphor,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = terminalContextPreview,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    if (mode == AiPanelMode.FIX) {
      // AUTO-DIAGNOSE & FIX CONSOLE ERROR MODE
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Console Error Auto-Fixer",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = "Inspects your live SSH shell output for errors (missing tools, ports in use, permissions, apt/npm/nginx failures) and builds a verified fix.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = fixHintInput,
              onValueChange = { fixHintInput = it },
              placeholder = { Text("Optional: describe what failed or what you were running...") },
              minLines = 1,
              maxLines = 3,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("fix_hint_input"),
            )
            Button(
              onClick = {
                if (!isFixBusy) {
                  onDiagnoseConsoleError(fixHintInput.takeIf { it.isNotBlank() })
                }
              },
              enabled = !isFixBusy && hasTerminalContextSource,
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("diagnose_console_error_button"),
            ) {
              if (isFixBusy) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyzing Console Output...")
              } else {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (hasTerminalContextSource) "Diagnose & Generate Safe Fix" else "Open an SSH Session First")
              }
            }
          }
        }

        when (consoleFixState) {
          is ConsoleFixUiState.Idle -> Unit
          is ConsoleFixUiState.Diagnosing -> Unit
          is ConsoleFixUiState.Error -> {
            Surface(
              color = CoralError.copy(alpha = 0.14f),
              shape = MaterialTheme.shapes.small,
              border = androidx.compose.foundation.BorderStroke(1.dp, CoralError.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CoralError)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = consoleFixState.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = CoralError,
                )
              }
            }
          }
          is ConsoleFixUiState.Ready -> {
            val fix = consoleFixState.fixPlan
            ConsoleFixCard(
              plan = fix,
              hasActiveSession = hasActiveSession,
              onRunStep = onRunCommand,
              onRunAll = {
                if (fix.requiresSudo || fix.fixSteps.any { it.isDestructive }) {
                  pendingConfirmFixPlan = fix
                } else {
                  onRunFixPlan(fix)
                }
              },
              onCopyAll = {
                val fullScript = fix.fixSteps.joinToString("\n") { it.command }
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText(fix.errorTitle, fullScript))
                onNotify("Copied fix script")
              },
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    } else if (mode == AiPanelMode.BUILD) {
      // PROMPT-TO-BUILD MODE
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Describe what to build or configure",
              style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
              value = buildPromptInput,
              onValueChange = { buildPromptInput = it },
              placeholder = {
                Text("e.g. Create a Node.js API on port 3000 and run it, or setup Nginx reverse proxy...")
              },
              minLines = 2,
              maxLines = 4,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("build_prompt_input"),
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              BUILD_PRESET_PROMPTS.forEach { (label, presetPrompt) ->
                AssistChip(
                  onClick = { buildPromptInput = presetPrompt },
                  label = { Text(label) },
                )
              }
            }
            Button(
              onClick = {
                if (buildPromptInput.isNotBlank() && !isBuildBusy) {
                  onGenerateBuildPlan(buildPromptInput)
                }
              },
              enabled = buildPromptInput.isNotBlank() && !isBuildBusy,
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("generate_build_plan_button"),
            ) {
              if (isBuildBusy) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generating Build Plan...")
              } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Build Plan")
              }
            }
          }
        }

        when (buildPlanState) {
          is BuildPlanUiState.Idle -> {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Give any prompt above and GrassSSH AI System will generate a step-by-step bash build plan you can run directly on your VPS over SSH or save as a Snippet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
              )
            }
          }
          is BuildPlanUiState.Generating -> Unit
          is BuildPlanUiState.Error -> {
            Surface(
              color = CoralError.copy(alpha = 0.14f),
              shape = MaterialTheme.shapes.small,
              border = androidx.compose.foundation.BorderStroke(1.dp, CoralError.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CoralError)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = buildPlanState.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = CoralError,
                )
              }
            }
          }
          is BuildPlanUiState.Ready -> {
            val plan = buildPlanState.plan
            BuildPlanCard(
              plan = plan,
              hasActiveSession = hasActiveSession,
              onRunStep = onRunCommand,
              onRunAll = {
                if (plan.requiresSudo || plan.steps.any { it.isDestructive }) {
                  pendingConfirmPlan = plan
                } else {
                  onRunBuildPlan(plan)
                }
              },
              onSaveSnippet = { onSaveBuildPlanAsSnippet(plan) },
              onCopyAll = {
                val fullScript = plan.steps.joinToString("\n") { it.command }
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText(plan.planTitle, fullScript))
                onNotify("Copied full build script")
              },
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    } else {
      // ASK AI STREAMING CHAT MODE
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AI_QUICK_PROMPTS.forEach { (label, fullPrompt) ->
          AssistChip(
            onClick = {
              if (!isChatBusy) onSendPrompt(fullPrompt)
            },
            label = { Text(label) },
          )
        }
      }

      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .testTag("ai_chat_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (messages.isEmpty()) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(8.dp)
                      .clip(CircleShape)
                      .background(EmeraldPhosphor),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "GrassSSH AI System Ready",
                    style = MaterialTheme.typography.titleSmall,
                  )
                }
                Text(
                  text = "Ask for Linux commands, troubleshoot VPS connection errors, inspect live JSch terminal logs, or switch to 'Build from Prompt' to generate & run multi-step setups.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        items(messages, key = { it.id }) { msg ->
          AiMessageBubble(
            message = msg,
            hasActiveSession = hasActiveSession,
            onRunCommand = onRunCommand,
            onCopyText = { textToCopy ->
              val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clip.setPrimaryClip(ClipData.newPlainText("GrassSSH AI Command", textToCopy))
              onNotify("Copied to clipboard")
            },
          )
        }

        if (aiUiState is AiSystemUiState.Loading) {
          item(key = "ai_loading_indicator") {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(vertical = 8.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "GrassSSH AI System is thinking...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        if (aiUiState is AiSystemUiState.Error) {
          item(key = "ai_error_banner") {
            Surface(
              color = CoralError.copy(alpha = 0.14f),
              shape = MaterialTheme.shapes.small,
              border = androidx.compose.foundation.BorderStroke(1.dp, CoralError.copy(alpha = 0.5f)),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_error_banner"),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CoralError)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = aiUiState.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = CoralError,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      // Chat Prompt Input Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = promptInput,
          onValueChange = { promptInput = it },
          placeholder = {
            Text(
              text = stringResource(R.string.ai_prompt_hint),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          maxLines = 3,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              val trimmed = promptInput.trim()
              if (trimmed.isNotEmpty() && !isChatBusy) {
                onSendPrompt(trimmed)
                promptInput = ""
              }
            },
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("ai_prompt_input"),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          onClick = {
            val trimmed = promptInput.trim()
            if (trimmed.isNotEmpty() && !isChatBusy) {
              onSendPrompt(trimmed)
              promptInput = ""
            }
          },
          shape = RoundedCornerShape(12.dp),
          color = if (!isChatBusy && promptInput.isNotBlank()) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          },
          modifier = Modifier
            .size(48.dp)
            .testTag("ai_send_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Ask GrassSSH AI System",
              tint = if (!isChatBusy && promptInput.isNotBlank()) {
                MaterialTheme.colorScheme.onPrimary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }

  // Customize GrassSSH AI System Instructions Dialog
  if (showCustomizeDialog) {
    var draft by remember(customAiInstructions) { mutableStateOf(customAiInstructions) }
    AlertDialog(
      onDismissRequest = { showCustomizeDialog = false },
      title = { Text("Customize GrassSSH AI System") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Set server defaults or rules (e.g., OS distro, Docker/PM2 preference, or response language) applied to every prompt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Custom AI Instructions") },
            placeholder = { Text("Ubuntu 24.04 VPS, prefer Docker Compose, answer concisely") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("custom_ai_instructions_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveCustomInstructions(draft)
            showCustomizeDialog = false
          },
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCustomizeDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Confirmation dialog before running privileged/destructive build plans
  pendingConfirmPlan?.let { plan ->
    AlertDialog(
      onDismissRequest = { pendingConfirmPlan = null },
      icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning) },
      title = { Text("Execute Build Plan on VPS?") },
      text = {
        Text(
          text = "This plan (${plan.steps.size} steps) uses elevated privileges or modifies system configuration. It runs sequentially with fail-fast safety (set -e).",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRunBuildPlan(plan)
            pendingConfirmPlan = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
          Text("Build & Run Safely")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingConfirmPlan = null }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  pendingConfirmFixPlan?.let { fixPlan ->
    AlertDialog(
      onDismissRequest = { pendingConfirmFixPlan = null },
      icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning) },
      title = { Text("Apply Console Fix on VPS?") },
      text = {
        Text(
          text = "This fix (${fixPlan.fixSteps.size} steps) uses elevated privileges or modifies system state. Steps execute sequentially with fail-fast protection (set -e).",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRunFixPlan(fixPlan)
            pendingConfirmFixPlan = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
          Text("Apply Auto-Fix")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingConfirmFixPlan = null }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}

@Composable
private fun BuildPlanCard(
  plan: GeneratedBuildPlan,
  hasActiveSession: Boolean,
  onRunStep: (String) -> Unit,
  onRunAll: () -> Unit,
  onSaveSnippet: () -> Unit,
  onCopyAll: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("generated_build_plan_card"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = plan.planTitle,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        if (plan.requiresSudo) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = AmberWarning.copy(alpha = 0.16f),
          ) {
            Text(
              text = "SUDO",
              style = MaterialTheme.typography.labelSmall,
              color = AmberWarning,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
          }
        }
      }

      if (plan.summary.isNotBlank()) {
        Text(
          text = plan.summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (plan.environmentAssumptions.isNotBlank()) {
        Text(
          text = "Env: ${plan.environmentAssumptions}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      plan.steps.forEachIndexed { index, step ->
        PlanStepRow(
          index = index,
          step = step,
          hasActiveSession = hasActiveSession,
          onRunStep = onRunStep,
        )
      }

      if (plan.verificationCommand.isNotBlank()) {
        VerificationCommandBlock(
          command = plan.verificationCommand,
          hasActiveSession = hasActiveSession,
          onRun = { onRunStep(plan.verificationCommand) },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onCopyAll,
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Copy", maxLines = 1)
        }
        OutlinedButton(
          onClick = onSaveSnippet,
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Snippet", maxLines = 1)
        }
      }

      Button(
        onClick = onRunAll,
        enabled = hasActiveSession,
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("run_build_plan_button"),
      ) {
        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          if (hasActiveSession) "Build & Run Safely (set -e)"
          else "Connect to a VPS Host to Run",
        )
      }
    }
  }
}

@Composable
private fun ConsoleFixCard(
  plan: ConsoleFixPlan,
  hasActiveSession: Boolean,
  onRunStep: (String) -> Unit,
  onRunAll: () -> Unit,
  onCopyAll: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPhosphor.copy(alpha = 0.55f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("console_fix_card"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = plan.errorTitle,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = EmeraldPhosphor.copy(alpha = 0.16f),
          ) {
            Text(
              text = "${plan.confidence} Confidence",
              style = MaterialTheme.typography.labelSmall,
              color = EmeraldPhosphor,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
          }
          if (plan.requiresSudo) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = AmberWarning.copy(alpha = 0.16f),
            ) {
              Text(
                text = "SUDO",
                style = MaterialTheme.typography.labelSmall,
                color = AmberWarning,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
              )
            }
          }
        }
      }

      if (plan.rootCause.isNotBlank()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = CoralError.copy(alpha = 0.09f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "Root Cause",
              style = MaterialTheme.typography.labelSmall,
              color = CoralError,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = plan.rootCause,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      plan.fixSteps.forEachIndexed { index, step ->
        PlanStepRow(
          index = index,
          step = step,
          hasActiveSession = hasActiveSession,
          onRunStep = onRunStep,
        )
      }

      if (plan.verificationCommand.isNotBlank()) {
        VerificationCommandBlock(
          command = plan.verificationCommand,
          hasActiveSession = hasActiveSession,
          onRun = { onRunStep(plan.verificationCommand) },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onCopyAll,
          modifier = Modifier.weight(0.38f),
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Copy", maxLines = 1)
        }
        Button(
          onClick = onRunAll,
          enabled = hasActiveSession,
          modifier = Modifier
            .weight(0.62f)
            .minimumInteractiveComponentSize()
            .testTag("apply_console_fix_button"),
        ) {
          Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (hasActiveSession) "Auto-Fix Safely" else "Connect Session", maxLines = 1)
        }
      }
    }
  }
}

@Composable
private fun PlanStepRow(
  index: Int,
  step: BuildPlanStep,
  hasActiveSession: Boolean,
  onRunStep: (String) -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    border = if (step.isDestructive) {
      androidx.compose.foundation.BorderStroke(1.dp, CoralError.copy(alpha = 0.45f))
    } else {
      null
    },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${index + 1}. ${step.title}",
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.weight(1f),
        )
        if (hasActiveSession) {
          TextButton(
            onClick = { onRunStep(step.command) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("Run", fontSize = 12.sp)
          }
        }
      }
      if (step.explanation.isNotBlank()) {
        Text(
          text = step.explanation,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.background)
          .padding(8.dp),
      ) {
        SelectionContainer {
          Text(
            text = step.command,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
private fun VerificationCommandBlock(
  command: String,
  hasActiveSession: Boolean,
  onRun: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = EmeraldPhosphor.copy(alpha = 0.08f),
    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPhosphor.copy(alpha = 0.35f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = null,
        tint = EmeraldPhosphor,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Verification Check",
          style = MaterialTheme.typography.labelSmall,
          color = EmeraldPhosphor,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = command,
          fontFamily = JetBrainsMonoFamily,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      if (hasActiveSession) {
        TextButton(onClick = onRun) {
          Text("Verify", fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
private fun AiMessageBubble(
  message: AiChatMessage,
  hasActiveSession: Boolean,
  onRunCommand: (String) -> Unit,
  onCopyText: (String) -> Unit,
) {
  val isUser = message.isUser
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
      ),
      color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
      border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.widthIn(max = 580.dp),
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        if (!isUser) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp),
          ) {
            Text(
              text = stringResource(R.string.ai_system_name),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
            if (!message.isStreaming && message.text.isNotBlank()) {
              IconButton(
                onClick = { onCopyText(message.text) },
                modifier = Modifier.size(24.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = "Copy response",
                  modifier = Modifier.size(14.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        SelectionContainer {
          Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
          )
        }

        if (!isUser && message.extractedCommands.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            message.extractedCommands.forEach { cmd ->
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = cmd,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(
                    onClick = { onCopyText(cmd) },
                    modifier = Modifier.size(32.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.ContentCopy,
                      contentDescription = "Copy command",
                      modifier = Modifier.size(15.dp),
                    )
                  }
                  if (hasActiveSession) {
                    TextButton(
                      onClick = { onRunCommand(cmd) },
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(2.dp))
                      Text("Run", fontSize = 12.sp)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

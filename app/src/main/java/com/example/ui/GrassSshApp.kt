package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.screens.GrassSshAiScreen
import com.example.ui.screens.HostsScreen
import com.example.ui.screens.KeysScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SnippetsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.viewmodel.MainViewModel

object GrassRoutes {
  const val HOSTS = "hosts"
  const val TERMINAL = "terminal"
  const val KEYS = "keys"
  const val SNIPPETS = "snippets"
  const val SETTINGS = "settings"
  const val AI = "grassssh_ai_system"
}

private data class NavTab(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val NAV_TABS = listOf(
  NavTab(GrassRoutes.HOSTS, R.string.nav_hosts, Icons.Filled.Dns, Icons.Outlined.Dns, "nav_hosts"),
  NavTab(GrassRoutes.TERMINAL, R.string.nav_terminal, Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_terminal"),
  NavTab(GrassRoutes.KEYS, R.string.nav_keys, Icons.Filled.Key, Icons.Outlined.Key, "nav_keys"),
  NavTab(GrassRoutes.SNIPPETS, R.string.nav_snippets, Icons.Filled.Code, Icons.Outlined.Code, "nav_snippets"),
  NavTab(GrassRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings"),
)

@Composable
fun GrassSshApp(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: GrassRoutes.HOSTS

  val hosts by viewModel.hosts.collectAsStateWithLifecycle()
  val keys by viewModel.keys.collectAsStateWithLifecycle()
  val knownHosts by viewModel.knownHosts.collectAsStateWithLifecycle()
  val snippets by viewModel.snippets.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
  val probeResults by viewModel.probeResults.collectAsStateWithLifecycle()
  val probingHostIds by viewModel.probingHostIds.collectAsStateWithLifecycle()
  val isGeneratingKey by viewModel.isGeneratingKey.collectAsStateWithLifecycle()
  val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
  val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
  val buildPlanState by viewModel.buildPlanState.collectAsStateWithLifecycle()
  val consoleFixState by viewModel.consoleFixState.collectAsStateWithLifecycle()
  val includeAiTerminalContext by viewModel.includeAiTerminalContext.collectAsStateWithLifecycle()

  val activeSession = sessions.firstOrNull { it.sessionId == activeSessionId } ?: sessions.lastOrNull()
  val activeSessionLabel = activeSession?.let {
    "${it.hostConfig.username}@${it.hostConfig.hostname}"
  }

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) {
    viewModel.uiMessages.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  fun navigateToTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useNavigationRail) {
        NavigationRail(
          containerColor = MaterialTheme.colorScheme.surface,
        ) {
          NAV_TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationRailItem(
              selected = selected,
              onClick = { navigateToTab(tab.route) },
              icon = {
                TabIconWithBadge(
                  tab = tab,
                  selected = selected,
                  sessionCount = sessions.size,
                )
              },
              label = { Text(stringResource(tab.labelRes)) },
              modifier = Modifier.testTag(tab.testTag),
            )
          }
        }
      }

      Scaffold(
        modifier = Modifier.weight(1f),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
          if (!useNavigationRail && currentRoute != GrassRoutes.AI) {
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
            ) {
              NAV_TABS.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateToTab(tab.route) },
                  icon = {
                    TabIconWithBadge(
                      tab = tab,
                      selected = selected,
                      sessionCount = sessions.size,
                    )
                  },
                  label = { Text(stringResource(tab.labelRes)) },
                  alwaysShowLabel = true,
                  modifier = Modifier.testTag(tab.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        NavHost(
          navController = navController,
          startDestination = GrassRoutes.HOSTS,
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding),
        ) {
          composable(GrassRoutes.HOSTS) {
            HostsScreen(
              hosts = hosts,
              keys = keys,
              activeSessionCount = sessions.size,
              probeResults = probeResults,
              probingHostIds = probingHostIds,
              onQuickConnect = { target ->
                viewModel.quickConnect(target) {
                  navigateToTab(GrassRoutes.TERMINAL)
                }
              },
              onConnectHost = { host ->
                viewModel.connectHost(host) {
                  navigateToTab(GrassRoutes.TERMINAL)
                }
              },
              onSaveHost = viewModel::saveHost,
              onDeleteHost = viewModel::deleteHost,
              onProbeHost = viewModel::probeHost,
              onOpenTerminal = { navigateToTab(GrassRoutes.TERMINAL) },
              onOpenAiSystem = { navController.navigate(GrassRoutes.AI) },
            )
          }

          composable(GrassRoutes.TERMINAL) {
            TerminalScreen(
              sessions = sessions,
              activeSessionId = activeSessionId,
              settings = settings,
              snippets = snippets,
              aiMessages = aiMessages,
              aiUiState = aiUiState,
              buildPlanState = buildPlanState,
              consoleFixState = consoleFixState,
              includeAiTerminalContext = includeAiTerminalContext,
              onToggleIncludeAiContext = viewModel::setIncludeAiTerminalContext,
              onSendAiPrompt = viewModel::sendAiPrompt,
              onClearAiChat = viewModel::clearAiChat,
              onGenerateBuildPlan = viewModel::generateBuildPlan,
              onClearBuildPlan = viewModel::clearBuildPlan,
              onDiagnoseConsoleError = viewModel::diagnoseConsoleError,
              onClearConsoleFix = viewModel::clearConsoleFix,
              onRunBuildPlan = { plan ->
                viewModel.executeFullBuildPlan(plan) { }
              },
              onRunFixPlan = { plan ->
                viewModel.executeFixPlan(plan) { }
              },
              onSaveBuildPlanAsSnippet = viewModel::saveBuildPlanAsSnippet,
              onSaveCustomAiInstructions = viewModel::setCustomAiInstructions,
              onSelectSession = viewModel::selectSession,
              onCloseSession = viewModel::closeSession,
              onChangeFontSize = viewModel::setFontSizeSp,
              onNavigateToHosts = { navigateToTab(GrassRoutes.HOSTS) },
              onNotify = viewModel::notifyMessage,
            )
          }

          composable(GrassRoutes.KEYS) {
            KeysScreen(
              keys = keys,
              isGeneratingKey = isGeneratingKey,
              onGenerateKey = viewModel::generateKey,
              onImportKey = viewModel::importKey,
              onDeleteKey = viewModel::deleteKey,
              onNotify = viewModel::notifyMessage,
            )
          }

          composable(GrassRoutes.SNIPPETS) {
            SnippetsScreen(
              snippets = snippets,
              hasActiveSession = sessions.isNotEmpty(),
              onSaveSnippet = viewModel::saveSnippet,
              onDeleteSnippet = viewModel::deleteSnippet,
              onRunSnippet = { snippet ->
                viewModel.runSnippetInActiveSession(snippet) {
                  navigateToTab(GrassRoutes.TERMINAL)
                }
              },
              onNotify = viewModel::notifyMessage,
            )
          }

          composable(GrassRoutes.SETTINGS) {
            SettingsScreen(
              settings = settings,
              knownHosts = knownHosts,
              onSelectTheme = viewModel::setThemePreset,
              onFontSizeChange = viewModel::setFontSizeSp,
              onKeepScreenOnChange = viewModel::setKeepScreenOn,
              onVibrateOnKeyChange = viewModel::setVibrateOnKey,
              onDefaultUsernameChange = viewModel::setDefaultUsername,
              onForgetKnownHost = viewModel::forgetKnownHost,
            )
          }

          composable(GrassRoutes.AI) {
            GrassSshAiScreen(
              messages = aiMessages,
              aiUiState = aiUiState,
              buildPlanState = buildPlanState,
              consoleFixState = consoleFixState,
              customAiInstructions = settings.customAiInstructions,
              activeSessionLabel = activeSessionLabel,
              hasActiveSession = sessions.isNotEmpty(),
              includeTerminalContext = includeAiTerminalContext,
              onToggleIncludeContext = viewModel::setIncludeAiTerminalContext,
              onSendPrompt = viewModel::sendAiPrompt,
              onClearChat = viewModel::clearAiChat,
              onGenerateBuildPlan = viewModel::generateBuildPlan,
              onClearBuildPlan = viewModel::clearBuildPlan,
              onDiagnoseConsoleError = viewModel::diagnoseConsoleError,
              onClearConsoleFix = viewModel::clearConsoleFix,
              onRunCommand = { cmd ->
                viewModel.runCommandInActiveSession(cmd) {
                  navigateToTab(GrassRoutes.TERMINAL)
                }
              },
              onRunBuildPlan = { plan ->
                viewModel.executeFullBuildPlan(plan) {
                  navigateToTab(GrassRoutes.TERMINAL)
                }
              },
              onRunFixPlan = { plan ->
                viewModel.executeFixPlan(plan) {
                  navigateToTab(GrassRoutes.TERMINAL)
                }
              },
              onSaveBuildPlanAsSnippet = viewModel::saveBuildPlanAsSnippet,
              onSaveCustomInstructions = viewModel::setCustomAiInstructions,
              onNotify = viewModel::notifyMessage,
              onNavigateBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TabIconWithBadge(
  tab: NavTab,
  selected: Boolean,
  sessionCount: Int,
) {
  val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
  val desc = stringResource(tab.labelRes)
  if (tab.route == GrassRoutes.TERMINAL && sessionCount > 0) {
    BadgedBox(
      badge = {
        Badge {
          Text(sessionCount.toString())
        }
      },
    ) {
      Icon(imageVector = icon, contentDescription = desc)
    }
  } else {
    Icon(imageVector = icon, contentDescription = desc)
  }
}

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AuthType
import com.example.data.model.SshHostEntity
import com.example.data.model.SshKeyEntity
import com.example.ssh.TcpProbeResult
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPhosphor
import com.example.ui.theme.JetBrainsMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HOST_GROUPS = listOf("All", "General", "Production", "Homelab", "Cloud")

@Composable
fun HostsScreen(
  hosts: List<SshHostEntity>,
  keys: List<SshKeyEntity>,
  activeSessionCount: Int,
  probeResults: Map<Long, TcpProbeResult>,
  probingHostIds: Set<Long>,
  onQuickConnect: (String) -> Unit,
  onConnectHost: (SshHostEntity) -> Unit,
  onSaveHost: (SshHostEntity) -> Unit,
  onDeleteHost: (SshHostEntity) -> Unit,
  onProbeHost: (SshHostEntity) -> Unit,
  onOpenTerminal: () -> Unit,
  onOpenAiSystem: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var quickTarget by rememberSaveable { mutableStateOf("") }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var selectedGroup by rememberSaveable { mutableStateOf("All") }
  var editingHost by remember { mutableStateOf<SshHostEntity?>(null) }
  var showNewHostDialog by remember { mutableStateOf(false) }

  val filteredHosts = remember(hosts, searchQuery, selectedGroup) {
    hosts.filter { host ->
      val matchesGroup = selectedGroup == "All" || host.groupName.equals(selectedGroup, ignoreCase = true)
      val q = searchQuery.trim().lowercase()
      val matchesQuery = q.isEmpty() ||
        host.alias.lowercase().contains(q) ||
        host.hostname.lowercase().contains(q) ||
        host.username.lowercase().contains(q)
      matchesGroup && matchesQuery
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showNewHostDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("add_host_fab"),
      ) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_host))
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 720.dp),
      ) {
        // Branded GrassSSH Hero Header
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                  Color.Transparent,
                )
              )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.primaryContainer)
                  .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = ">_",
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                  text = stringResource(R.string.hosts_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Surface(
                onClick = onOpenAiSystem,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("open_ai_system_button"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                  )
                }
              }

              AnimatedVisibility(visible = activeSessionCount > 0) {
                Surface(
                  onClick = onOpenTerminal,
                  shape = RoundedCornerShape(999.dp),
                  color = MaterialTheme.colorScheme.primaryContainer,
                  border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("active_sessions_pill"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Box(
                      modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldPhosphor),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "$activeSessionCount Live",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
              }
            }
          }
        }

        // Quick Connect Bar
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
          ),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          shape = MaterialTheme.shapes.medium,
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(bottom = 8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Quick Connect",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              OutlinedTextField(
                value = quickTarget,
                onValueChange = { quickTarget = it },
                placeholder = {
                  Text(
                    text = stringResource(R.string.quick_connect_hint),
                    fontFamily = JetBrainsMonoFamily,
                  )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Uri,
                  imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                  onGo = { onQuickConnect(quickTarget) },
                ),
                trailingIcon = {
                  if (quickTarget.isNotEmpty()) {
                    IconButton(onClick = { quickTarget = "" }) {
                      Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("quick_connect_input"),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = { onQuickConnect(quickTarget) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("quick_connect_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = stringResource(R.string.quick_connect_action),
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search & Group Filter Chips
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text(stringResource(R.string.search_hosts_hint)) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("hosts_search_input"),
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          HOST_GROUPS.forEach { group ->
            FilterChip(
              selected = selectedGroup == group,
              onClick = { selectedGroup = group },
              label = { Text(group) },
              modifier = Modifier.testTag("group_chip_${group.lowercase()}"),
            )
          }
        }

        if (filteredHosts.isEmpty()) {
          EmptyHostsState(
            hasSavedHosts = hosts.isNotEmpty(),
            onAddHost = { showNewHostDialog = true },
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(filteredHosts, key = { it.id }) { host ->
              HostItemCard(
                host = host,
                keyName = keys.firstOrNull { it.id == host.keyId }?.name,
                probeResult = probeResults[host.id],
                isProbing = probingHostIds.contains(host.id),
                onConnect = { onConnectHost(host) },
                onEdit = { editingHost = host },
                onProbe = { onProbeHost(host) },
              )
            }
          }
        }
      }
    }
  }

  if (showNewHostDialog) {
    HostEditorDialog(
      initialHost = null,
      keys = keys,
      onDismiss = { showNewHostDialog = false },
      onSave = { newHost ->
        onSaveHost(newHost)
        showNewHostDialog = false
      },
      onDelete = null,
    )
  }

  editingHost?.let { targetHost ->
    HostEditorDialog(
      initialHost = targetHost,
      keys = keys,
      onDismiss = { editingHost = null },
      onSave = { updated ->
        onSaveHost(updated)
        editingHost = null
      },
      onDelete = {
        onDeleteHost(targetHost)
        editingHost = null
      },
    )
  }
}

@Composable
private fun HostItemCard(
  host: SshHostEntity,
  keyName: String?,
  probeResult: TcpProbeResult?,
  isProbing: Boolean,
  onConnect: () -> Unit,
  onEdit: () -> Unit,
  onProbe: () -> Unit,
) {
  val groupColor = when (host.groupName.lowercase()) {
    "production" -> CoralError
    "homelab" -> EmeraldPhosphor
    "cloud" -> Color(0xFF38BDF8)
    else -> AmberWarning
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onConnect)
      .testTag("host_card_${host.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    shape = MaterialTheme.shapes.medium,
  ) {
    Row(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .width(5.dp)
          .height(142.dp)
          .background(groupColor),
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = host.alias.ifBlank { host.hostname },
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${host.username}@${host.hostname}:${host.port}",
              fontFamily = JetBrainsMonoFamily,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text = host.groupName,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val authIcon = when (host.authType) {
            AuthType.PRIVATE_KEY -> Icons.Default.Key
            AuthType.PASSWORD -> Icons.Default.Password
            AuthType.ASK_ON_CONNECT -> Icons.Default.Lock
          }
          val authLabel = when (host.authType) {
            AuthType.PRIVATE_KEY -> keyName ?: "SSH Key"
            AuthType.PASSWORD -> "Password"
            AuthType.ASK_ON_CONNECT -> "Prompt"
          }
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = authIcon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = authLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          if (probeResult != null) {
            val probeColor = if (probeResult.reachable) EmeraldPhosphor else CoralError
            val probeText = if (probeResult.reachable) "${probeResult.latencyMs}ms" else "Offline"
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = probeColor.copy(alpha = 0.16f),
            ) {
              Text(
                text = probeText,
                style = MaterialTheme.typography.labelSmall,
                color = probeColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          } else if (host.lastConnectedAt != null) {
            val formatted = remember(host.lastConnectedAt) {
              SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(host.lastConnectedAt))
            }
            Text(
              text = "Last: $formatted",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onProbe,
            modifier = Modifier.testTag("probe_host_${host.id}"),
          ) {
            if (isProbing) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Default.NetworkCheck,
                contentDescription = "Check TCP reachability",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(
            onClick = onEdit,
            modifier = Modifier.testTag("edit_host_${host.id}"),
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "Edit host",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Button(
            onClick = onConnect,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("connect_host_${host.id}"),
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.connect_button))
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyHostsState(
  hasSavedHosts: Boolean,
  onAddHost: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(72.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (hasSavedHosts) Icons.Default.Search else Icons.Default.Dns,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = if (hasSavedHosts) "No matching hosts" else stringResource(R.string.empty_hosts_title),
      style = MaterialTheme.typography.titleLarge,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = if (hasSavedHosts) {
        "Try clearing your search filter or switching groups."
      } else {
        stringResource(R.string.empty_hosts_desc)
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    if (!hasSavedHosts) {
      Spacer(modifier = Modifier.height(20.dp))
      OutlinedButton(
        onClick = onAddHost,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.add_host))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostEditorDialog(
  initialHost: SshHostEntity?,
  keys: List<SshKeyEntity>,
  onDismiss: () -> Unit,
  onSave: (SshHostEntity) -> Unit,
  onDelete: (() -> Unit)?,
) {
  var alias by remember { mutableStateOf(initialHost?.alias ?: "") }
  var hostname by remember { mutableStateOf(initialHost?.hostname ?: "") }
  var portText by remember { mutableStateOf((initialHost?.port ?: 22).toString()) }
  var username by remember { mutableStateOf(initialHost?.username ?: "root") }
  var groupName by remember { mutableStateOf(initialHost?.groupName ?: "General") }
  var authType by remember { mutableStateOf(initialHost?.authType ?: AuthType.PASSWORD) }
  var password by remember { mutableStateOf(initialHost?.password ?: "") }
  var passwordVisible by remember { mutableStateOf(false) }
  var selectedKeyId by remember { mutableStateOf(initialHost?.keyId ?: keys.firstOrNull()?.id) }
  var startupCmd by remember { mutableStateOf(initialHost?.startupCommand ?: "") }
  var keyDropdownExpanded by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (initialHost == null) stringResource(R.string.add_host) else stringResource(R.string.edit_host),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = alias,
          onValueChange = { alias = it },
          label = { Text(stringResource(R.string.label_alias)) },
          placeholder = { Text("Production API") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("host_alias_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = hostname,
            onValueChange = {
              hostname = it
              validationError = null
            },
            label = { Text(stringResource(R.string.label_hostname)) },
            placeholder = { Text("192.168.1.50") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
            modifier = Modifier
              .weight(1f)
              .testTag("host_hostname_input"),
          )
          OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
            label = { Text(stringResource(R.string.label_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
            modifier = Modifier
              .width(86.dp)
              .testTag("host_port_input"),
          )
        }

        OutlinedTextField(
          value = username,
          onValueChange = {
            username = it
            validationError = null
          },
          label = { Text(stringResource(R.string.label_username)) },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("host_username_input"),
        )

        Text(
          text = stringResource(R.string.label_group),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          HOST_GROUPS.drop(1).forEach { grp ->
            FilterChip(
              selected = groupName == grp,
              onClick = { groupName = grp },
              label = { Text(grp) },
            )
          }
        }

        Text(
          text = stringResource(R.string.label_auth_method),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          AuthType.entries.forEach { method ->
            FilterChip(
              selected = authType == method,
              onClick = { authType = method },
              label = { Text(method.label) },
            )
          }
        }

        if (authType == AuthType.PASSWORD) {
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.label_password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = "Toggle password visibility",
                )
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("host_password_input"),
          )
        } else if (authType == AuthType.PRIVATE_KEY) {
          if (keys.isEmpty()) {
            Text(
              text = "No SSH keys in vault yet. Generate or import one on the Keys tab.",
              style = MaterialTheme.typography.bodySmall,
              color = AmberWarning,
            )
          } else {
            val activeKey = keys.firstOrNull { it.id == selectedKeyId } ?: keys.first()
            ExposedDropdownMenuBox(
              expanded = keyDropdownExpanded,
              onExpandedChange = { keyDropdownExpanded = !keyDropdownExpanded },
            ) {
              OutlinedTextField(
                value = "${activeKey.name} (${activeKey.algorithm})",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.label_select_key)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyDropdownExpanded) },
                modifier = Modifier
                  .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                  .fillMaxWidth(),
              )
              ExposedDropdownMenu(
                expanded = keyDropdownExpanded,
                onDismissRequest = { keyDropdownExpanded = false },
              ) {
                keys.forEach { key ->
                  DropdownMenuItem(
                    text = { Text("${key.name} (${key.algorithm})") },
                    onClick = {
                      selectedKeyId = key.id
                      keyDropdownExpanded = false
                    },
                  )
                }
              }
            }
          }
        }

        OutlinedTextField(
          value = startupCmd,
          onValueChange = { startupCmd = it },
          label = { Text(stringResource(R.string.label_startup_cmd)) },
          placeholder = { Text("cd /srv && ls -la") },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
          modifier = Modifier.fillMaxWidth(),
        )

        validationError?.let { err ->
          Text(
            text = err,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cleanHost = com.example.ssh.SshInputSanitizer.sanitizeHostname(hostname)
          val cleanUser = com.example.ssh.SshInputSanitizer.sanitizeUsername(username)
          val parsedPort = portText.toIntOrNull()
          when {
            cleanHost.isEmpty() -> validationError = "Hostname or IP address is required."
            cleanHost.contains(' ') -> validationError = "Hostname cannot contain spaces."
            cleanUser.isEmpty() -> validationError = "Username is required."
            parsedPort == null || parsedPort !in 1..65535 -> validationError = "Port must be between 1 and 65535."
            else -> {
              onSave(
                SshHostEntity(
                  id = initialHost?.id ?: 0,
                  alias = alias.trim().ifBlank { "$cleanUser@$cleanHost" },
                  hostname = cleanHost,
                  port = parsedPort,
                  username = cleanUser,
                  authType = authType,
                  password = if (authType == AuthType.PASSWORD) password else "",
                  keyId = if (authType == AuthType.PRIVATE_KEY) selectedKeyId else null,
                  groupName = groupName,
                  startupCommand = startupCmd.trim(),
                  lastConnectedAt = initialHost?.lastConnectedAt,
                  createdAt = initialHost?.createdAt ?: System.currentTimeMillis(),
                )
              )
            }
          }
        },
        modifier = Modifier.testTag("save_host_button"),
      ) {
        Text(stringResource(R.string.save_host))
      }
    },
    dismissButton = {
      Row {
        if (onDelete != null) {
          TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("delete_host_button"),
          ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.delete))
          }
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.cancel))
        }
      }
    },
  )
}

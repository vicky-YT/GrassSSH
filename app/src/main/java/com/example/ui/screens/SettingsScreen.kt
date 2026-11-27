package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.TerminalSettings
import com.example.data.model.KnownHostEntity
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TerminalThemePreset
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
  settings: TerminalSettings,
  knownHosts: List<KnownHostEntity>,
  onSelectTheme: (TerminalThemePreset) -> Unit,
  onFontSizeChange: (Int) -> Unit,
  onKeepScreenOnChange: (Boolean) -> Unit,
  onVibrateOnKeyChange: (Boolean) -> Unit,
  onDefaultUsernameChange: (String) -> Unit,
  onForgetKnownHost: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var usernameDraft by remember(settings.defaultUsername) { mutableStateOf(settings.defaultUsername) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 720.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Column {
            Text(
              text = stringResource(R.string.settings_title),
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = "Customize terminal palette, ergonomics, and TOFU trust store",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Live Terminal Preview
        item {
          val preset = settings.themePreset
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .background(preset.background)
              .border(1.dp, preset.accent.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
              .padding(14.dp),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "${settings.defaultUsername}@grass-node:~# uname -srm",
                fontFamily = JetBrainsMonoFamily,
                fontSize = settings.fontSizeSp.sp,
                color = preset.accent,
              )
              Text(
                text = "Linux 6.8.0-45-generic x86_64",
                fontFamily = JetBrainsMonoFamily,
                fontSize = settings.fontSizeSp.sp,
                color = preset.foreground,
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "${settings.defaultUsername}@grass-node:~# ",
                  fontFamily = JetBrainsMonoFamily,
                  fontSize = settings.fontSizeSp.sp,
                  color = preset.accent,
                )
                Box(
                  modifier = Modifier
                    .width(8.dp)
                    .height((settings.fontSizeSp + 2).dp)
                    .background(preset.cursor),
                )
              }
            }
          }
        }

        // Terminal Theme Picker
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "Terminal Color Scheme",
                style = MaterialTheme.typography.titleMedium,
              )
              TerminalThemePreset.entries.forEach { preset ->
                val selected = settings.themePreset == preset
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelectTheme(preset) }
                    .background(
                      if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("theme_option_${preset.name}"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(preset.background)
                        .border(2.dp, preset.accent, CircleShape),
                      contentAlignment = Alignment.Center,
                    ) {
                      Box(
                        modifier = Modifier
                          .size(10.dp)
                          .clip(CircleShape)
                          .background(preset.accent),
                      )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                      text = preset.displayName,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                  }
                  if (selected) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = "Selected",
                      tint = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }
            }
          }
        }

        // Terminal Font Size & Behavior
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Terminal Font Size", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "${settings.fontSizeSp} sp",
                  fontFamily = JetBrainsMonoFamily,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Slider(
                value = settings.fontSizeSp.toFloat(),
                onValueChange = { onFontSizeChange(it.roundToInt()) },
                valueRange = 10f..20f,
                steps = 9,
                modifier = Modifier.testTag("font_size_slider"),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Keep Screen On", style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = "Prevent screen sleep during live SSH sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = settings.keepScreenOn,
                  onCheckedChange = onKeepScreenOnChange,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Haptic Key Feedback", style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = "Subtle vibration on terminal accessory keys",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = settings.vibrateOnKey,
                  onCheckedChange = onVibrateOnKeyChange,
                )
              }

              OutlinedTextField(
                value = usernameDraft,
                onValueChange = {
                  usernameDraft = it
                  onDefaultUsernameChange(it)
                },
                label = { Text("Default Quick-Connect Username") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }

        // Known Hosts (TOFU)
        item {
          Text(
            text = stringResource(R.string.known_hosts_title),
            style = MaterialTheme.typography.titleLarge,
          )
        }

        if (knownHosts.isEmpty()) {
          item {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.VerifiedUser,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = "No trusted host fingerprints yet. When you connect to an SSH server for the first time, GrassSSH verifies and saves its SHA-256 fingerprint here.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(knownHosts, key = { it.hostKeyId }) { known ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${known.hostname}:${known.port} (${known.keyType})",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Text(
                    text = known.fingerprintSha256,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                IconButton(onClick = { onForgetKnownHost(known.hostKeyId) }) {
                  Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Forget known host",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

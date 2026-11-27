package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SnippetEntity
import com.example.ui.theme.JetBrainsMonoFamily

private val DEFAULT_SNIPPET_CATEGORIES = listOf("System", "Docker", "Network", "Git", "Logs")

@Composable
fun SnippetsScreen(
  snippets: List<SnippetEntity>,
  hasActiveSession: Boolean,
  onSaveSnippet: (SnippetEntity) -> Unit,
  onDeleteSnippet: (SnippetEntity) -> Unit,
  onRunSnippet: (SnippetEntity) -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var selectedCategory by rememberSaveable { mutableStateOf("All") }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var editingSnippet by remember { mutableStateOf<SnippetEntity?>(null) }
  var showEditorDialog by remember { mutableStateOf(false) }

  val allCategories = remember(snippets) {
    val dynamic = snippets.map { it.category.trim() }.filter { it.isNotEmpty() }
    listOf("All") + (DEFAULT_SNIPPET_CATEGORIES + dynamic).distinct()
  }

  val filtered = remember(snippets, selectedCategory, searchQuery) {
    val q = searchQuery.trim()
    snippets.filter { item ->
      val catMatch = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
      val queryMatch = q.isEmpty() ||
        item.title.contains(q, ignoreCase = true) ||
        item.command.contains(q, ignoreCase = true) ||
        item.category.contains(q, ignoreCase = true)
      catMatch && queryMatch
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingSnippet = null
          showEditorDialog = true
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("add_snippet_button"),
      ) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_snippet))
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
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
          Text(
            text = stringResource(R.string.snippets_title),
            style = MaterialTheme.typography.headlineMedium,
          )
          Text(
            text = "One-tap shell commands & sysadmin macros",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search snippets or commands...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("snippets_search_input"),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          allCategories.forEach { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat) },
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
              )
              Text(
                text = "No matching command snippets",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Tap New Snippet below to save your own reusable shell command.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(filtered, key = { it.id }) { snippet ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("snippet_card_${snippet.id}"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              shape = MaterialTheme.shapes.medium,
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                      imageVector = Icons.Default.Code,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = snippet.title,
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                  ) {
                    Text(
                      text = snippet.category,
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(10.dp),
                ) {
                  Text(
                    text = snippet.command,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  IconButton(onClick = { onDeleteSnippet(snippet) }) {
                    Icon(
                      imageVector = Icons.Default.DeleteOutline,
                      contentDescription = "Delete snippet",
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                  IconButton(
                    onClick = {
                      editingSnippet = snippet
                      showEditorDialog = true
                    },
                    modifier = Modifier.testTag("edit_snippet_${snippet.id}"),
                  ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit snippet")
                  }
                  IconButton(
                    onClick = {
                      val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      clip.setPrimaryClip(ClipData.newPlainText("Snippet", snippet.command))
                      onNotify("Copied command")
                    },
                  ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy command")
                  }
                  Spacer(modifier = Modifier.width(4.dp))
                  Button(
                    onClick = { onRunSnippet(snippet) },
                    enabled = hasActiveSession,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run")
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

  if (showEditorDialog) {
    SnippetEditorDialog(
      initial = editingSnippet,
      onDismiss = { showEditorDialog = false },
      onSave = { updated ->
        onSaveSnippet(updated)
        showEditorDialog = false
      },
    )
  }
}

@Composable
private fun SnippetEditorDialog(
  initial: SnippetEntity?,
  onDismiss: () -> Unit,
  onSave: (SnippetEntity) -> Unit,
) {
  var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
  var command by remember(initial) { mutableStateOf(initial?.command.orEmpty()) }
  var category by remember(initial) { mutableStateOf(initial?.category ?: "System") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(if (initial == null) stringResource(R.string.add_snippet) else "Edit Snippet")
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title") },
          placeholder = { Text("Restart Nginx") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("snippet_title_input"),
        )
        OutlinedTextField(
          value = command,
          onValueChange = { command = it },
          label = { Text("Shell Command") },
          placeholder = { Text("sudo systemctl restart nginx") },
          minLines = 2,
          maxLines = 4,
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("snippet_command_input"),
        )
        OutlinedTextField(
          value = category,
          onValueChange = { category = it },
          label = { Text("Category") },
          placeholder = { Text("System") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("snippet_category_input"),
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          DEFAULT_SNIPPET_CATEGORIES.forEach { cat ->
            FilterChip(
              selected = category.equals(cat, ignoreCase = true),
              onClick = { category = cat },
              label = { Text(cat) },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            (initial ?: SnippetEntity(title = "", command = "")).copy(
              title = title.trim(),
              command = command.trim(),
              category = category.trim().ifEmpty { "System" },
            )
          )
        },
        enabled = title.isNotBlank() && command.isNotBlank(),
        modifier = Modifier.testTag("save_snippet_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

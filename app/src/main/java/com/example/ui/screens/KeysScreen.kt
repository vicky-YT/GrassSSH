package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SshKeyEntity
import com.example.ssh.KeyAlgorithmOption
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun KeysScreen(
  keys: List<SshKeyEntity>,
  isGeneratingKey: Boolean,
  onGenerateKey: (String, KeyAlgorithmOption, String) -> Unit,
  onImportKey: (String, String, String) -> Unit,
  onDeleteKey: (SshKeyEntity) -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showGenerateDialog by remember { mutableStateOf(false) }
  var showImportDialog by remember { mutableStateOf(false) }
  var inspectingKey by remember { mutableStateOf<SshKeyEntity?>(null) }

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
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 720.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.keys_title),
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = "${keys.size} key(s) stored locally",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isGeneratingKey) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = { showGenerateDialog = true },
            enabled = !isGeneratingKey,
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("generate_key_button"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.generate_key))
          }
          OutlinedButton(
            onClick = { showImportDialog = true },
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("import_key_button"),
          ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.import_key))
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (keys.isEmpty()) {
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
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
              )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = stringResource(R.string.empty_keys_title),
              style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = stringResource(R.string.empty_keys_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(keys, key = { it.id }) { keyItem ->
              KeyVaultCard(
                keyItem = keyItem,
                onCopyPublic = {
                  if (keyItem.publicKeyOpenSsh.isBlank()) {
                    onNotify("Public key unavailable for passphrase-locked imported key")
                  } else {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("SSH Public Key", keyItem.publicKeyOpenSsh))
                    onNotify("Copied OpenSSH public key")
                  }
                },
                onInspect = { inspectingKey = keyItem },
                onDelete = { onDeleteKey(keyItem) },
              )
            }
          }
        }
      }
    }
  }

  if (showGenerateDialog) {
    GenerateKeyDialog(
      onDismiss = { showGenerateDialog = false },
      onGenerate = { name, algo, pass ->
        onGenerateKey(name, algo, pass)
        showGenerateDialog = false
      },
    )
  }

  if (showImportDialog) {
    ImportKeyDialog(
      onDismiss = { showImportDialog = false },
      onImport = { name, pem, pass ->
        onImportKey(name, pem, pass)
        showImportDialog = false
      },
    )
  }

  inspectingKey?.let { keyItem ->
    AlertDialog(
      onDismissRequest = { inspectingKey = null },
      title = { Text(keyItem.name) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "${keyItem.algorithm} • ${keyItem.fingerprintSha256}",
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(MaterialTheme.shapes.small)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .verticalScroll(rememberScrollState())
              .padding(10.dp),
          ) {
            SelectionContainer {
              Text(
                text = keyItem.publicKeyOpenSsh.ifBlank { "(Passphrase-protected private key; public key hidden)" },
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
              )
            }
          }
        }
      },
      confirmButton = {
        if (keyItem.publicKeyOpenSsh.isNotBlank()) {
          Button(
            onClick = {
              val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clip.setPrimaryClip(ClipData.newPlainText("SSH Public Key", keyItem.publicKeyOpenSsh))
              onNotify("Copied OpenSSH public key")
              inspectingKey = null
            },
          ) {
            Text(stringResource(R.string.copy_public_key))
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { inspectingKey = null }) {
          Text("Close")
        }
      },
    )
  }
}

@Composable
private fun KeyVaultCard(
  keyItem: SshKeyEntity,
  onCopyPublic: () -> Unit,
  onInspect: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("key_card_${keyItem.id}"),
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
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = keyItem.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (keyItem.hasPassphrase) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Passphrase protected",
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(16.dp),
            )
          }
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = keyItem.algorithm,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = keyItem.fingerprintSha256,
        fontFamily = JetBrainsMonoFamily,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onInspect) {
          Icon(Icons.Default.Visibility, contentDescription = "View public key")
        }
        IconButton(
          onClick = onDelete,
          colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "Delete key")
        }
        Spacer(modifier = Modifier.width(4.dp))
        OutlinedButton(
          onClick = onCopyPublic,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy Pub")
        }
      }
    }
  }
}

@Composable
private fun GenerateKeyDialog(
  onDismiss: () -> Unit,
  onGenerate: (String, KeyAlgorithmOption, String) -> Unit,
) {
  var name by remember { mutableStateOf("My Device Key") }
  var selectedAlgo by remember { mutableStateOf(KeyAlgorithmOption.ECDSA_256) }
  var passphrase by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.generate_key)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Key Alias") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("generate_key_name_input"),
        )
        Text(
          text = "Algorithm",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          KeyAlgorithmOption.entries.forEach { option ->
            FilterChip(
              selected = selectedAlgo == option,
              onClick = { selectedAlgo = option },
              label = { Text(option.label) },
            )
          }
        }
        OutlinedTextField(
          value = passphrase,
          onValueChange = { passphrase = it },
          label = { Text("Passphrase (optional)") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onGenerate(name, selectedAlgo, passphrase) },
        enabled = name.isNotBlank(),
        modifier = Modifier.testTag("confirm_generate_key_button"),
      ) {
        Text("Generate")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
private fun ImportKeyDialog(
  onDismiss: () -> Unit,
  onImport: (String, String, String) -> Unit,
) {
  val context = LocalContext.current
  var name by remember { mutableStateOf("") }
  var pemText by remember { mutableStateOf("") }
  var passphrase by remember { mutableStateOf("") }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri != null) {
      val content = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
      }.getOrNull()
      if (!content.isNullOrBlank()) {
        pemText = content
        if (name.isBlank()) {
          name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Key"
        }
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.import_key)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Key Name") },
          placeholder = { Text("id_rsa_prod") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
          onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pick Private Key File")
        }
        OutlinedTextField(
          value = pemText,
          onValueChange = { pemText = it },
          label = { Text("Private Key PEM / OpenSSH") },
          placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
          minLines = 4,
          maxLines = 7,
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = passphrase,
          onValueChange = { passphrase = it },
          label = { Text("Passphrase (if encrypted)") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onImport(name, pemText, passphrase) },
        enabled = name.isNotBlank() && pemText.isNotBlank(),
      ) {
        Text("Import")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

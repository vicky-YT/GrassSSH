package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ssh.LiveSshSession
import com.example.ssh.SessionStatus
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TerminalThemePreset
import kotlinx.coroutines.launch

/**
 * Text-based Terminal view component rendering real-time ANSI/VT100 output
 * streamed from a live JSch SSH session.
 */
@Composable
fun SshTerminalView(
  session: LiveSshSession,
  theme: TerminalThemePreset,
  fontSizeSp: Int,
  modifier: Modifier = Modifier,
) {
  val lines by session.lines.collectAsStateWithLifecycle()
  val activeLine by session.activeLine.collectAsStateWithLifecycle()
  val status by session.status.collectAsStateWithLifecycle()
  val errorMessage by session.errorMessage.collectAsStateWithLifecycle()

  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()

  val isScrolledAway by remember {
    derivedStateOf {
      val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      val totalItems = listState.layoutInfo.totalItemsCount
      totalItems > 4 && lastVisible < totalItems - 2
    }
  }

  // Auto-scroll to bottom as new JSch output arrives unless user scrolled up
  LaunchedEffect(lines.size, activeLine.text) {
    if (!isScrolledAway && lines.isNotEmpty()) {
      listState.scrollToItem(lines.size)
    }
  }

  val blinkTransition = rememberInfiniteTransition(label = "terminal_cursor_blink")
  val cursorAlpha by blinkTransition.animateFloat(
    initialValue = 1f,
    targetValue = 0.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(650),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "terminal_cursor_alpha",
  )

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(theme.background)
      .testTag("ssh_terminal_view"),
  ) {
    val charWidthDp = (fontSizeSp * 0.62f).dp
    val lineHeightDp = (fontSizeSp * 1.38f).dp
    val estimatedCols = (maxWidth / charWidthDp).toInt().coerceIn(40, 200)
    val estimatedRows = (maxHeight / lineHeightDp).toInt().coerceIn(12, 80)

    LaunchedEffect(estimatedCols, estimatedRows, status) {
      if (status == SessionStatus.CONNECTED) {
        session.resizePty(estimatedCols, estimatedRows)
      }
    }

    val fontSize = fontSizeSp.sp
    val lineHeight = (fontSizeSp * 1.35f).sp

    Column(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp),
      ) {
        SelectionContainer {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .testTag("terminal_output_list"),
          ) {
            items(lines) { line ->
              Text(
                text = line,
                fontFamily = JetBrainsMonoFamily,
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = theme.foreground,
              )
            }
            item(key = "active_prompt_line") {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = activeLine,
                  fontFamily = JetBrainsMonoFamily,
                  fontSize = fontSize,
                  lineHeight = lineHeight,
                  color = theme.foreground,
                )
                if (status == SessionStatus.CONNECTED) {
                  Box(
                    modifier = Modifier
                      .alpha(cursorAlpha)
                      .width(8.dp)
                      .height((fontSizeSp + 3).dp)
                      .background(theme.cursor),
                  )
                }
              }
            }
          }
        }

        androidx.compose.animation.AnimatedVisibility(
          visible = isScrolledAway,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp),
        ) {
          SmallFloatingActionButton(
            onClick = {
              coroutineScope.launch {
                listState.animateScrollToItem(lines.size)
              }
            },
            containerColor = theme.surface,
            contentColor = theme.accent,
          ) {
            Icon(
              imageVector = Icons.Default.ArrowDownward,
              contentDescription = "Jump to latest output",
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      if (status == SessionStatus.ERROR && !errorMessage.isNullOrBlank()) {
        Surface(
          color = CoralError.copy(alpha = 0.14f),
          shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = errorMessage ?: "Connection failed",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
              color = CoralError,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { session.reconnect() }) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = CoralError,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Retry", color = CoralError)
            }
          }
        }
      }
    }
  }
}

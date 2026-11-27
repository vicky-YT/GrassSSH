package com.example.ssh

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

private data class CellStyle(
  val fg: Color? = null,
  val bg: Color? = null,
  val bold: Boolean = false,
  val dim: Boolean = false,
  val underline: Boolean = false,
  val inverse: Boolean = false,
)

private data class StyledChar(
  val ch: Char,
  val style: CellStyle,
)

class AnsiTerminalParser(
  private val maxScrollbackLines: Int = 1200,
  private val onTitleChanged: (String) -> Unit = {},
  private val onBell: () -> Unit = {},
) {
  private val historyLines = ArrayList<AnnotatedString>()
  private var activeLine = ArrayList<StyledChar>()
  private var cursorCol = 0
  private var currentStyle = CellStyle()

  private enum class State {
    NORMAL,
    ESC,
    CSI,
    OSC,
    OSC_ESC,
  }

  private var state = State.NORMAL
  private val seqBuffer = StringBuilder()

  @Synchronized
  fun feed(text: String) {
    for (ch in text) {
      when (state) {
        State.NORMAL -> handleNormalChar(ch)
        State.ESC -> handleEscChar(ch)
        State.CSI -> handleCsiChar(ch)
        State.OSC -> handleOscChar(ch)
        State.OSC_ESC -> {
          if (ch == '\\') {
            finishOsc()
          }
          state = State.NORMAL
        }
      }
    }
  }

  private fun handleNormalChar(ch: Char) {
    when (ch) {
      '\u001B' -> {
        state = State.ESC
        seqBuffer.clear()
      }
      '\u0007' -> onBell()
      '\r' -> cursorCol = 0
      '\n' -> commitLine()
      '\b', '\u007F' -> {
        if (cursorCol > 0) {
          cursorCol--
        }
      }
      '\t' -> {
        val spaces = 8 - (cursorCol % 8)
        repeat(spaces) { writeChar(' ') }
      }
      else -> {
        if (ch.code >= 0x20) {
          writeChar(ch)
        }
      }
    }
  }

  private fun writeChar(ch: Char) {
    while (activeLine.size < cursorCol) {
      activeLine.add(StyledChar(' ', CellStyle()))
    }
    val cell = StyledChar(ch, currentStyle)
    if (cursorCol < activeLine.size) {
      activeLine[cursorCol] = cell
    } else {
      activeLine.add(cell)
    }
    cursorCol++
  }

  private fun commitLine() {
    historyLines.add(buildLineAnnotatedString(activeLine))
    if (historyLines.size > maxScrollbackLines) {
      historyLines.removeAt(0)
    }
    activeLine = ArrayList()
    cursorCol = 0
  }

  private fun handleEscChar(ch: Char) {
    when (ch) {
      '[' -> {
        state = State.CSI
        seqBuffer.clear()
      }
      ']' -> {
        state = State.OSC
        seqBuffer.clear()
      }
      '(', ')', '*', '+' -> {
        // Charset designator; consume gracefully
        state = State.NORMAL
      }
      else -> state = State.NORMAL
    }
  }

  private fun handleCsiChar(ch: Char) {
    if (ch.code in 0x40..0x7E) {
      executeCsi(seqBuffer.toString(), ch)
      state = State.NORMAL
      seqBuffer.clear()
    } else {
      if (seqBuffer.length < 64) {
        seqBuffer.append(ch)
      }
    }
  }

  private fun handleOscChar(ch: Char) {
    when (ch) {
      '\u0007' -> {
        finishOsc()
        state = State.NORMAL
      }
      '\u001B' -> state = State.OSC_ESC
      else -> {
        if (seqBuffer.length < 256) {
          seqBuffer.append(ch)
        }
      }
    }
  }

  private fun finishOsc() {
    val content = seqBuffer.toString()
    seqBuffer.clear()
    if (content.startsWith("0;") || content.startsWith("2;")) {
      val title = content.substring(2).trim()
      if (title.isNotEmpty()) {
        onTitleChanged(title)
      }
    }
  }

  private fun executeCsi(params: String, finalChar: Char) {
    if (params.startsWith("?")) {
      // Private DEC modes like ?25h, ?2004h, ?1049h -> ignore safely
      return
    }
    val args = if (params.isEmpty()) emptyList() else params.split(';').map { it.toIntOrNull() }
    when (finalChar) {
      'm' -> applySgr(args)
      'K' -> {
        val mode = args.firstOrNull() ?: 0
        when (mode) {
          0 -> {
            if (cursorCol < activeLine.size) {
              activeLine.subList(cursorCol, activeLine.size).clear()
            }
          }
          1 -> {
            for (i in 0 until minOf(cursorCol, activeLine.size)) {
              activeLine[i] = StyledChar(' ', CellStyle())
            }
          }
          2 -> {
            activeLine.clear()
            cursorCol = 0
          }
        }
      }
      'J' -> {
        val mode = args.firstOrNull() ?: 0
        if (mode == 2 || mode == 3) {
          historyLines.clear()
          activeLine.clear()
          cursorCol = 0
        }
      }
      'C' -> {
        val count = (args.firstOrNull() ?: 1).coerceAtLeast(1)
        cursorCol += count
      }
      'D' -> {
        val count = (args.firstOrNull() ?: 1).coerceAtLeast(1)
        cursorCol = (cursorCol - count).coerceAtLeast(0)
      }
      'G' -> {
        val col = ((args.firstOrNull() ?: 1) - 1).coerceAtLeast(0)
        cursorCol = col
      }
      'H', 'f' -> {
        // Home / cursor move: if column provided without full screen buffer, adjust col cleanly
        if (args.size >= 2) {
          cursorCol = ((args[1] ?: 1) - 1).coerceAtLeast(0)
        } else if (args.isEmpty()) {
          cursorCol = 0
        }
      }
    }
  }

  private fun applySgr(rawArgs: List<Int?>) {
    val args = if (rawArgs.isEmpty()) listOf(0) else rawArgs
    var i = 0
    while (i < args.size) {
      val code = args[i] ?: 0
      when (code) {
        0 -> currentStyle = CellStyle()
        1 -> currentStyle = currentStyle.copy(bold = true)
        2 -> currentStyle = currentStyle.copy(dim = true)
        4 -> currentStyle = currentStyle.copy(underline = true)
        7 -> currentStyle = currentStyle.copy(inverse = true)
        22 -> currentStyle = currentStyle.copy(bold = false, dim = false)
        24 -> currentStyle = currentStyle.copy(underline = false)
        27 -> currentStyle = currentStyle.copy(inverse = false)
        in 30..37 -> currentStyle = currentStyle.copy(fg = ansi16Color(code - 30, bright = false))
        39 -> currentStyle = currentStyle.copy(fg = null)
        in 40..47 -> currentStyle = currentStyle.copy(bg = ansi16Color(code - 40, bright = false))
        49 -> currentStyle = currentStyle.copy(bg = null)
        in 90..97 -> currentStyle = currentStyle.copy(fg = ansi16Color(code - 90, bright = true))
        in 100..107 -> currentStyle = currentStyle.copy(bg = ansi16Color(code - 100, bright = true))
        38, 48 -> {
          val isFg = code == 38
          val mode = args.getOrNull(i + 1)
          if (mode == 5 && i + 2 < args.size) {
            val colorIndex = args[i + 2] ?: 0
            val color = ansi256Color(colorIndex)
            currentStyle = if (isFg) currentStyle.copy(fg = color) else currentStyle.copy(bg = color)
            i += 2
          } else if (mode == 2 && i + 4 < args.size) {
            val r = (args[i + 2] ?: 0).coerceIn(0, 255)
            val g = (args[i + 3] ?: 0).coerceIn(0, 255)
            val b = (args[i + 4] ?: 0).coerceIn(0, 255)
            val color = Color(r, g, b)
            currentStyle = if (isFg) currentStyle.copy(fg = color) else currentStyle.copy(bg = color)
            i += 4
          }
        }
      }
      i++
    }
  }

  private fun buildLineAnnotatedString(chars: List<StyledChar>): AnnotatedString {
    if (chars.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
      var runStart = 0
      while (runStart < chars.size) {
        val style = chars[runStart].style
        var runEnd = runStart + 1
        while (runEnd < chars.size && chars[runEnd].style == style) {
          runEnd++
        }
        val sb = StringBuilder(runEnd - runStart)
        for (k in runStart until runEnd) {
          sb.append(chars[k].ch)
        }
        val segment = sb.toString()
        val span = toSpanStyle(style)
        if (span != null) {
          pushStyle(span)
          append(segment)
          pop()
        } else {
          append(segment)
        }
        runStart = runEnd
      }
    }
  }

  private fun toSpanStyle(style: CellStyle): SpanStyle? {
    if (style == CellStyle()) return null
    val rawFg = style.fg?.let { if (style.dim) it.copy(alpha = 0.65f) else it }
    val rawBg = style.bg
    val finalFg = if (style.inverse) rawBg ?: Color(0xFF0A110D) else rawFg
    val finalBg = if (style.inverse) rawFg ?: Color(0xFFD8F7E6) else rawBg
    return SpanStyle(
      color = finalFg ?: Color.Unspecified,
      background = finalBg ?: Color.Unspecified,
      fontWeight = if (style.bold) FontWeight.Bold else null,
      textDecoration = if (style.underline) TextDecoration.Underline else null,
    )
  }

  @Synchronized
  fun snapshotHistory(): List<AnnotatedString> = historyLines.toList()

  @Synchronized
  fun snapshotActiveLine(): AnnotatedString = buildLineAnnotatedString(activeLine)

  @Synchronized
  fun snapshotAllPlainText(): String = buildString {
    historyLines.forEach { appendLine(it.text) }
    append(buildLineAnnotatedString(activeLine).text)
  }

  @Synchronized
  fun clear() {
    historyLines.clear()
    activeLine.clear()
    cursorCol = 0
  }

  @Synchronized
  fun appendSystemMessage(message: String, color: Color) {
    if (activeLine.isNotEmpty()) {
      commitLine()
    }
    historyLines.add(
      buildAnnotatedString {
        pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium))
        append(message)
        pop()
      }
    )
    if (historyLines.size > maxScrollbackLines) {
      historyLines.removeAt(0)
    }
  }

  private fun ansi16Color(index: Int, bright: Boolean): Color {
    return if (!bright) {
      when (index) {
        0 -> Color(0xFF1F2937)
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFF22C55E)
        3 -> Color(0xFFEAB308)
        4 -> Color(0xFF3B82F6)
        5 -> Color(0xFFA855F7)
        6 -> Color(0xFF06B6D4)
        7 -> Color(0xFFE5E7EB)
        else -> Color.Unspecified
      }
    } else {
      when (index) {
        0 -> Color(0xFF6B7280)
        1 -> Color(0xFFF87171)
        2 -> Color(0xFF4ADE80)
        3 -> Color(0xFFFACC15)
        4 -> Color(0xFF60A5FA)
        5 -> Color(0xFFC084FC)
        6 -> Color(0xFF22D3EE)
        7 -> Color(0xFFFFFFFF)
        else -> Color.Unspecified
      }
    }
  }

  private fun ansi256Color(index: Int): Color {
    if (index < 0 || index > 255) return Color.Unspecified
    if (index < 8) return ansi16Color(index, bright = false)
    if (index < 16) return ansi16Color(index - 8, bright = true)
    if (index >= 232) {
      val gray = 8 + (index - 232) * 10
      return Color(gray, gray, gray)
    }
    val idx = index - 16
    val rIdx = idx / 36
    val gIdx = (idx % 36) / 6
    val bIdx = idx % 6
    val levels = intArrayOf(0, 95, 135, 175, 215, 255)
    return Color(levels[rIdx], levels[gIdx], levels[bIdx])
  }
}

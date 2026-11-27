package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

enum class AiLanguageMode(
  val displayName: String,
  val shortLabel: String,
  val promptInstruction: String,
) {
  ENGLISH_AND_HINGLISH(
    displayName = "English + Hinglish",
    shortLabel = "Eng + Hinglish",
    promptInstruction = "Language preference: Understand both English and Hinglish seamlessly. Explain problems, concepts, and steps in friendly Hinglish (Hindi written in English letters) when the user writes in Hinglish or wants easy explanation, or clear English when the user writes in pure English—feel free to mix simple Hinglish + English explanations so everything is crystal clear. Always keep Linux/SSH commands, flags, file paths, and code in exact English.",
  ),
  HINGLISH(
    displayName = "Hinglish",
    shortLabel = "Hinglish",
    promptInstruction = "Language preference: Always talk and explain everything clearly in simple, natural Hinglish (Hindi written in English script, e.g., 'Ye error port conflict ki wajah se aa raha hai...'). Always keep exact Linux/SSH commands, flags, file paths, and code in English.",
  ),
  ENGLISH(
    displayName = "English",
    shortLabel = "English",
    promptInstruction = "Language preference: Respond and explain clearly in concise, professional English.",
  ),
}

object AiImageAttachmentHelper {
  private const val DEFAULT_MAX_DIMENSION_PX = 1280

  suspend fun decodeAndScaleImageFromUri(
    context: Context,
    uri: Uri,
    maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX,
  ): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
          // Software allocation is required because Firebase AI SDK compresses Bitmaps on CPU
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          decoder.isMutableRequired = false
          val width = info.size.width
          val height = info.size.height
          val largest = max(width, height)
          if (largest > maxDimensionPx && largest > 0) {
            val scale = maxDimensionPx.toFloat() / largest.toFloat()
            val targetW = (width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (height * scale).roundToInt().coerceAtLeast(1)
            decoder.setTargetSize(targetW, targetH)
          }
        }
      } else {
        val boundsOptions = BitmapFactory.Options().apply {
          inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        if (rawW <= 0 || rawH <= 0) return@runCatching null

        var sampleSize = 1
        while (max(rawW / sampleSize, rawH / sampleSize) > maxDimensionPx * 2) {
          sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return@runCatching null

        val largest = max(decoded.width, decoded.height)
        if (largest > maxDimensionPx && largest > 0) {
          val scale = maxDimensionPx.toFloat() / largest.toFloat()
          val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
          val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
          val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
          if (scaled != decoded) decoded.recycle()
          scaled
        } else {
          decoded
        }
      }
    }.getOrNull()
  }
}

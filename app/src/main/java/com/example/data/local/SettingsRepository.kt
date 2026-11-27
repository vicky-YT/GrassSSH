package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.TerminalThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "grassssh_settings")

data class TerminalSettings(
  val themePreset: TerminalThemePreset = TerminalThemePreset.GRASS_PHOSPHOR,
  val fontSizeSp: Int = 13,
  val keepScreenOn: Boolean = true,
  val vibrateOnKey: Boolean = true,
  val defaultUsername: String = "root",
  val customAiInstructions: String = "",
)

class SettingsRepository(private val context: Context) {
  private object Keys {
    val THEME_PRESET = stringPreferencesKey("theme_preset")
    val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val VIBRATE_ON_KEY = booleanPreferencesKey("vibrate_on_key")
    val DEFAULT_USERNAME = stringPreferencesKey("default_username")
    val CUSTOM_AI_INSTRUCTIONS = stringPreferencesKey("custom_ai_instructions")
  }

  val settings: Flow<TerminalSettings> = context.settingsDataStore.data.map { prefs ->
    val presetName = prefs[Keys.THEME_PRESET]
    val preset = TerminalThemePreset.entries.firstOrNull { it.name == presetName }
      ?: TerminalThemePreset.GRASS_PHOSPHOR
    TerminalSettings(
      themePreset = preset,
      fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 13).coerceIn(10, 20),
      keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
      vibrateOnKey = prefs[Keys.VIBRATE_ON_KEY] ?: true,
      defaultUsername = prefs[Keys.DEFAULT_USERNAME]?.ifBlank { "root" } ?: "root",
      customAiInstructions = prefs[Keys.CUSTOM_AI_INSTRUCTIONS] ?: "",
    )
  }

  suspend fun setThemePreset(preset: TerminalThemePreset) {
    context.settingsDataStore.edit { it[Keys.THEME_PRESET] = preset.name }
  }

  suspend fun setFontSizeSp(sizeSp: Int) {
    context.settingsDataStore.edit { it[Keys.FONT_SIZE_SP] = sizeSp.coerceIn(10, 20) }
  }

  suspend fun setKeepScreenOn(enabled: Boolean) {
    context.settingsDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
  }

  suspend fun setVibrateOnKey(enabled: Boolean) {
    context.settingsDataStore.edit { it[Keys.VIBRATE_ON_KEY] = enabled }
  }

  suspend fun setDefaultUsername(username: String) {
    context.settingsDataStore.edit { it[Keys.DEFAULT_USERNAME] = username.trim().ifBlank { "root" } }
  }

  suspend fun setCustomAiInstructions(instructions: String) {
    context.settingsDataStore.edit { it[Keys.CUSTOM_AI_INSTRUCTIONS] = instructions.trim() }
  }
}

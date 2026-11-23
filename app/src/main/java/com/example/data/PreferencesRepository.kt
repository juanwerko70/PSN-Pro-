package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pkg_forge_settings")

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.DARK_GLASS,
    val languageTag: String = "system",
    val glassOpacity: Float = 0.56f,
    val pushUpdateAlertsEnabled: Boolean = true,
    val lastNotifiedVersionCode: Long = 1L
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val GLASS_OPACITY = floatPreferencesKey("glass_opacity")
        val PUSH_UPDATES = booleanPreferencesKey("push_updates")
        val LAST_NOTIFIED_VERSION = stringPreferencesKey("last_notified_version")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = AppThemeMode.entries.firstOrNull {
            it.name == (prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK_GLASS.name)
        } ?: AppThemeMode.DARK_GLASS
        UserPreferences(
            themeMode = theme,
            languageTag = prefs[Keys.LANGUAGE_TAG] ?: "system",
            glassOpacity = prefs[Keys.GLASS_OPACITY] ?: 0.56f,
            pushUpdateAlertsEnabled = prefs[Keys.PUSH_UPDATES] ?: true,
            lastNotifiedVersionCode = prefs[Keys.LAST_NOTIFIED_VERSION]?.toLongOrNull() ?: 1L
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguageTag(tag: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_TAG] = tag }
    }

    suspend fun setGlassOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.GLASS_OPACITY] = opacity.coerceIn(0.28f, 0.90f) }
    }

    suspend fun setPushUpdateAlerts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PUSH_UPDATES] = enabled }
    }

    suspend fun setLastNotifiedVersionCode(versionCode: Long) {
        context.dataStore.edit { it[Keys.LAST_NOTIFIED_VERSION] = versionCode.toString() }
    }
}

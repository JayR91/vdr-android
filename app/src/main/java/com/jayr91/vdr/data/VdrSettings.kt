package com.jayr91.vdr.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vdr_settings")

data class VdrPrefs(
    val wifiOnly: Boolean = false,
    val speedKb: Int = 0,
    val focusGuard: Boolean = false,
    val lastClipboardUrl: String = "",
)

object VdrSettings {
    private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    private val SPEED_KB = intPreferencesKey("speed_kb")
    private val FOCUS_GUARD = booleanPreferencesKey("focus_guard")
    private val LAST_CLIPBOARD = stringPreferencesKey("last_clipboard_url")

    fun prefs(context: Context): Flow<VdrPrefs> =
        context.applicationContext.dataStore.data.map { p ->
            VdrPrefs(
                wifiOnly = p[WIFI_ONLY] ?: false,
                speedKb = p[SPEED_KB] ?: 0,
                focusGuard = p[FOCUS_GUARD] ?: false,
                lastClipboardUrl = p[LAST_CLIPBOARD].orEmpty(),
            )
        }

    suspend fun read(context: Context): VdrPrefs = prefs(context).first()

    suspend fun setWifiOnly(context: Context, value: Boolean) {
        context.applicationContext.dataStore.edit { it[WIFI_ONLY] = value }
    }

    suspend fun setSpeedKb(context: Context, value: Int) {
        context.applicationContext.dataStore.edit { it[SPEED_KB] = value }
    }

    suspend fun setFocusGuard(context: Context, value: Boolean) {
        context.applicationContext.dataStore.edit { it[FOCUS_GUARD] = value }
    }

    suspend fun setLastClipboardUrl(context: Context, value: String) {
        context.applicationContext.dataStore.edit { it[LAST_CLIPBOARD] = value }
    }
}

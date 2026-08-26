package com.jayr91.vdr.billing

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.proDataStore by preferencesDataStore(name = "vdr_pro")

private fun Context.isDebuggable(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * Local cache of Pro unlock. Play Billing is the source of truth; this store
 * keeps the unlock available offline after a successful purchase/restore.
 * Debug unlock is only honored on debuggable builds (long-press fake unlock).
 */
object ProEntitlement {
    private val UNLOCKED = booleanPreferencesKey("pro_unlocked")
    private val DEBUG_UNLOCKED = booleanPreferencesKey("pro_debug_unlocked")

    fun isProFlow(context: Context): Flow<Boolean> {
        val app = context.applicationContext
        return app.proDataStore.data.map { prefs ->
            val purchased = prefs[UNLOCKED] == true
            val debug = app.isDebuggable() && prefs[DEBUG_UNLOCKED] == true
            purchased || debug
        }
    }

    suspend fun isPro(context: Context): Boolean = isProFlow(context).first()

    suspend fun setPurchased(context: Context, unlocked: Boolean) {
        context.applicationContext.proDataStore.edit { it[UNLOCKED] = unlocked }
    }

    /** Debug-only toggle for device testing without Play Billing. */
    suspend fun setDebugUnlocked(context: Context, unlocked: Boolean) {
        val app = context.applicationContext
        if (!app.isDebuggable()) return
        app.proDataStore.edit { it[DEBUG_UNLOCKED] = unlocked }
    }
}

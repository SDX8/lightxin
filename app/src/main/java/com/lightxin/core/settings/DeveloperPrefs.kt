package com.lightxin.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lightxin_dev")

@Singleton
class DeveloperPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_ADVANCED_ENABLED = booleanPreferencesKey("advanced_enabled")
        private val KEY_ROUTE_IN_PLAINTEXT = booleanPreferencesKey("route_in_plaintext")
    }

    val isAdvancedEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ADVANCED_ENABLED] == true
    }

    suspend fun setAdvancedEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADVANCED_ENABLED] = enabled
        }
    }

    /**
     * 调试用 A/B：`runningRoute` 的值不加密（键名与其它字段仍走 RSA）。
     * 用来验证「长字段分块拼接的密文服务端解不开」这一假设。
     */
    val uploadRouteInPlaintext: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ROUTE_IN_PLAINTEXT] == true
    }

    suspend fun setUploadRouteInPlaintext(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ROUTE_IN_PLAINTEXT] = enabled
        }
    }
}

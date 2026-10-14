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
        private val KEY_EXTRACT_RECORD_UPLOAD = booleanPreferencesKey("extract_record_upload")
    }

    val isAdvancedEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ADVANCED_ENABLED] == true
    }

    suspend fun setAdvancedEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADVANCED_ENABLED] = enabled
        }
    }

    /** 调试用：跑步上传改走官方 H5 的 addExtractCheckRecord.do 明文端点，用于 A/B 定位「运动轨迹异常」。 */
    val extractRecordUploadEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EXTRACT_RECORD_UPLOAD] == true
    }

    suspend fun setExtractRecordUploadEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EXTRACT_RECORD_UPLOAD] = enabled
        }
    }
}

package com.lightxin.feature.schedule.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lightxin.feature.schedule.domain.MorningSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.morningPrefs: DataStore<Preferences> by preferencesDataStore(name = "lightxin_morning")

/**
 * 早自习/早锻炼设置的落盘处。条数很少且整体读写，直接序列化成一段 JSON 存 prefs，
 * 不值得为它开一个文件存储。
 */
@Singleton
class MorningSectionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    val settings: Flow<List<MorningSetting>> = context.morningPrefs.data.map { prefs ->
        decode(prefs[KEY_SETTINGS])
    }

    suspend fun current(): List<MorningSetting> = settings.first()

    /** 同 id 覆盖，没有则追加 */
    suspend fun save(setting: MorningSetting) {
        val existing = current()
        val next = if (existing.any { it.id == setting.id }) {
            existing.map { if (it.id == setting.id) setting else it }
        } else {
            existing + setting
        }
        write(next)
    }

    suspend fun delete(id: String) {
        write(current().filterNot { it.id == id })
    }

    suspend fun clear() {
        context.morningPrefs.edit { it.remove(KEY_SETTINGS) }
    }

    private suspend fun write(settings: List<MorningSetting>) {
        val json = if (settings.isEmpty()) null else gson.toJson(settings)
        context.morningPrefs.edit { prefs ->
            if (json == null) prefs.remove(KEY_SETTINGS) else prefs[KEY_SETTINGS] = json
        }
    }

    private fun decode(json: String?): List<MorningSetting> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<MorningSetting>>(json, SETTINGS_TYPE) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_SETTINGS = stringPreferencesKey("morning_settings")
        val SETTINGS_TYPE = object : TypeToken<List<MorningSetting>>() {}.type
    }
}

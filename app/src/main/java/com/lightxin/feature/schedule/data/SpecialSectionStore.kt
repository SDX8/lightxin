package com.lightxin.feature.schedule.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lightxin.feature.schedule.domain.SpecialItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.specialPrefs: DataStore<Preferences> by preferencesDataStore(name = "lightxin_special_sections")

/**
 * 早自习/早锻炼/晚自习设置的落盘处。条数很少且整体读写，直接序列化成一段 JSON 存 prefs，
 * 不值得为它开一个文件存储。
 */
@Singleton
class SpecialSectionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    val settings: Flow<List<SpecialItem>> = context.specialPrefs.data.map { prefs ->
        decode(prefs[KEY_SETTINGS])
    }

    suspend fun current(): List<SpecialItem> = settings.first()

    /** 用户按下「今晚无晚自习」的那一天；过了一天它就不再匹配今天 */
    val eveningPausedOn: Flow<LocalDate?> = context.specialPrefs.data.map { prefs ->
        prefs[KEY_EVENING_PAUSED_ON]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    /** "第二天要上课才有晚自习"这条校历规则，默认开着；有的班周五晚上照样上自习，可以整条关掉 */
    val eveningSkipFriSat: Flow<Boolean> = context.specialPrefs.data.map { prefs ->
        prefs[KEY_EVENING_SKIP_FRI_SAT] ?: true
    }

    suspend fun setEveningSkipFriSat(skip: Boolean) {
        context.specialPrefs.edit { prefs -> prefs[KEY_EVENING_SKIP_FRI_SAT] = skip }
    }

    suspend fun setEveningPaused(paused: Boolean) {
        context.specialPrefs.edit { prefs ->
            if (paused) prefs[KEY_EVENING_PAUSED_ON] = LocalDate.now().toString()
            else prefs.remove(KEY_EVENING_PAUSED_ON)
        }
    }

    /** 同 id 覆盖，没有则追加 */
    suspend fun save(setting: SpecialItem) {
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
        context.specialPrefs.edit { it.clear() }
    }

    private suspend fun write(settings: List<SpecialItem>) {
        val json = if (settings.isEmpty()) null else gson.toJson(settings)
        context.specialPrefs.edit { prefs ->
            if (json == null) prefs.remove(KEY_SETTINGS) else prefs[KEY_SETTINGS] = json
        }
    }

    private fun decode(json: String?): List<SpecialItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<SpecialItem>>(json, SETTINGS_TYPE) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_SETTINGS = stringPreferencesKey("special_settings")
        val KEY_EVENING_PAUSED_ON = stringPreferencesKey("evening_paused_on")
        val KEY_EVENING_SKIP_FRI_SAT = booleanPreferencesKey("evening_skip_fri_sat")
        val SETTINGS_TYPE = object : TypeToken<List<SpecialItem>>() {}.type
    }
}

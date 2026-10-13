package com.lightxin.feature.schedule.data

import android.content.Context
import com.google.gson.Gson
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.feature.schedule.domain.ScheduleData
import com.lightxin.feature.schedule.domain.WeekInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 周次信息的本地快照 */
data class CachedWeekInfo(
    val userCode: String,
    val currentWeek: Int,
    val totalWeeks: Int,
    val schoolYear: String,
    val schoolTerm: String,
    val cachedAtMillis: Long,
)

/**
 * 某一整周课表的本地快照。
 * weekDates 落地为 String 键，避免 Gson 处理非 String Map 键的不确定行为。
 */
data class CachedCourseWeek(
    val userCode: String,
    val schoolYear: String,
    val schoolTerm: String,
    val week: Int,
    val cachedAtMillis: Long,
    val courses: List<Course>,
    val weekDates: Map<String, String>,
)

/**
 * 课表本地快照：单 JSON 文件 + Mutex，与 RouteTemplateStore 同构（项目决定不引入 Room）。
 *
 * 课表接口全是 POST 表单，OkHttp 的 HTTP 缓存不会命中，所以断网可用只能落在应用层。
 * 写入只发生在网络成功之后，读取只发生在网络失败之时。
 */
@Singleton
class ScheduleCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file: File by lazy { File(context.filesDir, FILE_NAME) }
    private val gson = Gson()
    private val mutex = Mutex()

    /** 首次读盘后驻内存，周次切换时不再重复 IO */
    private var memory: MutableCache? = null

    suspend fun saveWeekInfo(snapshot: CachedWeekInfo) = mutex.withLock {
        val cache = readLocked()
        cache.weekInfo[snapshot.userCode] = snapshot
        writeLocked(cache)
    }

    /**
     * 保存某一周的课表，同时把课程缓存收敛到「当前账号 + 当前学期」：
     * 一学期只有二十余周，既控制文件体积，也让换账号后读不到上一位使用者的课表。
     */
    suspend fun saveCourses(snapshot: CachedCourseWeek) = mutex.withLock {
        val cache = readLocked()
        val kept = pruneToTerm(cache.courses, snapshot)
        cache.courses.clear()
        cache.courses.putAll(kept)
        cache.courses[courseCacheKey(snapshot.userCode, snapshot.schoolYear, snapshot.schoolTerm, snapshot.week)] =
            snapshot
        writeLocked(cache)
    }

    suspend fun weekInfo(userCode: String): CachedWeekInfo? =
        mutex.withLock { readLocked().weekInfo[userCode] }

    suspend fun courses(
        userCode: String,
        schoolYear: String,
        schoolTerm: String,
        week: Int,
    ): CachedCourseWeek? = mutex.withLock {
        readLocked().courses[courseCacheKey(userCode, schoolYear, schoolTerm, week)]
    }

    // ── 下面两个函数自身不加锁，调用方必须持有 mutex ──

    private suspend fun readLocked(): MutableCache {
        memory?.let { return it }
        val raw = if (file.exists()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    gson.fromJson(file.readText(), ScheduleCacheFile::class.java)
                }.getOrNull()
            }
        } else {
            null
        }
        return MutableCache(
            weekInfo = raw?.weekInfo?.toMutableMap() ?: mutableMapOf(),
            courses = raw?.courses?.toMutableMap() ?: mutableMapOf(),
        ).also { memory = it }
    }

    private suspend fun writeLocked(cache: MutableCache) {
        memory = cache
        val json = gson.toJson(ScheduleCacheFile(cache.weekInfo.toMap(), cache.courses.toMap()))
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json)
            // renameTo 本身原子覆盖；先 delete 会在 rename 失败时把已有快照一起丢掉
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }

    private data class MutableCache(
        val weekInfo: MutableMap<String, CachedWeekInfo>,
        val courses: MutableMap<String, CachedCourseWeek>,
    )

    companion object {
        private const val FILE_NAME = "schedule_cache.json"
    }
}

/** Gson 落地结构；字段可空以容忍残缺或历史文件 */
internal data class ScheduleCacheFile(
    val weekInfo: Map<String, CachedWeekInfo>? = null,
    val courses: Map<String, CachedCourseWeek>? = null,
)

internal fun courseCacheKey(
    userCode: String,
    schoolYear: String,
    schoolTerm: String,
    week: Int,
): String = "$userCode#$schoolYear#$schoolTerm#$week"

internal fun pruneToTerm(
    courses: Map<String, CachedCourseWeek>,
    keep: CachedCourseWeek,
): Map<String, CachedCourseWeek> = courses.filterValues {
    it.userCode == keep.userCode &&
        it.schoolYear == keep.schoolYear &&
        it.schoolTerm == keep.schoolTerm
}

internal fun CachedCourseWeek.toScheduleData(): ScheduleData = ScheduleData(
    courses = courses,
    weekDates = weekDates.mapNotNull { (day, date) -> day.toIntOrNull()?.let { it to date } }.toMap(),
    cachedAtMillis = cachedAtMillis,
)

internal fun CachedWeekInfo.toWeekInfo(): WeekInfo = WeekInfo(
    currentWeek = currentWeek,
    totalWeeks = totalWeeks,
    schoolYear = schoolYear,
    schoolTerm = schoolTerm,
    cachedAtMillis = cachedAtMillis,
)

package com.lightxin.feature.schedule.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.CshRetrofit
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.feature.schedule.domain.ScheduleData
import com.lightxin.feature.schedule.domain.WeekInfo
import com.lightxin.feature.schedule.domain.mergeSpecialSections
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.Retrofit
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val api: ScheduleApi,
    private val tokenManager: TokenManager,
    private val cacheStore: ScheduleCacheStore,
    private val specialStore: SpecialSectionStore,
) {
    suspend fun getWeekInfo(): Result<WeekInfo> {
        val userCode = tokenManager.getUserCode().orEmpty()
        return try {
            val response = api.getWeekList()
            val data = response.data
            val rows = response.rows

            if (data == null) {
                throw IllegalStateException("获取周次信息失败")
            }
            val weekInfo = WeekInfo(
                currentWeek = data.week?.toIntOrNull() ?: 1,
                totalWeeks = rows?.size ?: 18,
                schoolYear = data.schoolYear ?: "",
                schoolTerm = data.schoolTerm ?: "",
            )
            if (userCode.isNotBlank()) {
                cacheStore.saveWeekInfo(
                    CachedWeekInfo(
                        userCode = userCode,
                        currentWeek = weekInfo.currentWeek,
                        totalWeeks = weekInfo.totalWeeks,
                        schoolYear = weekInfo.schoolYear,
                        schoolTerm = weekInfo.schoolTerm,
                        cachedAtMillis = System.currentTimeMillis(),
                    )
                )
            }
            Result.success(weekInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = userCode.takeIf { it.isNotBlank() }?.let { cacheStore.weekInfo(it) }
            if (cached != null) {
                Result.success(cached.toWeekInfo())
            } else {
                Result.failure(Exception(mapErrorMessage("获取周次", e), e))
            }
        }
    }

    suspend fun getCourses(
        schoolYear: String,
        schoolTerm: String,
        week: Int,
    ): Result<ScheduleData> {
        return try {
            val userCode = tokenManager.getUserCode().orEmpty()
            if (userCode.isBlank()) {
                return Result.failure(Exception("登录信息已失效，请重新登录"))
            }

            val response = api.getTimeTable(
                userCode = userCode,
                schoolYear = schoolYear,
                schoolTerm = schoolTerm,
                week = week.toString(),
            )

            val courses = mutableListOf<Course>()
            val weekDates = mutableMapOf<Int, String>()
            response.rows?.forEach { day ->
                val dayOfWeek = day.xq?.toIntOrNull() ?: return@forEach
                if (day.rq != null) weekDates[dayOfWeek] = day.rq
                day.kcVoList?.forEach { vo ->
                    val startSection = vo.ksjc?.toIntOrNull() ?: return@forEach
                    val endSection = vo.jsjc?.toIntOrNull() ?: startSection
                    courses.add(
                        Course(
                            name = vo.kcmc ?: "",
                            startSection = startSection,
                            endSection = endSection,
                            room = vo.jsmc ?: "",
                            teacher = vo.teacherName ?: "",
                            dayOfWeek = dayOfWeek,
                        )
                    )
                }
            }

            val data = ScheduleData(courses = courses, weekDates = weekDates)
            cacheStore.saveCourses(
                CachedCourseWeek(
                    userCode = userCode,
                    schoolYear = schoolYear,
                    schoolTerm = schoolTerm,
                    week = week,
                    cachedAtMillis = System.currentTimeMillis(),
                    courses = courses.toList(),
                    weekDates = weekDates.mapKeys { (day, _) -> day.toString() },
                )
            )
            // 缓存只存服务端结果，用户自设的早/晚行在出口处才并进 —— 每所班级不同、也不该被写进别人的快照
            Result.success(data.withSpecialSections())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = cachedCourses(schoolYear, schoolTerm, week)
            if (cached != null) {
                Result.success(cached.toScheduleData().withSpecialSections())
            } else {
                Result.failure(Exception(mapErrorMessage("加载课表", e), e))
            }
        }
    }

    private suspend fun ScheduleData.withSpecialSections(): ScheduleData =
        copy(
            courses = mergeSpecialSections(
                serverCourses = courses,
                settings = specialStore.current(),
                eveningPausedOn = specialStore.eveningPausedOn.first(),
                skipFriSat = specialStore.eveningSkipFriSat.first(),
            ),
        )

    private suspend fun cachedCourses(
        schoolYear: String,
        schoolTerm: String,
        week: Int,
    ): CachedCourseWeek? {
        val userCode = tokenManager.getUserCode().orEmpty()
        if (userCode.isBlank()) return null
        return cacheStore.courses(userCode, schoolYear, schoolTerm, week)
    }

    private fun mapErrorMessage(action: String, error: Exception): String {
        return when (error) {
            is HttpException -> when {
                error.code() == 401 -> "登录已失效，请重新登录"
                error.code() >= 500 -> "${action}接口暂时异常，请稍后重试"
                else -> "${action}失败（HTTP ${error.code()}）"
            }

            is IOException -> "网络异常，请检查连接后重试"
            else -> error.message ?: "${action}失败"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ScheduleModule {

    @Provides
    @Singleton
    fun provideScheduleApi(@CshRetrofit retrofit: Retrofit): ScheduleApi =
        retrofit.create(ScheduleApi::class.java)
}

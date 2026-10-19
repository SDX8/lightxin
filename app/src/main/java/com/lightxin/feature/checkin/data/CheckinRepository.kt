package com.lightxin.feature.checkin.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.CheckinRetrofit
import com.lightxin.core.network.fdyglFailureMessage
import com.lightxin.core.network.fdyglSucceeded
import com.lightxin.feature.checkin.domain.CheckinHistoryRecord
import com.lightxin.feature.checkin.domain.CheckinMonthHistory
import com.lightxin.feature.checkin.domain.CheckinMonthSummary
import com.lightxin.feature.checkin.domain.CheckinRecordStatus
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.checkin.domain.TaskDetail
import com.lightxin.feature.checkin.domain.sortCheckinHistory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_SIZE_HISTORY = 50

@Singleton
class CheckinRepository @Inject constructor(
    private val api: CheckinApi,
    private val fileApi: FileUploadApi,
    private val tokenManager: TokenManager,
) {
    suspend fun getTasks(page: Int, pageSize: Int = 10): Result<List<CheckinTask>> {
        return try {
            val body = mapOf<String, Any>(
                "pageNum" to page,
                "pageSize" to pageSize,
                "type" to "1",
                "taskMajorType" to "3",
            )
            val response = api.pageStudentSignIn(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(fdyglFailureMessage(response.msg, "获取签到任务失败")))
            }
            val list = response.rows ?: response.data?.list.orEmpty()

            Result.success(
                list.map { row ->
                    CheckinTask(
                        id = row.id ?: "",
                        taskName = row.title ?: row.taskName ?: "",
                        taskDateId = row.taskDateId ?: row.id ?: row.userTaskId ?: row.taskId ?: "",
                        isSigned = row.executionedStatus == "1" || row.signinStatus == "1",
                        startTime = row.timedStartTime ?: row.startTime ?: row.collectionStartTime ?: "",
                        endTime = row.timedEndTime ?: row.endTime ?: row.collectionEndTime ?: "",
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取签到任务", e), e))
        }
    }

    /**
     * 查寝历史（按月）。服务端每个状态档各查一次，行里不回状态，
     * 所以状态由查询条件带回来 —— 与官方 H5 历史页的分页签一致。
     */
    suspend fun getMonthHistory(month: String): Result<CheckinMonthHistory> {
        val userCode = tokenManager.getUserCode()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("缺少学号，请重新登录"))
        return try {
            val counts = api.collectionStudentStatics(monthBody(month, userCode))
            if (!fdyglSucceeded(counts.code, counts.flag)) {
                return Result.failure(Exception(fdyglFailureMessage(counts.msg, "获取查寝汇总失败")))
            }
            val records = coroutineScope {
                CheckinRecordStatus.entries.map { status ->
                    async { fetchStatics(month, userCode, status) }
                }.map { it.await() }
            }.flatten()
            Result.success(
                CheckinMonthHistory(
                    records = sortCheckinHistory(records),
                    summary = CheckinMonthSummary(
                        dueCount = counts.data?.signiningNum ?: 0,
                        counts = mapOf(
                            CheckinRecordStatus.SIGNED to (counts.data?.signinedNum ?: 0),
                            CheckinRecordStatus.LEAVE to (counts.data?.leaveNum ?: 0),
                            CheckinRecordStatus.NOT_SIGNED to (counts.data?.notSigninedNum ?: 0),
                            CheckinRecordStatus.LEAVE_SCHOOL to (counts.data?.leaveSchoolNum ?: 0),
                        ),
                    ),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取查寝历史", e), e))
        }
    }

    private suspend fun fetchStatics(
        month: String,
        userCode: String,
        status: CheckinRecordStatus,
    ): List<CheckinHistoryRecord> {
        val response = api.pageStudentStatics(monthBody(month, userCode) + ("executionedStatus" to status.code))
        if (!fdyglSucceeded(response.code, response.flag)) {
            throw Exception(fdyglFailureMessage(response.msg, "获取查寝历史失败"))
        }
        return response.rows.orEmpty().map { row ->
            CheckinHistoryRecord(
                id = row.id ?: "",
                taskName = row.title.orEmpty(),
                dateLabel = row.taskDateStr.orEmpty(),
                time = row.timedStartTime.orEmpty().substringAfter(' ').take(5),
                place = row.signinPlace.orEmpty(),
                hasPhoto = !row.signinPhoto.isNullOrBlank(),
                status = status,
            )
        }
    }

    private fun monthBody(month: String, userCode: String) = mapOf<String, Any>(
        "pageNum" to 1,
        "pageSize" to PAGE_SIZE_HISTORY,
        "taskMajorType" to "3",
        "createBy" to userCode,
        "staticDate" to month,
    )

    suspend fun getTaskDetail(dateId: String): Result<TaskDetail> {
        return try {
            val body = mapOf("dateId" to dateId)
            val response = api.getTaskInfoByDateId(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(fdyglFailureMessage(response.msg, "获取任务详情失败")))
            }
            val data = response.data
                ?: return Result.failure(Exception("获取任务详情失败"))
            val firstLocation = data.signinLocations?.firstOrNull()
            val (centerLng, centerLat) = firstLocation?.lngLat
                ?.split(",")
                ?.let { parts ->
                    val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                    val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    lng to lat
                } ?: (data.lng?.toDoubleOrNull() ?: 0.0) to (data.lat?.toDoubleOrNull() ?: 0.0)

            Result.success(
                TaskDetail(
                    taskName = data.title ?: data.taskName ?: "",
                    taskDateId = data.dateId ?: data.taskDateId ?: dateId,
                    startTime = data.timedStartTime ?: data.startTime ?: data.collectionStartTime ?: "",
                    endTime = data.timedEndTime ?: data.endTime ?: data.collectionEndTime ?: "",
                    needPhoto = data.photoRequire == "1" || data.signinPhoto == "1" || data.needPhoto == "1",
                    isSigned = data.signStatus == "1" || data.signinStatus == "1",
                    signinPlace = firstLocation?.signinLocation ?: data.signinPlace ?: "",
                    locationRange = firstLocation?.content?.toString()?.toDoubleOrNull()
                        ?: data.locationRange?.toDoubleOrNull()
                        ?: 0.0,
                    centerLng = centerLng,
                    centerLat = centerLat,
                    address = firstLocation?.signinLocation ?: data.address ?: "",
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取任务详情", e), e))
        }
    }

    suspend fun uploadPhoto(file: File): Result<String> {
        return try {
            val requestBody = file.asRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            val response = fileApi.uploadFile(part)

            val url = response.data.extractUploadUrl()
            if (url.isNullOrBlank() || !fdyglSucceeded(response.code, response.flag)) {
                Result.failure(Exception(fdyglFailureMessage(response.msg, "照片上传失败")))
            } else {
                Result.success(url)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapError("上传照片", e), e))
        }
    }

    suspend fun submitSignIn(
        taskDateId: String,
        photoUrl: String,
        place: String,
        lngLatString: String,   // "经度,纬度" BD-09格式
    ): Result<Unit> {
        return try {
            val body = mapOf(
                "taskDateId" to taskDateId,
                "signinPhoto" to photoUrl,
                "signinPlace" to place,
                "latLng" to lngLatString,
                "outSignin" to "0",
                "outSigninDesc" to "",
            )
            val response = api.signIn(body)
            if (response.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(fdyglFailureMessage(response.msg, "签到失败")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(mapError("提交签到", e), e))
        }
    }

    private fun mapError(action: String, error: Exception): String {
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

    private fun SignInPageResponse.isSuccess(): Boolean = fdyglSucceeded(code, flag)

    private fun TaskInfoResponse.isSuccess(): Boolean = fdyglSucceeded(code, flag)

    private fun SignInSubmitResponse.isSuccess(): Boolean = fdyglSucceeded(code, flag)

    private fun JsonElement?.extractUploadUrl(): String? {
        val element = this ?: return null
        if (element.isJsonNull) return null
        if (element.isJsonPrimitive) return element.asString
        if (!element.isJsonObject) return null

        val obj = element.asJsonObject
        val commonKeys = listOf("url", "fileUrl", "fullPath", "path", "filePath")
        commonKeys.forEach { key ->
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }

        obj.entrySet().forEach { (_, value) ->
            if (value.isJsonPrimitive) {
                val text = value.asString
                if (text.startsWith("http://") || text.startsWith("https://") || text.contains("/group")) {
                    return text
                }
            }
        }

        return null
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CheckinModule {

    @Provides
    @Singleton
    fun provideCheckinApi(@CheckinRetrofit retrofit: Retrofit): CheckinApi =
        retrofit.create(CheckinApi::class.java)

    @Provides
    @Singleton
    fun provideFileUploadApi(@CheckinRetrofit retrofit: Retrofit): FileUploadApi =
        retrofit.create(FileUploadApi::class.java)
}

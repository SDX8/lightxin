package com.lightxin.core.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profilePrefs: DataStore<Preferences> by preferencesDataStore(name = "lightxin_profile")

/**
 * 用户自己填的资料。刻意与 [TokenManager] 里的 `userName` 分开：那个是服务端下发的真实姓名，
 * 还要作为 `userName` 参数发回接口，被昵称覆盖会同时弄坏显示与请求。
 */
data class LocalProfile(
    val nickname: String = "",
    val avatarFileName: String? = null,
)

@Singleton
class ProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val avatarMutex = Mutex()

    val profile: Flow<LocalProfile> = context.profilePrefs.data.map { prefs ->
        val name = prefs[KEY_AVATAR_FILE]
        LocalProfile(
            nickname = prefs[KEY_NICKNAME].orEmpty(),
            avatarFileName = name?.takeIf { avatarFile(it).exists() },
        )
    }

    /** 传空字符串即清除昵称，回落到真实姓名 */
    suspend fun setNickname(raw: String) {
        val nickname = sanitizeNickname(raw)
        context.profilePrefs.edit { prefs ->
            if (nickname.isEmpty()) {
                prefs.remove(KEY_NICKNAME)
            } else {
                prefs[KEY_NICKNAME] = nickname
            }
        }
    }

    /**
     * 把相册选中的图拷进应用私有目录并压到头像尺寸。
     * Photo Picker 给的 content Uri 只有临时读权限，必须当场复制，不能记 Uri。
     */
    suspend fun saveAvatar(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        avatarMutex.withLock {
            runCatching<Unit> {
                val raw = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                    ?: error("无法读取所选图片")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_AVATAR_EDGE)
                }
                val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, decodeOptions)
                    ?: error("图片解码失败，换一张试试")
                val bitmap = scaleToMaxEdge(decoded, MAX_AVATAR_EDGE)
                val target = avatarFile(AVATAR_FILE_NAME)
                val tmp = File(context.filesDir, "$AVATAR_FILE_NAME.tmp")
                tmp.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (bitmap !== decoded) bitmap.recycle()
                decoded.recycle()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    error("头像保存失败")
                }
                context.profilePrefs.edit { it[KEY_AVATAR_FILE] = AVATAR_FILE_NAME }
            }.onFailure {
                Log.w(PROFILE_LOG_TAG, "头像保存失败", it)
            }
        }
    }

    suspend fun clearAvatar() {
        withContext(Dispatchers.IO) {
            avatarMutex.withLock {
                avatarFile(AVATAR_FILE_NAME).delete()
                context.profilePrefs.edit { it.remove(KEY_AVATAR_FILE) }
            }
        }
    }

    fun avatarFile(fileName: String): File = File(context.filesDir, fileName)

    /** 换账号时必须清掉，否则下一个登录者会顶着上一个人的昵称和头像 */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            avatarMutex.withLock {
                context.profilePrefs.edit { it.clear() }
                avatarFile(AVATAR_FILE_NAME).delete()
            }
        }
    }

    private companion object {
        val KEY_NICKNAME = stringPreferencesKey("nickname")
        val KEY_AVATAR_FILE = stringPreferencesKey("avatar_file")

        const val AVATAR_FILE_NAME = "profile_avatar.jpg"
        const val MAX_AVATAR_EDGE = 512
        const val JPEG_QUALITY = 88
        const val PROFILE_LOG_TAG = "ProfileStore"
    }
}

/** 只留一行、去首尾空白并截断，避免粘贴多行文本撑破布局 */
internal fun sanitizeNickname(raw: String): String =
    raw.lineSequence().firstOrNull().orEmpty().trim().take(MAX_NICKNAME_LENGTH)

/** 昵称优先，没设过就用服务端真实姓名 */
internal fun resolveDisplayName(nickname: String, serverName: String): String =
    nickname.ifBlank { serverName }

internal fun avatarInitial(name: String): String =
    name.trim().firstOrNull()?.toString() ?: "?"

/** BitmapFactory 的 inSampleSize 只按 2 的幂降采样，这里取不越过上限的最大一档 */
internal fun computeInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= maxEdge || height / (sample * 2) >= maxEdge) {
        sample *= 2
    }
    return sample
}

private const val MAX_NICKNAME_LENGTH = 12

private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= maxEdge || longest == 0) return bitmap
    val ratio = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).toInt().coerceAtLeast(1),
        (bitmap.height * ratio).toInt().coerceAtLeast(1),
        true,
    )
}

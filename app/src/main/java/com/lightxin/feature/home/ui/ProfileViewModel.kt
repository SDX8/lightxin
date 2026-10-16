package com.lightxin.feature.home.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.auth.ProfileStore
import com.lightxin.core.auth.SessionManager
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.auth.resolveDisplayName
import com.lightxin.core.network.FifSessionManager
import com.lightxin.core.settings.DeveloperPrefs
import com.lightxin.core.settings.UpdatePrefs
import com.lightxin.feature.update.domain.AppVersion
import com.lightxin.feature.update.domain.AppVersionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userCode: String = "",
    val realName: String = "",
    val nickname: String = "",
    val avatarFileName: String? = null,
    val profileError: String? = null,
    val isLoggingOut: Boolean = false,
    val advancedEnabled: Boolean = false,
    val updateHint: String? = null,
) {
    /** 昵称优先，没设过才显示服务端真实姓名 */
    val displayName: String get() = resolveDisplayName(nickname, realName)

    /** 大字换成昵称后，小字那行把真名带回来，免得忘了自己顶着的是谁 */
    val subtitleLine: String
        get() = if (nickname.isNotBlank() && realName.isNotBlank()) {
            "$realName · $userCode"
        } else {
            userCode.ifBlank { "---" }
        }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager,
    private val developerPrefs: DeveloperPrefs,
    private val updatePrefs: UpdatePrefs,
    private val appVersionProvider: AppVersionProvider,
    private val fifSession: FifSessionManager,
    private val profileStore: ProfileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    userCode = tokenManager.getUserCode().orEmpty(),
                    realName = tokenManager.getUserName().orEmpty(),
                )
            }
        }
        viewModelScope.launch {
            profileStore.profile.collectLatest { profile ->
                _uiState.update {
                    it.copy(nickname = profile.nickname, avatarFileName = profile.avatarFileName)
                }
            }
        }
        viewModelScope.launch {
            developerPrefs.isAdvancedEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(advancedEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                updatePrefs.latestVersion,
                updatePrefs.skippedVersion,
            ) { latest, skipped -> latest to skipped }
                .collectLatest { (latest, skipped) ->
                    val hint = computeUpdateHint(latest, skipped)
                    _uiState.update { it.copy(updateHint = hint) }
                }
        }
    }

    private fun computeUpdateHint(latest: String, skipped: String): String? {
        if (latest.isBlank()) return null
        val remoteVersion = AppVersion.parse(latest) ?: return null
        val localVersion = appVersionProvider.appVersion ?: return null
        if (remoteVersion <= localVersion) return null
        if (latest == skipped) return null
        return "发现新版本 v$latest"
    }

    fun saveNickname(raw: String) {
        viewModelScope.launch {
            profileStore.setNickname(raw)
            _uiState.update { it.copy(profileError = null) }
        }
    }

    fun pickAvatar(uri: Uri) {
        viewModelScope.launch {
            profileStore.saveAvatar(uri).onFailure { error ->
                _uiState.update { it.copy(profileError = error.message ?: "头像保存失败") }
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch { profileStore.clearAvatar() }
    }

    fun dismissProfileError() {
        _uiState.update { it.copy(profileError = null) }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            sessionManager.logout()
            // FIF 身份存在自己的 DataStore 里，不清会串到下一个登录账号
            fifSession.clear()
            // 昵称与头像同理：换账号不该顶着上一个人的资料
            profileStore.clear()
            onDone()
        }
    }
}

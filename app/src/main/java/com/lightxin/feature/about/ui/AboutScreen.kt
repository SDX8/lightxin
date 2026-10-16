package com.lightxin.feature.about.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.R
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDialog
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.NewsreaderDisplay
import com.lightxin.core.settings.DeveloperPasscode

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPasscodeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "关于轻小信", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp),
        ) {
            BrandCard(versionName = uiState.versionName)

            Spacer(modifier = Modifier.height(22.dp))

            LxCard {
                Column {
                    UpdateCheckRow(
                        state = uiState.updateCheckState,
                        onCheck = { viewModel.checkForUpdate() },
                        onDownload = { viewModel.downloadUpdate() },
                    )
                    CardDivider()
                    DeveloperToggleRow(
                        enabled = uiState.advancedEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !uiState.advancedEnabled) {
                                showPasscodeDialog = true
                            } else if (!checked) {
                                viewModel.setAdvancedEnabled(false)
                            }
                        },
                    )
                    CardDivider()
                    GitHubRow()
                }
            }
        }
    }

    if (showPasscodeDialog) {
        DeveloperPasscodeDialog(
            onDismiss = { showPasscodeDialog = false },
            onPassed = {
                showPasscodeDialog = false
                viewModel.setAdvancedEnabled(true)
            },
        )
    }
}

/**
 * 下面是打开开发者模式的功能，防止都能打开路径模拟。
 * 校验本身只是客户端拦截，见 [DeveloperPasscode] 的说明。
 */
@Composable
private fun DeveloperPasscodeDialog(
    onDismiss: () -> Unit,
    onPassed: () -> Unit,
) {
    var passcode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val submit: () -> Unit = {
        if (DeveloperPasscode.verify(passcode)) {
            onPassed()
        } else {
            errorMessage = "密码不正确"
        }
    }

    LxDialog(
        title = "Enable Developer Mode",
        message = "功能仅供开发调试",
        confirmText = "启用",
        dismissText = "取消",
        onDismissRequest = onDismiss,
        onDismiss = onDismiss,
        onConfirm = submit,
        content = {
            LxTextField(
                value = passcode,
                onValueChange = {
                    passcode = it
                    errorMessage = null
                },
                label = "调试密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            errorMessage?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = LxRose,
                )
            }
        },
    )
}

@Composable
private fun BrandCard(versionName: String) {
    LxCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(80.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(1.45f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "轻小信",
                fontFamily = NewsreaderDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                color = LxTerra,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "让校园生活更轻盈",
                fontSize = 13.sp,
                color = LxInkMuted,
            )
            if (versionName.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "v$versionName",
                    fontSize = 12.sp,
                    color = LxInkMuted,
                )
            }
        }
    }
}

@Composable
private fun DeveloperToggleRow(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Enable Developer Mode",
            fontSize = 15.sp,
            color = LxInk,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = LxTerra,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = LxRose.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun UpdateCheckRow(
    state: UpdateCheckState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state !is UpdateCheckState.Checking) { onCheck() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "检测更新",
            fontSize = 15.sp,
            color = LxInk,
        )
        when (state) {
            is UpdateCheckState.Idle -> {}
            is UpdateCheckState.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LxTerra,
                )
            }
            is UpdateCheckState.HasUpdate -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "v${state.versionName}",
                        fontSize = 13.sp,
                        color = LxTerra,
                    )
                    Text(
                        text = "立即更新",
                        fontSize = 13.sp,
                        color = LxTerra,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDownload() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            is UpdateCheckState.AlreadyLatest -> {
                Text(
                    text = "已是最新版本",
                    fontSize = 13.sp,
                    color = LxInkMuted,
                )
            }
            is UpdateCheckState.Failed -> {
                Text(
                    text = "检测失败，请稍后再试",
                    fontSize = 13.sp,
                    color = LxRose,
                )
            }
        }
    }
}

@Composable
private fun GitHubRow() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Relianttt/lightxin"))
                context.startActivity(intent)
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "GitHub 仓库",
            fontSize = 15.sp,
            color = LxInk,
        )
        Text(
            text = "›",
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            color = LxInkMuted,
        )
    }
}

@Composable
private fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(LxSand),
    )
}

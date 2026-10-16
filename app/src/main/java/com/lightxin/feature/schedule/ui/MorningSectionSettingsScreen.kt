package com.lightxin.feature.schedule.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxChoiceChip
import com.lightxin.core.designsystem.component.LxDialog
import com.lightxin.core.designsystem.component.LxDialogConfirmTone
import com.lightxin.core.designsystem.component.LxEmptyHint
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.NewsreaderDisplay
import com.lightxin.feature.schedule.domain.MORNING_DAY_NAMES
import com.lightxin.feature.schedule.domain.MorningSetting
import com.lightxin.feature.schedule.domain.MorningSlot
import com.lightxin.feature.schedule.domain.morningDaysLabel
import com.lightxin.feature.schedule.domain.morningTimeRange

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MorningSectionSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MorningSectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<MorningSetting?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "早自习 / 早锻炼", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp),
        ) {
            val draft = uiState.draft
            if (draft == null) {
                MorningSettingList(
                    settings = uiState.settings,
                    onEdit = viewModel::startEdit,
                    onDelete = { pendingDelete = it },
                )
                Spacer(modifier = Modifier.height(18.dp))
                LxButton(text = "添加早安排", onClick = viewModel::startAdd)
            } else {
                MorningSettingForm(
                    name = draft.name,
                    room = draft.room,
                    slot = draft.slot,
                    days = draft.days,
                    error = uiState.error,
                    isNew = draft.id == null,
                    onNameChange = viewModel::onNameChange,
                    onRoomChange = viewModel::onRoomChange,
                    onSlotChange = viewModel::onSlotChange,
                    onDayToggle = viewModel::onDayToggle,
                    onSave = viewModel::save,
                    onCancel = viewModel::cancelEdit,
                )
            }
        }
    }

    pendingDelete?.let { setting ->
        LxDialog(
            title = "删除「${setting.name}」",
            message = "课表上的第 0 节会一并消失。",
            confirmText = "删除",
            dismissText = "取消",
            onDismissRequest = { pendingDelete = null },
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(setting.id)
                pendingDelete = null
            },
            confirmTone = LxDialogConfirmTone.Destructive,
        )
    }
}

@Composable
private fun MorningSettingList(
    settings: List<MorningSetting>,
    onEdit: (MorningSetting) -> Unit,
    onDelete: (MorningSetting) -> Unit,
) {
    if (settings.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            LxEmptyHint(message = "还没有早安排")
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        settings.forEach { setting ->
            LxCard(onClick = { onEdit(setting) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = morningDaysLabel(setting.days).ifBlank { "未选星期" },
                            fontFamily = NewsreaderDisplay,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            color = LxInk,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = listOfNotNull(
                                setting.name,
                                morningTimeRange(setting.slot),
                                setting.room.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            fontSize = 12.sp,
                            color = LxInkMuted,
                        )
                    }
                    Text(
                        text = "删除",
                        fontSize = 13.sp,
                        color = LxRose,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDelete(setting) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MorningSettingForm(
    name: String,
    room: String,
    slot: MorningSlot,
    days: Set<Int>,
    error: String?,
    isNew: Boolean,
    onNameChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onSlotChange: (MorningSlot) -> Unit,
    onDayToggle: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = if (isNew) "新的早安排" else "编辑早安排",
            fontFamily = NewsreaderDisplay,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            color = LxTerra,
        )

        LxTextField(
            value = name,
            onValueChange = onNameChange,
            label = "名称",
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "时段", style = MaterialTheme.typography.labelMedium, color = LxInkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MorningSlot.entries.forEach { option ->
                    LxChoiceChip(
                        text = option.label,
                        selected = option == slot,
                        onClick = { onSlotChange(option) },
                    )
                }
            }
            Text(
                text = morningTimeRange(slot),
                fontSize = 12.sp,
                color = LxInkMuted,
            )
        }

        LxTextField(
            value = room,
            onValueChange = onRoomChange,
            label = "地点",
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "星期", style = MaterialTheme.typography.labelMedium, color = LxInkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MORNING_DAY_NAMES.forEach { (day, label) ->
                    LxChoiceChip(
                        text = label,
                        selected = day in days,
                        onClick = { onDayToggle(day) },
                    )
                }
            }
        }

        if (error != null) {
            Text(text = error, fontSize = 12.sp, color = LxRose)
        }

        LxButton(text = "保存", onClick = onSave)
        Text(
            text = "取消",
            fontSize = 14.sp,
            color = LxInkMuted,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

package com.lightxin.feature.schedule.ui

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
import androidx.compose.material3.Switch
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
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.NewsreaderDisplay
import com.lightxin.feature.schedule.domain.DAY_NAMES
import com.lightxin.feature.schedule.domain.SectionKind
import com.lightxin.feature.schedule.domain.SpecialItem
import com.lightxin.feature.schedule.domain.SpecialSlot
import com.lightxin.feature.schedule.domain.allowedDaysFor
import com.lightxin.feature.schedule.domain.specialDaysLabel
import com.lightxin.feature.schedule.domain.specialTimeRange

@Composable
fun SpecialSectionSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpecialSectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<SpecialItem?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "早自习 / 晚自习", onBack = onBack) },
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
                SpecialItemList(
                    settings = uiState.settings,
                    onEdit = viewModel::startEdit,
                    onDelete = { pendingDelete = it },
                )
                if (uiState.hasEvening) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SwitchRow(
                            title = "周五、周六无晚自习",
                            checked = uiState.eveningSkipFriSat,
                            onCheckedChange = viewModel::setEveningSkipFriSat,
                        )
                        SwitchRow(
                            title = "今晚无晚自习",
                            checked = uiState.eveningPausedToday,
                            onCheckedChange = viewModel::setEveningPaused,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LxOutlinedButton(
                        text = "添加早安排",
                        onClick = { viewModel.startAdd(SectionKind.MORNING) },
                        modifier = Modifier.weight(1f),
                    )
                    LxButton(
                        text = "添加晚自习",
                        onClick = { viewModel.startAdd(SectionKind.EVENING) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                SpecialItemForm(
                    draft = draft,
                    allowedDays = allowedDaysFor(draft.slot.kind, uiState.eveningSkipFriSat),
                    error = uiState.error,
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
        val rowLabel = if (setting.slot.kind == SectionKind.MORNING) "早" else "晚"
        LxDialog(
            title = "删除「${setting.name}」",
            message = "课表上「$rowLabel」那一行会一并消失。",
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
private fun SpecialItemList(
    settings: List<SpecialItem>,
    onEdit: (SpecialItem) -> Unit,
    onDelete: (SpecialItem) -> Unit,
) {
    if (settings.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            LxEmptyHint(message = "还没有早/晚安排")
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
                            text = specialDaysLabel(setting.days).ifBlank { "未选星期" },
                            fontFamily = NewsreaderDisplay,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            color = LxInk,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = listOfNotNull(
                                setting.name,
                                specialTimeRange(setting.slot),
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

/** 一行一个开关：整块卡片可点，不额外配解释性小字 */
@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    LxCard(onClick = { onCheckedChange(!checked) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = LxInk,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecialItemForm(
    draft: SpecialDraft,
    allowedDays: Set<Int>,
    error: String?,
    onNameChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onSlotChange: (SpecialSlot) -> Unit,
    onDayToggle: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val kindLabel = if (draft.slot.kind == SectionKind.MORNING) "早安排" else "晚自习"
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = (if (draft.id == null) "新的" else "编辑") + kindLabel,
            fontFamily = NewsreaderDisplay,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            color = LxTerra,
        )

        LxTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = "名称",
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "时段", style = MaterialTheme.typography.labelMedium, color = LxInkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpecialSlot.entries.filter { it.kind == draft.slot.kind }.forEach { option ->
                    LxChoiceChip(
                        text = option.label,
                        selected = option == draft.slot,
                        onClick = { onSlotChange(option) },
                    )
                }
            }
            Text(
                text = specialTimeRange(draft.slot),
                fontSize = 12.sp,
                color = LxInkMuted,
            )
        }

        LxTextField(
            value = draft.room,
            onValueChange = onRoomChange,
            label = "地点",
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "星期", style = MaterialTheme.typography.labelMedium, color = LxInkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DAY_NAMES.filterKeys { it in allowedDays }.forEach { (day, label) ->
                    LxChoiceChip(
                        text = label,
                        selected = day in draft.days,
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

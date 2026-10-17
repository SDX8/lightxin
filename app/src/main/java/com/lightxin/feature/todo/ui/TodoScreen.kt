package com.lightxin.feature.todo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxChoiceChip
import com.lightxin.core.designsystem.component.LxDialog
import com.lightxin.core.designsystem.component.LxDialogConfirmTone
import com.lightxin.core.designsystem.component.LxEmptyHint
import com.lightxin.core.designsystem.component.LxFloatingActionButton
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkFaint
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxSageSoft
import com.lightxin.core.designsystem.theme.LxSandDeep
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.NewsreaderDisplay
import com.lightxin.core.designsystem.theme.NewsreaderLarge
import com.lightxin.feature.todo.domain.TodoItem
import com.lightxin.feature.todo.domain.TodoSections
import com.lightxin.feature.todo.domain.dueLabel
import com.lightxin.feature.todo.domain.isOverdue
import com.lightxin.feature.todo.domain.todoMetaLine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 左滑露出的删除键宽度，滑过一半就自己吸住 */
private val DELETE_REVEAL_WIDTH = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    modifier: Modifier = Modifier,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sections = uiState.sections
    val today = uiState.today
    var pendingDelete by remember { mutableStateOf<TodoItem?>(null) }
    // 左滑之后还要再点一下删除，已经是两道有意的动作，不再叠第三层确认
    val delete: (TodoItem) -> Unit = { item -> viewModel.delete(item.id) }
    // 切走 tab 时组合被销毁，此刻才把"刚勾完"的条目归到已完成段（ViewModel 挂在 HOME 上，不会跟着销毁）
    DisposableEffect(Unit) {
        onDispose { viewModel.settleCompleted() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 96.dp),
        ) {
            TodoHeader(sections, today)
            Spacer(modifier = Modifier.height(18.dp))

            if (sections.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LxEmptyHint(message = "还没有待办，点右下角记一条")
                }
            } else {
                TodoSection("今天", sections.today, today, viewModel::startEdit, viewModel::setDone, delete)
                TodoSection("以后", sections.later, today, viewModel::startEdit, viewModel::setDone, delete)
                if (sections.done.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(text = "已完成 ${sections.done.size} 条", modifier = Modifier.weight(1f))
                        Text(
                            text = "清空",
                            fontSize = 13.sp,
                            color = LxInkMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = viewModel::clearDone)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sections.done.forEach { item ->
                            TodoRow(item, today, viewModel::startEdit, viewModel::setDone, delete)
                        }
                    }
                }
            }
        }

        LxFloatingActionButton(
            onClick = viewModel::startAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "添加待办", tint = Color.White)
        }
    }

    uiState.draft?.let { draft ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::cancelEdit,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
        ) {
            TodoEditor(
                draft = draft,
                today = today,
                error = uiState.error,
                onTitleChange = viewModel::onTitleChange,
                onSubjectChange = viewModel::onSubjectChange,
                onDueDateChange = viewModel::onDueDateChange,
                onSave = viewModel::save,
                onDelete = {
                    draft.id?.let { id -> pendingDelete = uiState.items.firstOrNull { it.id == id } }
                    viewModel.cancelEdit()
                },
            )
        }
    }

    pendingDelete?.let { item ->
        LxDialog(
            title = "删除「${item.title}」",
            message = "这条待办就此消失，不会留在已完成里。",
            confirmText = "删除",
            dismissText = "取消",
            onDismissRequest = { pendingDelete = null },
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(item.id)
                pendingDelete = null
            },
            confirmTone = LxDialogConfirmTone.Destructive,
        )
    }
}

@Composable
private fun TodoHeader(sections: TodoSections, today: LocalDate) {
    val overdueCount = sections.today.count { isOverdue(it, today) }
    Column {
        Text(
            text = "待办",
            fontFamily = NewsreaderLarge,
            fontWeight = FontWeight.Medium,
            fontSize = 28.sp,
            color = LxInk,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                sections.isEmpty -> "空空如也"
                overdueCount > 0 -> "还有 ${sections.openCount} 件，其中 $overdueCount 件已经逾期"
                sections.openCount == 0 -> "该做的都做完了"
                else -> "还有 ${sections.openCount} 件没做完"
            },
            fontSize = 13.sp,
            color = if (overdueCount > 0) LxRose else LxInkMuted,
        )
    }
}

@Composable
private fun TodoSection(
    title: String,
    items: List<TodoItem>,
    today: LocalDate,
    onEdit: (TodoItem) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (TodoItem) -> Unit,
) {
    if (items.isEmpty()) return
    SectionLabel(text = "$title ${items.size} 条")
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            TodoRow(item = item, today = today, onEdit = onEdit, onToggle = onToggle, onDelete = onDelete)
        }
    }
    Spacer(modifier = Modifier.height(22.dp))
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = LxInkFaint,
    )
}

@Composable
private fun TodoRow(
    item: TodoItem,
    today: LocalDate,
    onEdit: (TodoItem) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (TodoItem) -> Unit,
) {
    val revealPx = with(LocalDensity.current) { DELETE_REVEAL_WIDTH.toPx() }
    val slide = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val overdue = isOverdue(item, today)

    fun settleTo(target: Float) {
        scope.launch { slide.animateTo(target, tween(180)) }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(DELETE_REVEAL_WIDTH)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LxRose)
                    .clickable {
                        settleTo(0f)
                        onDelete(item)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "删除", fontSize = 14.sp, color = Color.White)
            }
        }

        LxCard(
            modifier = Modifier
                .offset { IntOffset(slide.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { settleTo(if (slide.value < -revealPx / 2) -revealPx else 0f) },
                        onDragCancel = { settleTo(0f) },
                    ) { _, dragAmount ->
                        scope.launch { slide.snapTo((slide.value + dragAmount).coerceIn(-revealPx, 0f)) }
                    }
                },
            onClick = {
                // 已经滑开了，第一下是收回去，不该顺手把编辑页也弹出来
                if (slide.value < 0f) settleTo(0f) else onEdit(item)
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DoneToggle(checked = item.done, onToggle = { onToggle(item.id, !item.done) })
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    StrikethroughText(text = item.title, struck = item.done)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = todoMetaLine(item, today),
                        fontSize = 12.sp,
                        color = when {
                            item.done -> LxInkFaint
                            overdue -> LxRose
                            else -> LxInkMuted
                        },
                    )
                }
            }
        }
    }
}

/**
 * 划掉是画出来的，不是 `TextDecoration.LineThrough` —— 后者只能整条瞬间出现，
 * 这里要的是勾选那一下从左往右长出来。按行画，两行标题也不会被一条长线横穿。
 */
@Composable
private fun StrikethroughText(text: String, struck: Boolean) {
    val progress by animateFloatAsState(if (struck) 1f else 0f, tween(260), label = "strike")
    val ink by animateColorAsState(if (struck) LxInkFaint else LxInk, tween(260), label = "strike-ink")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        modifier = Modifier.drawWithContent {
            drawContent()
            if (progress > 0f) {
                val result = layout
                repeat(result?.lineCount ?: 1) { line ->
                    val top = result?.getLineTop(line) ?: 0f
                    val bottom = result?.getLineBottom(line) ?: size.height
                    val left = result?.getLineLeft(line) ?: 0f
                    val right = result?.getLineRight(line) ?: size.width
                    val y = (top + bottom) / 2f
                    drawLine(
                        color = ink,
                        start = Offset(left, y),
                        end = Offset(left + (right - left) * progress, y),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
        },
        onTextLayout = { layout = it },
        fontFamily = NewsreaderDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        color = ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 设计系统里没有 Checkbox，行内这一个圆点就是勾选态 */
@Composable
private fun DoneToggle(checked: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (checked) {
                    Modifier.background(LxSageSoft)
                } else {
                    Modifier.border(1.5.dp, LxSandDeep, CircleShape)
                }
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "标记为未完成",
                tint = LxSage,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun TodoEditor(
    draft: TodoDraft,
    today: LocalDate,
    error: String?,
    onTitleChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onDueDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = if (draft.id == null) "新的待办" else "编辑待办",
            fontFamily = NewsreaderDisplay,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            color = LxTerra,
        )

        LxTextField(
            value = draft.title,
            onValueChange = onTitleChange,
            label = "要做什么",
        )

        LxTextField(
            value = draft.subject,
            onValueChange = onSubjectChange,
            label = "科目（可不填）",
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "截止", style = MaterialTheme.typography.labelMedium, color = LxInkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LxChoiceChip(text = "今天", selected = draft.dueDate == today, onClick = { onDueDateChange(today) })
                LxChoiceChip(text = "明天", selected = draft.dueDate == today.plusDays(1), onClick = {
                    onDueDateChange(today.plusDays(1))
                })
                // 第三个入口只负责打开日历：日期本身由下面那行念，免得选"今天"时出现两颗同名 chip
                LxChoiceChip(text = "选日期", selected = false, onClick = { showPicker = true })
            }
            Text(
                text = dueLabel(draft.dueDate, today),
                fontSize = 12.sp,
                color = LxInkMuted,
            )
        }

        if (error != null) {
            Text(text = error, fontSize = 12.sp, color = LxRose)
        }

        LxButton(text = "保存", onClick = onSave)

        // 没有"取消"：下滑或点页面外就是取消
        if (draft.id != null) {
            Text(
                text = "删除",
                fontSize = 14.sp,
                color = LxRose,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    if (showPicker) {
        DueDatePicker(
            initial = draft.dueDate,
            onPick = {
                onDueDateChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // DatePicker 给的是 UTC 零点的毫秒数，读写都用同一个时区，否则跨时区会差一天
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis == null) onDismiss()
                    else onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = state)
    }
}

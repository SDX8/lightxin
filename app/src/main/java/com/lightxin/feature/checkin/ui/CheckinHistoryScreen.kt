package com.lightxin.feature.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxIconButton
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSuccess
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.checkin.domain.CheckinHistoryRecord
import com.lightxin.feature.checkin.domain.CheckinMonthSummary
import com.lightxin.feature.checkin.domain.CheckinRecordStatus

@Composable
fun CheckinHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckinHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val error = uiState.error

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "查寝历史", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            MonthSwitcher(
                label = uiState.monthLabel,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                canGoNext = uiState.canGoNext,
            )
            when {
                uiState.isLoading -> LxLoading()
                error != null -> LxError(message = error, onRetry = viewModel::retry)
                else -> {
                    uiState.summary?.let { SummaryCard(it) }
                    if (uiState.records.isEmpty()) {
                        LxEmpty(message = "这个月没有查寝记录")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 20.dp,
                                vertical = 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(uiState.records, key = { "${it.status.code}_${it.id}" }) { record ->
                                HistoryCard(record, modifier = Modifier.animateItem())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LxIconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "上一月",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LxIconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "下一月",
                tint = if (canGoNext) MaterialTheme.colorScheme.onSurface else LxInkMuted,
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: CheckinMonthSummary, modifier: Modifier = Modifier) {
    LxCard(modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            text = "本月应查寝 ${summary.dueCount} 次",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = CheckinRecordStatus.entries.joinToString(" · ") { status ->
                "${status.label} ${summary.counts[status] ?: 0}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryCard(
    record: CheckinHistoryRecord,
    modifier: Modifier = Modifier,
) {
    LxCard(modifier = modifier.padding(horizontal = 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.taskName.ifBlank { "晚点名" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metaLine(record),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            StatusBadge(record.status)
        }
    }
}

private fun metaLine(record: CheckinHistoryRecord): String {
    val parts = listOfNotNull(
        record.dateLabel.takeIf { it.isNotBlank() },
        record.time.takeIf { it.isNotBlank() },
        record.place.takeIf { it.isNotBlank() },
        "拍照".takeIf { record.hasPhoto },
    )
    return parts.joinToString(" · ")
}

@Composable
private fun StatusBadge(status: CheckinRecordStatus) {
    val color = when (status) {
        CheckinRecordStatus.SIGNED -> LxSuccess
        CheckinRecordStatus.LEAVE, CheckinRecordStatus.LEAVE_SCHOOL -> LxInkMuted
        CheckinRecordStatus.NOT_SIGNED -> LxTerra
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

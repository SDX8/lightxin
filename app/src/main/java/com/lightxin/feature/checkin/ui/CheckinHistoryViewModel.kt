package com.lightxin.feature.checkin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.checkin.data.CheckinRepository
import com.lightxin.feature.checkin.domain.CheckinHistoryRecord
import com.lightxin.feature.checkin.domain.CheckinMonthSummary
import com.lightxin.feature.checkin.domain.checkinMonthKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class CheckinHistoryUiState(
    val monthLabel: String = "",
    val records: List<CheckinHistoryRecord> = emptyList(),
    val summary: CheckinMonthSummary? = null,
    val isLoading: Boolean = true,
    val canGoNext: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CheckinHistoryViewModel @Inject constructor(
    private val repository: CheckinRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinHistoryUiState())
    val uiState: StateFlow<CheckinHistoryUiState> = _uiState

    private var month: YearMonth = YearMonth.now()

    init {
        load()
    }

    fun previousMonth() {
        month = month.minusMonths(1)
        load()
    }

    /** 未来没有历史记录可看 */
    fun nextMonth() {
        if (month.plusMonths(1) > YearMonth.now()) return
        month = month.plusMonths(1)
        load()
    }

    fun retry() = load()

    private fun load() {
        val requested = month
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, monthLabel = monthLabelOf(requested))
            }
            repository.getMonthHistory(checkinMonthKey(requested.year, requested.monthValue)).fold(
                onSuccess = { page ->
                    // 翻月翻得快时，只认最后一次请求的结果
                    if (requested != month) return@fold
                    _uiState.update {
                        it.copy(
                            records = page.records,
                            summary = page.summary,
                            isLoading = false,
                            canGoNext = requested < YearMonth.now(),
                        )
                    }
                },
                onFailure = { e ->
                    if (requested != month) return@fold
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    private fun monthLabelOf(value: YearMonth) = "${value.year}年${value.monthValue}月"
}

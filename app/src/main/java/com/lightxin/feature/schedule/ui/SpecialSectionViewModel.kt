package com.lightxin.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.schedule.data.SpecialSectionStore
import com.lightxin.feature.schedule.domain.SectionKind
import com.lightxin.feature.schedule.domain.SpecialItem
import com.lightxin.feature.schedule.domain.SpecialSlot
import com.lightxin.feature.schedule.domain.specialConflictMessage
import com.lightxin.feature.schedule.domain.specialDayConflicts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** 编辑中的表单；id 为 null 表示新增 */
data class SpecialDraft(
    val id: String?,
    val name: String,
    val slot: SpecialSlot,
    val room: String,
    val days: Set<Int>,
)

data class SpecialSectionUiState(
    val settings: List<SpecialItem> = emptyList(),
    val draft: SpecialDraft? = null,
    val error: String? = null,
    val eveningPausedToday: Boolean = false,
) {
    val hasEvening: Boolean get() = settings.any { it.slot.kind == SectionKind.EVENING }
}

@HiltViewModel
class SpecialSectionViewModel @Inject constructor(
    private val store: SpecialSectionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpecialSectionUiState())
    val uiState: StateFlow<SpecialSectionUiState> = _uiState

    init {
        viewModelScope.launch {
            store.settings.collect { list ->
                val sorted = list.sortedWith(
                    compareBy(
                        { it.slot.kind.row },
                        { it.days.minOrNull() ?: Int.MAX_VALUE },
                        { it.name },
                    ),
                )
                _uiState.update { it.copy(settings = sorted) }
            }
        }
        viewModelScope.launch {
            store.eveningPausedOn.collect { pausedOn ->
                _uiState.update { it.copy(eveningPausedToday = pausedOn == LocalDate.now()) }
            }
        }
    }

    fun startAdd(kind: SectionKind) {
        _uiState.update {
            it.copy(
                draft = SpecialDraft(
                    id = null,
                    name = "",
                    slot = SpecialSlot.entries.first { slot -> slot.kind == kind },
                    room = "",
                    days = emptySet(),
                ),
                error = null,
            )
        }
    }

    fun startEdit(setting: SpecialItem) {
        _uiState.update {
            it.copy(
                draft = SpecialDraft(setting.id, setting.name, setting.slot, setting.room, setting.days),
                error = null,
            )
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(draft = null, error = null) }
    }

    fun onNameChange(value: String) = updateDraft { it.copy(name = value) }

    fun onRoomChange(value: String) = updateDraft { it.copy(room = value) }

    fun onSlotChange(slot: SpecialSlot) = updateDraft { it.copy(slot = slot) }

    fun onDayToggle(day: Int) = updateDraft {
        it.copy(days = if (day in it.days) it.days - day else it.days + day)
    }

    fun save() {
        val draft = _uiState.value.draft ?: return
        val name = draft.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = "请填写名称") }
            return
        }
        if (draft.days.isEmpty()) {
            _uiState.update { it.copy(error = "请至少勾选一天") }
            return
        }
        val candidate = SpecialItem(
            id = draft.id ?: UUID.randomUUID().toString(),
            name = name,
            slot = draft.slot,
            room = draft.room.trim(),
            days = draft.days,
        )
        val conflicts = specialDayConflicts(candidate, _uiState.value.settings)
        if (conflicts.isNotEmpty()) {
            _uiState.update { it.copy(error = specialConflictMessage(conflicts)) }
            return
        }
        viewModelScope.launch {
            store.save(candidate)
            _uiState.update { it.copy(draft = null, error = null) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { store.delete(id) }
    }

    /** 存的是"今天按下了"，不是布尔开关 —— 明天读到的日期不再是今天，它自己就恢复了 */
    fun setEveningPaused(paused: Boolean) {
        viewModelScope.launch { store.setEveningPaused(paused) }
    }

    private fun updateDraft(block: (SpecialDraft) -> SpecialDraft) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = block(draft), error = null)
        }
    }
}

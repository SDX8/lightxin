package com.lightxin.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.schedule.data.MorningSectionStore
import com.lightxin.feature.schedule.domain.MorningSetting
import com.lightxin.feature.schedule.domain.MorningSlot
import com.lightxin.feature.schedule.domain.morningConflictMessage
import com.lightxin.feature.schedule.domain.morningDayConflicts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** 编辑中的表单；id 为 null 表示新增 */
data class MorningDraft(
    val id: String?,
    val name: String,
    val slot: MorningSlot,
    val room: String,
    val days: Set<Int>,
)

data class MorningSectionUiState(
    val settings: List<MorningSetting> = emptyList(),
    val draft: MorningDraft? = null,
    val error: String? = null,
)

@HiltViewModel
class MorningSectionViewModel @Inject constructor(
    private val store: MorningSectionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MorningSectionUiState())
    val uiState: StateFlow<MorningSectionUiState> = _uiState

    init {
        viewModelScope.launch {
            store.settings.collect { list ->
                val sorted = list.sortedWith(
                    compareBy({ it.days.minOrNull() ?: Int.MAX_VALUE }, { it.name }),
                )
                _uiState.update { it.copy(settings = sorted) }
            }
        }
    }

    fun startAdd() {
        _uiState.update {
            it.copy(
                draft = MorningDraft(id = null, name = "", slot = MorningSlot.MORNING_STUDY, room = "", days = emptySet()),
                error = null,
            )
        }
    }

    fun startEdit(setting: MorningSetting) {
        _uiState.update {
            it.copy(
                draft = MorningDraft(setting.id, setting.name, setting.slot, setting.room, setting.days),
                error = null,
            )
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(draft = null, error = null) }
    }

    fun onNameChange(value: String) = updateDraft { it.copy(name = value) }

    fun onRoomChange(value: String) = updateDraft { it.copy(room = value) }

    fun onSlotChange(slot: MorningSlot) = updateDraft { it.copy(slot = slot) }

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
        val candidate = MorningSetting(
            id = draft.id ?: UUID.randomUUID().toString(),
            name = name,
            slot = draft.slot,
            room = draft.room.trim(),
            days = draft.days,
        )
        val conflicts = morningDayConflicts(candidate, _uiState.value.settings)
        if (conflicts.isNotEmpty()) {
            _uiState.update { it.copy(error = morningConflictMessage(conflicts)) }
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

    private fun updateDraft(block: (MorningDraft) -> MorningDraft) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = block(draft), error = null)
        }
    }
}

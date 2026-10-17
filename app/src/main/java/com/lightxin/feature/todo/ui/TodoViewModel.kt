package com.lightxin.feature.todo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.todo.data.TodoStore
import com.lightxin.feature.todo.domain.TodoItem
import com.lightxin.feature.todo.domain.TodoSections
import com.lightxin.feature.todo.domain.sanitizeTodoSubject
import com.lightxin.feature.todo.domain.sanitizeTodoTitle
import com.lightxin.feature.todo.domain.todoSections
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 编辑中的表单；id 为 null 表示新增 */
data class TodoDraft(
    val id: String?,
    val title: String,
    val subject: String,
    val dueDate: LocalDate,
)

data class TodoUiState(
    val items: List<TodoItem> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val pinnedDone: Set<String> = emptySet(),
    val sections: TodoSections = TodoSections(emptyList(), emptyList(), emptyList()),
    val draft: TodoDraft? = null,
    val error: String? = null,
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val store: TodoStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState

    init {
        viewModelScope.launch {
            store.items.collect { items ->
                recompute { it.copy(items = items, today = LocalDate.now()) }
            }
        }
    }

    fun startAdd() {
        _uiState.update {
            it.copy(draft = TodoDraft(id = null, title = "", subject = "", dueDate = it.today), error = null)
        }
    }

    fun startEdit(item: TodoItem) {
        _uiState.update {
            it.copy(
                draft = TodoDraft(item.id, item.title, item.subject, item.dueDate),
                error = null,
            )
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(draft = null, error = null) }
    }

    fun onTitleChange(value: String) = updateDraft { it.copy(title = value) }

    fun onSubjectChange(value: String) = updateDraft { it.copy(subject = value) }

    fun onDueDateChange(date: LocalDate) = updateDraft { it.copy(dueDate = date) }

    fun save() {
        val draft = _uiState.value.draft ?: return
        val title = sanitizeTodoTitle(draft.title)
        if (title.isEmpty()) {
            _uiState.update { it.copy(error = "总得写下要做什么") }
            return
        }
        val subject = sanitizeTodoSubject(draft.subject)
        viewModelScope.launch {
            val existing = draft.id?.let { id -> _uiState.value.items.firstOrNull { it.id == id } }
            if (existing == null) {
                store.add(title, subject, draft.dueDate)
            } else {
                store.update(existing.copy(title = title, subject = subject, dueDate = draft.dueDate))
            }
            _uiState.update { it.copy(draft = null, error = null) }
        }
    }

    /**
     * 勾完先钉在原来那一段里，别在用户手指底下把列表重排。
     *
     * ViewModel 挂在 HOME 这条导航记录上，切 tab 不会销毁它，所以钉住的状态
     * 由 [settleCompleted] 在待办页离开组合时清掉 —— 也就是"下次再进这个 tab 才归档"。
     */
    fun setDone(id: String, done: Boolean) {
        recompute { it.copy(pinnedDone = if (done) it.pinnedDone + id else it.pinnedDone - id) }
        viewModelScope.launch { store.setDone(id, done) }
    }

    fun settleCompleted() {
        recompute { it.copy(pinnedDone = emptySet()) }
    }

    fun delete(id: String) {
        viewModelScope.launch { store.delete(id) }
    }

    fun clearDone() {
        viewModelScope.launch { store.clearDone() }
    }

    private fun recompute(block: (TodoUiState) -> TodoUiState) {
        _uiState.update { state ->
            val next = block(state)
            next.copy(sections = todoSections(next.items, next.today, next.pinnedDone))
        }
    }

    private fun updateDraft(block: (TodoDraft) -> TodoDraft) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = block(draft), error = null)
        }
    }
}

package com.lightxin.feature.todo.data

import android.content.Context
import com.google.gson.Gson
import com.lightxin.feature.todo.domain.TodoItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待办的本地存储：单 JSON 文件 + Mutex，与 `RouteTemplateStore` 同一套路。
 *
 * 待办会长到几十上百条，且要按天增删改，不适合塞进 DataStore 的一个字符串键里。
 * 不引入 Room —— 见 codestable/compound/2026-04-22-decision-route-template-json-not-room.md。
 */
@Singleton
class TodoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file: File by lazy { File(context.filesDir, FILE_NAME) }
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<TodoItem>>(emptyList())
    val items: StateFlow<List<TodoItem>> = _items.asStateFlow()

    init {
        scope.launch { load() }
    }

    private suspend fun load() = mutex.withLock {
        val list = withContext(Dispatchers.IO) { readFile() }
        _items.value = list
    }

    suspend fun add(title: String, subject: String, dueDate: LocalDate): TodoItem = mutex.withLock {
        val item = TodoItem(
            id = UUID.randomUUID().toString(),
            title = title,
            subject = subject,
            dueDate = dueDate,
            createdAtMillis = System.currentTimeMillis(),
        )
        persist(_items.value + item)
        item
    }

    suspend fun update(item: TodoItem) = mutex.withLock {
        persist(_items.value.map { if (it.id == item.id) item else it })
    }

    suspend fun setDone(id: String, done: Boolean) = mutex.withLock {
        val today = LocalDate.now()
        persist(_items.value.map {
            if (it.id != id) it else it.copy(done = done, doneAt = if (done) today else null)
        })
    }

    suspend fun delete(id: String) = mutex.withLock {
        persist(_items.value.filterNot { it.id == id })
    }

    suspend fun clearDone() = mutex.withLock {
        persist(_items.value.filterNot { it.done })
    }

    private suspend fun persist(list: List<TodoItem>) {
        _items.value = list
        withContext(Dispatchers.IO) { writeFile(list) }
    }

    private fun readFile(): List<TodoItem> {
        if (!file.exists()) return emptyList()
        return decodeTodos(runCatching { file.readText() }.getOrNull())
    }

    private fun writeFile(list: List<TodoItem>) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(encodeTodos(list))
        // renameTo 本身原子覆盖；先 delete 会在 rename 失败时把整份清单一起丢掉
        if (!tmp.renameTo(file)) tmp.delete()
    }

    private companion object {
        const val FILE_NAME = "todo_items.json"
    }
}

/**
 * 落盘形态。
 *
 * 日期一律存 ISO 字符串：Gson 反射序列化 `LocalDate` 会把它内部的字段结构写进文件，
 * 换 JDK 版本就解不回来。
 */
internal data class TodoEntity(
    val id: String,
    val title: String,
    val subject: String,
    val dueDate: String,
    val createdAtMillis: Long,
    val done: Boolean,
    val doneAt: String?,
)

internal fun TodoItem.toEntity() = TodoEntity(
    id = id,
    title = title,
    subject = subject,
    dueDate = dueDate.toString(),
    createdAtMillis = createdAtMillis,
    done = done,
    doneAt = doneAt?.toString(),
)

/** 日期坏掉的条目直接丢：半条待办比没有更糟，它会在勾选、删除时以 null 字段回来咬人 */
internal fun TodoEntity.toItem(): TodoItem? {
    val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return null
    val doneDay = doneAt?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
    return TodoItem(
        id = id,
        title = title,
        subject = subject,
        dueDate = due,
        createdAtMillis = createdAtMillis,
        done = done,
        doneAt = doneDay,
    )
}

internal fun encodeTodos(items: List<TodoItem>): String =
    Gson().toJson(items.map { it.toEntity() })

internal fun decodeTodos(json: String?): List<TodoItem> {
    if (json.isNullOrBlank()) return emptyList()
    val entities = runCatching {
        Gson().fromJson(json, Array<TodoEntity>::class.java)?.toList()
    }.getOrNull()
    return entities.orEmpty().mapNotNull { it.toItem() }
}

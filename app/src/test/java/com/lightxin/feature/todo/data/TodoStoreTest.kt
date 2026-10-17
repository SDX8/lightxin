package com.lightxin.feature.todo.data

import com.lightxin.feature.todo.domain.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodoStoreTest {

    private val item = TodoItem(
        id = "a",
        title = "高数习题册第三章",
        subject = "高数",
        dueDate = LocalDate.of(2026, 10, 18),
        createdAtMillis = 1_729_000_000_000L,
    )

    @Test
    fun `items survive a json round trip`() {
        val decoded = decodeTodos(encodeTodos(listOf(item, item.copy(id = "b", done = true, doneAt = LocalDate.of(2026, 10, 17)))))

        assertEquals(2, decoded.size)
        assertEquals(item, decoded.first())
        assertEquals(LocalDate.of(2026, 10, 17), decoded[1].doneAt)
    }

    @Test
    fun `unreadable files degrade to an empty list instead of crashing`() {
        assertTrue(decodeTodos(null).isEmpty())
        assertTrue(decodeTodos("").isEmpty())
        assertTrue(decodeTodos("{ not json").isEmpty())
    }

    @Test
    fun `a row with a broken due date is dropped, its healthy neighbours are kept`() {
        val json = """
            [
              {"id":"a","title":"好的一条","subject":"","dueDate":"2026-10-18","createdAtMillis":1,"done":false,"doneAt":null},
              {"id":"b","title":"坏日期","subject":"","dueDate":"18/10/2026","createdAtMillis":2,"done":false,"doneAt":null},
              {"id":"c","title":"缺日期","subject":"","createdAtMillis":3,"done":false,"doneAt":null}
            ]
        """.trimIndent()

        val decoded = decodeTodos(json)

        assertEquals(listOf("a"), decoded.map { it.id })
    }

    @Test
    fun `dates are persisted as ISO strings, not as gson-reflected LocalDate internals`() {
        val json = encodeTodos(listOf(item))

        assertTrue(json, json.contains("\"dueDate\":\"2026-10-18\""))
        assertTrue(json, !json.contains("dayOfWeek"))
    }
}

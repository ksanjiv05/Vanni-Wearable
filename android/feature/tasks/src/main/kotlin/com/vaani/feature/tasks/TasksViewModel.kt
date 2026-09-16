package com.vaani.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.core.common.formatClock
import com.vaani.domain.model.Note
import com.vaani.domain.model.Priority
import com.vaani.domain.model.Todo
import com.vaani.domain.model.TodoStatus
import com.vaani.domain.repository.NotesRepository
import com.vaani.domain.repository.NotesWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: NotesRepository,
    private val writer: NotesWriter,
) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.OPEN)

    val uiState: StateFlow<TasksUiState> =
        combine(repository.observeNotes(), filter) { notes, f ->
            buildState(notes, f)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TasksUiState(isLoading = true),
        )

    fun setFilter(f: TaskFilter) { filter.value = f }

    /** Persist the toggle so it survives navigation/relaunch (durable via Room). */
    fun toggle(id: String) {
        viewModelScope.launch {
            val row = uiState.value.groups.flatMap { it.rows }.firstOrNull { it.id == id }
            val nowDone = row?.done ?: false
            val newStatus = if (nowDone) TodoStatus.OPEN else TodoStatus.DONE
            val completedAt = if (newStatus == TodoStatus.DONE) System.currentTimeMillis() else null
            writer.setTodoStatus(id, newStatus, completedAt)
        }
    }

    private data class FlatTodo(val note: Note, val todo: Todo, val done: Boolean)

    private fun buildState(notes: List<Note>, f: TaskFilter): TasksUiState {
        val flat = notes.flatMap { note ->
            note.todos.map { todo ->
                FlatTodo(note, todo, done = todo.status == TodoStatus.DONE)
            }
        }
        val visible = when (f) {
            TaskFilter.OPEN -> flat.filter { !it.done }
            TaskFilter.DONE -> flat.filter { it.done }
            TaskFilter.ALL -> flat
        }

        // Bucket by real due dates where present: dueHint => TODAY/UPCOMING, HIGH-priority
        // open items surface as OVERDUE, done items last. (No fabricated due dates.)
        val overdue = visible.filter { !it.done && it.todo.priority == Priority.HIGH }
        val today = visible.filter { !it.done && it.todo.priority != Priority.HIGH }
        val doneGroup = visible.filter { it.done }

        val groups = buildList {
            if (overdue.isNotEmpty()) add(TaskGroup("HIGH PRIORITY", danger = true, rows = overdue.map { it.toRow() }))
            if (today.isNotEmpty()) add(TaskGroup("TO DO", danger = false, rows = today.map { it.toRow() }))
            if (doneGroup.isNotEmpty()) add(TaskGroup("DONE", danger = false, rows = doneGroup.map { it.toRow() }))
        }

        return TasksUiState(
            isLoading = false,
            filter = f,
            openCount = flat.count { !it.done },
            groups = groups,
        )
    }

    private fun FlatTodo.toRow(): TaskRow {
        val prio = when {
            done -> TaskPriority.DONE
            todo.priority == Priority.HIGH -> TaskPriority.HIGH
            todo.priority == Priority.MEDIUM -> TaskPriority.MEDIUM
            else -> TaskPriority.LOW
        }
        val subtitle = when {
            done -> "Done"
            todo.assignee != null && todo.dueHint != null -> "${todo.assignee} · due ${todo.dueHint}"
            todo.assignee != null -> todo.assignee!!
            todo.dueHint != null -> "Due ${todo.dueHint}"
            else -> "From ${note.title}"
        }
        val seek = todo.sourceStartMs
            ?.let { "▶ ${formatClock(it)}" }
            ?: "▶ --:--"
        return TaskRow(
            id = todo.id,
            text = todo.text,
            sourceNote = note.title,
            subtitle = subtitle,
            seekLabel = seek,
            priorityLabel = when (todo.priority) {
                Priority.HIGH -> "HIGH"
                Priority.MEDIUM -> "MED"
                Priority.LOW -> "LOW"
            },
            priority = prio,
            done = done,
            noteId = note.id,
        )
    }
}

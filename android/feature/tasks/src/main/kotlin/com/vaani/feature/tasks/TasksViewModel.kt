package com.vaani.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.core.common.formatClock
import com.vaani.domain.model.Note
import com.vaani.domain.model.Priority
import com.vaani.domain.model.Todo
import com.vaani.domain.model.TodoStatus
import com.vaani.domain.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    repository: NotesRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.OPEN)

    /** Locally-toggled ids (overrides the fixture status until a data layer lands). */
    private val toggled = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<TasksUiState> =
        combine(repository.observeNotes(), filter, toggled) { notes, f, toggledIds ->
            buildState(notes, f, toggledIds)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TasksUiState(isLoading = true),
        )

    fun setFilter(f: TaskFilter) { filter.value = f }

    fun toggle(id: String) {
        toggled.value = toggled.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    private data class FlatTodo(val note: Note, val todo: Todo, val done: Boolean)

    private fun buildState(notes: List<Note>, f: TaskFilter, toggledIds: Set<String>): TasksUiState {
        val flat = notes.flatMap { note ->
            note.todos.map { todo ->
                val baseDone = todo.status == TodoStatus.DONE
                FlatTodo(note, todo, done = baseDone != (todo.id in toggledIds))
            }
        }
        val visible = when (f) {
            TaskFilter.OPEN -> flat.filter { !it.done }
            TaskFilter.DONE -> flat.filter { it.done }
            TaskFilter.ALL -> flat
        }

        // Deterministic sample bucketing: HIGH+open -> OVERDUE, other open -> TODAY, done -> DONE TODAY.
        val overdue = visible.filter { !it.done && it.todo.priority == Priority.HIGH }
        val today = visible.filter { !it.done && it.todo.priority != Priority.HIGH }
        val doneToday = visible.filter { it.done }

        val groups = buildList {
            if (overdue.isNotEmpty()) add(TaskGroup("OVERDUE", danger = true, rows = overdue.map { it.toRow() }))
            if (today.isNotEmpty()) add(TaskGroup("TODAY", danger = false, rows = today.map { it.toRow() }))
            if (doneToday.isNotEmpty()) add(TaskGroup("DONE TODAY", danger = false, rows = doneToday.map { it.toRow() }))
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
            done -> "Completed 2h ago"
            todo.assignee != null && todo.dueHint != null -> "${todo.assignee} · due ${todo.dueHint}"
            todo.assignee != null -> todo.assignee!!
            todo.dueHint != null -> "Assigned to you · due ${todo.dueHint}"
            else -> "Assigned to you"
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
        )
    }
}

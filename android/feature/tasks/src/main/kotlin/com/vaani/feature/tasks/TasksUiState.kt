package com.vaani.feature.tasks

/** Immutable UI state for the Tasks screen. */
data class TasksUiState(
    val isLoading: Boolean = true,
    val filter: TaskFilter = TaskFilter.OPEN,
    val openCount: Int = 0,
    val groups: List<TaskGroup> = emptyList(),
)

enum class TaskFilter { OPEN, ALL, DONE }

/** A due-date-bucketed section of task rows (OVERDUE / TODAY / DONE TODAY). */
data class TaskGroup(
    val header: String,
    val danger: Boolean,
    val rows: List<TaskRow>,
)

data class TaskRow(
    val id: String,
    val text: String,
    val sourceNote: String,
    val subtitle: String,
    val seekLabel: String,
    val priorityLabel: String,
    val priority: TaskPriority,
    val done: Boolean,
    val noteId: String = "",
)

enum class TaskPriority { HIGH, MEDIUM, LOW, DONE }

package com.cleaningapp.backend.taskplan

data class TaskPlanGenerationResult(
    val selected: Int,
    val created: Int,
    val skipped: Int,
    val failed: Int,
)

interface TaskPlanGenerationService {
    fun generateDueTasks(): TaskPlanGenerationResult
}

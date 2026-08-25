package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.task.TaskEntity
import java.time.LocalDateTime
import java.util.UUID

interface TaskPlanInstanceService {
    fun createTaskInstance(taskPlanId: UUID, dueAt: LocalDateTime): TaskEntity
}

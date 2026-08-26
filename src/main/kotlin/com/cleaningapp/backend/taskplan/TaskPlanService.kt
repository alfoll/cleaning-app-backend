package com.cleaningapp.backend.taskplan

import java.util.UUID

interface TaskPlanService {
    fun updateRecurrence(taskPlanId: UUID, recurrenceType: RecurrenceType)

    fun cancelTaskPlan(taskPlanId: UUID)
}

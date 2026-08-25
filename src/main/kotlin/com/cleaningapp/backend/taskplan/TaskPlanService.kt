package com.cleaningapp.backend.taskplan

import java.util.UUID

interface TaskPlanService {
    fun cancelTaskPlan(taskPlanId: UUID)
}

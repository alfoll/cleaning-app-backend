package com.cleaningapp.backend.taskplan

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/task-plans")
class TaskPlanController(
    private val taskPlanService: TaskPlanService,
) {

    @PatchMapping("/{taskPlanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateRecurrence(
        @PathVariable taskPlanId: UUID,
        @RequestBody request: TaskPlanRecurrenceUpdateDTO,
    ) {
        taskPlanService.updateRecurrence(taskPlanId, request.recurrenceType)
    }

    @DeleteMapping("/{taskPlanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelTaskPlan(@PathVariable taskPlanId: UUID) {
        taskPlanService.cancelTaskPlan(taskPlanId)
    }
}

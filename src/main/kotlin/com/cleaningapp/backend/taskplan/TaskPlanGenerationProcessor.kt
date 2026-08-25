package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityService
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.activity.RecordActivityCommand
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class TaskPlanGenerationProcessor(
    private val taskPlanRepository: TaskPlanRepository,
    private val taskRepository: TaskRepository,
    private val taskPlanInstanceService: TaskPlanInstanceService,
    private val recurrenceCalculator: TaskPlanRecurrenceCalculator,
    private val userHouseholdRepository: UserHouseholdRepository,
    private val activityService: ActivityService,
    private val clock: Clock,
) {

    @Transactional
    fun generateTaskForPlan(taskPlanId: UUID): Boolean {
        val taskPlan = taskPlanRepository.findByIdForUpdate(taskPlanId)
            ?: return false
        val currentDate = LocalDate.now(clock)

        if (!taskPlan.isActive || taskPlan.nextDueAt.toLocalDate().isAfter(currentDate))
            return false
        if (taskRepository.existsByTaskPlanIdAndIsCompletedFalse(taskPlanId))
            return false

        val taskDueAt = taskPlan.nextDueAt
        val task = taskPlanInstanceService.createTaskInstance(taskPlanId, taskDueAt)

        taskPlan.nextDueAt = recurrenceCalculator.calculateNextDueAt(
            currentDueAt = taskDueAt,
            recurrenceType = taskPlan.recurrenceType,
            monthlyAnchorDay = taskPlan.monthlyAnchorDay,
            monthlyLastDay = taskPlan.monthlyLastDay,
        )

        val creatorMembership = userHouseholdRepository.findByUserIdAndHouseholdId(
            taskPlan.createdBy.id!!,
            taskPlan.household.id!!,
        ) ?: throw MembershipNotFoundException()

        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = taskPlan.household.id!!,
                memberId = creatorMembership.id!!,
                activityType = ActivityType.TASK_CREATED,
                title = "Task created",
                description = "${taskPlan.createdBy.name} created task \"${task.title}\"",
            )
        )

        return true
    }
}

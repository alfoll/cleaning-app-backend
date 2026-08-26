package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.TaskPlanNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class TaskPlanServiceImpl(
    private val taskPlanRepository: TaskPlanRepository,
    private val userRepository: UserRepository,
    private val householdRepository: HouseholdRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
    private val taskRepository: TaskRepository,
    private val recurrenceCalculator: TaskPlanRecurrenceCalculator,
) : TaskPlanService {

    override fun updateRecurrence(taskPlanId: UUID, recurrenceType: RecurrenceType) {
        val taskPlan = getActiveOwnedTaskPlanForUpdate(
            taskPlanId = taskPlanId,
            creatorConflictMessage = "Only creator can update task plan",
            inactiveConflictMessage = "Inactive task plan cannot be updated",
        )

        if (taskPlan.recurrenceType == recurrenceType)
            return

        val unfinishedTask = taskRepository.findByTaskPlanIdAndIsCompletedFalse(taskPlanId)

        if (unfinishedTask != null) {
            val dueAt = checkNotNull(unfinishedTask.dueAt) {
                "Recurring task must have a due date"
            }
            val schedule = recurrenceCalculator.createSchedule(dueAt, recurrenceType)

            taskPlan.nextDueAt = schedule.nextDueAt
            taskPlan.monthlyAnchorDay = schedule.monthlyAnchorDay
            taskPlan.monthlyLastDay = schedule.monthlyLastDay
        } else {
            val metadata = recurrenceCalculator.createRecurrenceMetadata(
                referenceAt = taskPlan.nextDueAt,
                recurrenceType = recurrenceType,
            )

            taskPlan.monthlyAnchorDay = metadata.monthlyAnchorDay
            taskPlan.monthlyLastDay = metadata.monthlyLastDay
        }

        taskPlan.recurrenceType = recurrenceType
    }

    override fun cancelTaskPlan(taskPlanId: UUID) {
        val taskPlan = getActiveOwnedTaskPlanForUpdate(
            taskPlanId = taskPlanId,
            creatorConflictMessage = "Only creator can cancel task plan",
            inactiveConflictMessage = "Task plan is already inactive",
        )

        taskPlan.isActive = false
    }

    private fun getActiveOwnedTaskPlanForUpdate(
        taskPlanId: UUID,
        creatorConflictMessage: String,
        inactiveConflictMessage: String,
    ): TaskPlanEntity {
        val currentUser = getCurrentUser()
        val householdId = taskPlanRepository.findHouseholdIdByTaskPlanId(taskPlanId)
            ?: throw TaskPlanNotFoundException()

        val household = householdRepository.findByIdForUpdate(householdId)
            ?: throw HouseholdNotFoundException()
        if (!household.isActive)
            throw HouseholdNotActiveException()

        val membership = userHouseholdRepository.findByUserIdAndHouseholdIdForUpdate(
            currentUser.id!!,
            householdId,
        ) ?: throw MembershipNotFoundException()
        if (!membership.isUserActive)
            throw MembershipNotActiveException()

        val taskPlan = taskPlanRepository.findByIdForUpdate(taskPlanId)
            ?: throw TaskPlanNotFoundException()

        if (taskPlan.household.id != household.id)
            throw BusinessConflictException("Task plan does not belong to this household")
        if (taskPlan.createdBy.id != currentUser.id)
            throw BusinessConflictException(creatorConflictMessage)
        if (!taskPlan.isActive)
            throw BusinessConflictException(inactiveConflictMessage)

        return taskPlan
    }

    private fun getCurrentUser(): UserEntity {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("User not authenticated")
        val user = userRepository.findUserByFirebaseUid(authentication.name)
            ?: throw UserNotFoundException()

        if (!user.isActive)
            throw UserNotActiveException()

        return user
    }
}

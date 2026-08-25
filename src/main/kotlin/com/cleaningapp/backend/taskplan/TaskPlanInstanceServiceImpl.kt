package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.TaskPlanNotFoundException
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskEntity
import com.cleaningapp.backend.task.TaskRepository
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class TaskPlanInstanceServiceImpl(
    private val taskPlanRepository: TaskPlanRepository,
    private val taskRepository: TaskRepository,
) : TaskPlanInstanceService {

    private companion object {
        const val SINGLE_OPEN_TASK_INDEX = "idx_task_task_plan_single_open"
    }

    override fun createTaskInstance(
        taskPlanId: UUID,
        dueAt: LocalDateTime,
    ): TaskEntity {
        val taskPlan = taskPlanRepository.findByIdForUpdate(taskPlanId)
            ?: throw TaskPlanNotFoundException()

        if (!taskPlan.isActive)
            throw BusinessConflictException("Inactive task plan cannot create task instances")

        if (taskRepository.existsByTaskPlanIdAndIsCompletedFalse(taskPlanId))
            throw openTaskConflict()

        val task = TaskEntity(
            title = taskPlan.title,
            description = taskPlan.description,
            reward = taskPlan.reward,
            dueAt = TaskDueDatePolicy.endOfDay(dueAt.toLocalDate()),
        ).apply {
            household = taskPlan.household
            createdBy = taskPlan.createdBy
            this.taskPlan = taskPlan
        }

        return try {
            taskRepository.saveAndFlush(task)
        } catch (exception: DataIntegrityViolationException) {
            if (exception.isSingleOpenTaskViolation())
                throw openTaskConflict()

            throw exception
        }
    }

    private fun DataIntegrityViolationException.isSingleOpenTaskViolation(): Boolean =
        generateSequence<Throwable>(this) { it.cause }
            .any { cause ->
                (cause is ConstraintViolationException && cause.constraintName == SINGLE_OPEN_TASK_INDEX) ||
                    cause.message?.contains(SINGLE_OPEN_TASK_INDEX) == true
            }

    private fun openTaskConflict(): BusinessConflictException =
        BusinessConflictException("Task plan already has an unfinished task")
}

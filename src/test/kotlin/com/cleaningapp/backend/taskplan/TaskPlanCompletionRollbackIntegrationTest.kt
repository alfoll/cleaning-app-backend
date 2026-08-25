package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate

class TaskPlanCompletionRollbackIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `schedule update failure should roll back task reward transaction and activity`() {
        val user = testDataFactory.createTestUser(firebaseUid = defaultFirebaseUid)
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 10,
        )
        val originalNextDueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(1))
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = user,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = originalNextDueAt,
        )
        // Simulates invalid legacy data so schedule processing fails after the normal
        // completion side effects have run but before the transaction can commit.
        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 25,
            dueAt = null,
            taskPlan = plan,
        )
        authenticateAs()

        assertThatThrownBy {
            taskService.completeTask(task.id!!)
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Recurring task must have a due date")

        val savedTask = taskRepository.findById(task.id!!).orElseThrow()
        val savedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val savedPlan = taskPlanRepository.findById(plan.id!!).orElseThrow()

        assertThat(savedTask.isCompleted).isFalse()
        assertThat(savedTask.completedBy).isNull()
        assertThat(savedTask.completedAt).isNull()
        assertThat(savedTask.assignedTo).isNotNull()
        assertThat(savedMembership.balance).isEqualTo(10)
        assertThat(transactionRepository.findAll().filter { it.task?.id == task.id }).isEmpty()
        assertThat(
            activityRepository.findAll().filter { it.activityType == ActivityType.TASK_COMPLETED }
        ).isEmpty()
        assertThat(savedPlan.nextDueAt).isEqualTo(originalNextDueAt)
    }
}

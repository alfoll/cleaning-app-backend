package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.config.MutableTestClock
import com.cleaningapp.backend.task.TaskCreateDTO
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TaskPlanCompletionIntegrationTest : BaseIntegrationTest() {

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
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var testClock: MutableTestClock

    @AfterEach
    fun resetClock() {
        testClock.reset()
    }

    @Test
    fun `ordinary task completion should remain independent from task plan scheduling`() {
        val completionTime = LocalDateTime.of(2026, 9, 10, 12, 0)
        testClock.setCurrentDateTime(completionTime)
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)
        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 25,
        )
        authenticateAs()

        taskService.completeTask(task.id!!)

        entityManager.flush()
        entityManager.clear()
        val savedTask = taskRepository.findById(task.id!!).orElseThrow()

        assertThat(savedTask.isCompleted).isTrue()
        assertThat(savedTask.completedAt).isEqualTo(completionTime)
        assertThat(savedTask.taskPlan).isNull()
        assertThat(taskPlanRepository.count()).isZero()
    }

    @Test
    fun `daily completion on due date should keep precomputed next due date`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2026, 9, 10, 12, 0),
            dueDate = LocalDate.of(2026, 9, 10),
            recurrenceType = RecurrenceType.DAILY,
            nextDueDate = LocalDate.of(2026, 9, 11),
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 11))
        assertThat(plan.monthlyAnchorDay).isNull()
        assertThat(plan.monthlyLastDay).isFalse()
    }

    @Test
    fun `overdue daily completion should restart on next calendar day`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2026, 9, 13, 12, 0),
            dueDate = LocalDate.of(2026, 9, 10),
            recurrenceType = RecurrenceType.DAILY,
            nextDueDate = LocalDate.of(2026, 9, 11),
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 14))
        assertThat(taskRepository.findAllByTaskPlanId(fixture.planId)).hasSize(1)
    }

    @Test
    fun `early weekly completion should keep precomputed next due date`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2026, 9, 8, 12, 0),
            dueDate = LocalDate.of(2026, 9, 10),
            recurrenceType = RecurrenceType.WEEKLY,
            nextDueDate = LocalDate.of(2026, 9, 17),
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 17))
    }

    @Test
    fun `overdue weekly completion should restart seven calendar days after completion`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2026, 9, 20, 12, 0),
            dueDate = LocalDate.of(2026, 9, 10),
            recurrenceType = RecurrenceType.WEEKLY,
            nextDueDate = LocalDate.of(2026, 9, 17),
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 27))
    }

    @Test
    fun `early completion of weekly task created without due date should keep next cycle`() {
        val fixture = recurringTaskCreatedWithoutDueAt(
            completionTime = LocalDateTime.of(2026, 8, 5, 12, 0),
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 8, 15))
    }

    @Test
    fun `overdue completion of weekly task created without due date should restart from completion`() {
        val fixture = recurringTaskCreatedWithoutDueAt(
            completionTime = LocalDateTime.of(2026, 8, 10, 12, 0),
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 8, 17))
    }

    @Test
    fun `monthly completion before due date should preserve schedule and anchor semantics`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2026, 9, 8, 12, 0),
            dueDate = LocalDate.of(2026, 9, 30),
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueDate = LocalDate.of(2026, 10, 30),
            monthlyAnchorDay = 30,
            monthlyLastDay = false,
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 10, 30))
        assertThat(plan.monthlyAnchorDay).isEqualTo(30)
        assertThat(plan.monthlyLastDay).isFalse()
        assertThat(plan.recurrenceType).isEqualTo(RecurrenceType.MONTHLY)
    }

    @Test
    fun `overdue monthly completion on normal day should replace month end anchor`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2027, 2, 5, 12, 0),
            dueDate = LocalDate.of(2027, 1, 31),
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueDate = LocalDate.of(2027, 2, 28),
            monthlyAnchorDay = null,
            monthlyLastDay = true,
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2027, 3, 5))
        assertThat(plan.monthlyAnchorDay).isEqualTo(5)
        assertThat(plan.monthlyLastDay).isFalse()
    }

    @Test
    fun `overdue monthly completion on non leap February last day should establish month end anchor`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2027, 2, 28, 12, 0),
            dueDate = LocalDate.of(2027, 1, 31),
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueDate = LocalDate.of(2027, 2, 28),
            monthlyAnchorDay = null,
            monthlyLastDay = true,
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2027, 3, 31))
        assertThat(plan.monthlyAnchorDay).isNull()
        assertThat(plan.monthlyLastDay).isTrue()
    }

    @Test
    fun `overdue monthly completion on leap day should establish month end anchor`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2028, 2, 29, 12, 0),
            dueDate = LocalDate.of(2028, 1, 31),
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueDate = LocalDate.of(2028, 2, 29),
            monthlyAnchorDay = null,
            monthlyLastDay = true,
        )

        taskService.completeTask(fixture.taskId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2028, 3, 31))
        assertThat(plan.monthlyAnchorDay).isNull()
        assertThat(plan.monthlyLastDay).isTrue()
    }

    @Test
    fun `inactive plan task should complete and reward member without schedule update`() {
        val fixture = recurringTask(
            completionTime = LocalDateTime.of(2026, 9, 13, 12, 0),
            dueDate = LocalDate.of(2026, 9, 10),
            recurrenceType = RecurrenceType.DAILY,
            nextDueDate = LocalDate.of(2026, 9, 11),
            isActive = false,
            initialBalance = 10,
            reward = 25,
        )

        taskService.completeTask(fixture.taskId)

        entityManager.flush()
        entityManager.clear()
        val plan = taskPlanRepository.findById(fixture.planId).orElseThrow()
        val task = taskRepository.findById(fixture.taskId).orElseThrow()
        val membership = userHouseholdRepository.findById(fixture.membershipId).orElseThrow()
        val transactions = transactionRepository.findAll().filter { it.task?.id == fixture.taskId }
        val activities = activityRepository.findAll().filter {
            it.activityType == ActivityType.TASK_COMPLETED && it.member.id == fixture.membershipId
        }

        assertThat(task.isCompleted).isTrue()
        assertThat(task.completedAt).isEqualTo(LocalDateTime.of(2026, 9, 13, 12, 0))
        assertThat(membership.balance).isEqualTo(35)
        assertThat(transactions).hasSize(1)
        assertThat(transactions.single().type).isEqualTo(TransactionType.TASK_COMPLETION)
        assertThat(activities).hasSize(1)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 11))
    }

    private fun recurringTask(
        completionTime: LocalDateTime,
        dueDate: LocalDate,
        recurrenceType: RecurrenceType,
        nextDueDate: LocalDate,
        monthlyAnchorDay: Int? = null,
        monthlyLastDay: Boolean = false,
        isActive: Boolean = true,
        initialBalance: Int = 0,
        reward: Int = 20,
    ): CompletionFixture {
        testClock.setCurrentDateTime(completionTime)
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = initialBalance,
        )
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = user,
            reward = reward,
            recurrenceType = recurrenceType,
            nextDueAt = TaskDueDatePolicy.endOfDay(nextDueDate),
            monthlyAnchorDay = monthlyAnchorDay,
            monthlyLastDay = monthlyLastDay,
            isActive = isActive,
        )
        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = reward,
            dueAt = TaskDueDatePolicy.endOfDay(dueDate),
            taskPlan = plan,
        )
        authenticateAs()

        return CompletionFixture(
            taskId = task.id!!,
            planId = plan.id!!,
            membershipId = membership.id!!,
        )
    }

    private fun recurringTaskCreatedWithoutDueAt(
        completionTime: LocalDateTime,
    ): CompletionFixture {
        testClock.setCurrentDateTime(LocalDateTime.of(2026, 8, 1, 12, 0))
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)
        authenticateAs()

        val createdTask = taskService.createTask(
            householdId = household.id!!,
            task = TaskCreateDTO(
                title = "Weekly task without explicit deadline",
                reward = 20,
                recurrenceType = RecurrenceType.WEEKLY,
            ),
        )
        taskService.assignTask(createdTask.id)
        testClock.setCurrentDateTime(completionTime)

        return CompletionFixture(
            taskId = createdTask.id,
            planId = createdTask.taskPlanId!!,
            membershipId = membership.id!!,
        )
    }

    private fun reloadPlan(planId: UUID): TaskPlanEntity {
        entityManager.flush()
        entityManager.clear()
        return taskPlanRepository.findById(planId).orElseThrow()
    }

    private fun endOfDay(year: Int, month: Int, day: Int): LocalDateTime =
        TaskDueDatePolicy.endOfDay(LocalDate.of(year, month, day))

    private data class CompletionFixture(
        val taskId: UUID,
        val planId: UUID,
        val membershipId: UUID,
    )
}

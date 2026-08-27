package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.config.MutableTestClock
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.task.TaskCreateDTO
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.task.TaskUpdateDTO
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

class TaskPlanIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var recurrenceCalculator: TaskPlanRecurrenceCalculator

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var testClock: MutableTestClock

    @AfterEach
    fun resetClock() {
        testClock.reset()
    }

    @ParameterizedTest
    @EnumSource(RecurrenceType::class)
    fun `create recurring task should persist plan first task response and one activity`(
        recurrenceType: RecurrenceType,
    ) {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val selectedDueAt = LocalDate.now(clock).plusDays(5).atTime(12, 30)
        val normalizedDueAt = TaskDueDatePolicy.endOfDay(selectedDueAt.toLocalDate())
        val expectedSchedule = recurrenceCalculator.createSchedule(normalizedDueAt, recurrenceType)
        authenticateAs()

        val result = taskService.createTask(
            household.id!!,
            TaskCreateDTO(
                title = "Recurring cleaning",
                description = "Original plan description",
                reward = 30,
                dueAt = selectedDueAt,
                recurrenceType = recurrenceType,
            ),
        )

        entityManager.flush()
        entityManager.clear()
        val savedTask = taskRepository.findById(result.id).orElseThrow()
        val savedPlan = taskPlanRepository.findById(result.taskPlanId!!).orElseThrow()
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(savedTask.taskPlan?.id).isEqualTo(savedPlan.id)
        assertThat(savedTask.dueAt).isEqualTo(normalizedDueAt)
        assertThat(savedPlan.household.id).isEqualTo(household.id)
        assertThat(savedPlan.createdBy.id).isEqualTo(user.id)
        assertThat(savedPlan.title).isEqualTo("Recurring cleaning")
        assertThat(savedPlan.description).isEqualTo("Original plan description")
        assertThat(savedPlan.reward).isEqualTo(30)
        assertThat(savedPlan.recurrenceType).isEqualTo(recurrenceType)
        assertThat(savedPlan.nextDueAt).isEqualTo(expectedSchedule.nextDueAt)
        assertThat(savedPlan.monthlyAnchorDay).isEqualTo(expectedSchedule.monthlyAnchorDay)
        assertThat(savedPlan.monthlyLastDay).isEqualTo(expectedSchedule.monthlyLastDay)
        assertThat(savedPlan.isActive).isTrue()

        assertThat(result.taskPlanId).isEqualTo(savedPlan.id)
        assertThat(result.recurrenceType).isEqualTo(recurrenceType)
        assertThat(result.recurrenceActive).isTrue()
        assertThat(activities).hasSize(1)
        assertThat(activities.single().activityType).isEqualTo(ActivityType.TASK_CREATED)
    }

    @Test
    fun `create daily recurring task without due date should start cycle tomorrow`() {
        val fixture = createRecurringWithoutDueAt(
            startDate = LocalDate.of(2026, 8, 1),
            recurrenceType = RecurrenceType.DAILY,
        )

        assertThat(fixture.task.dueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 2)))
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 3)))
        assertThat(fixture.plan.monthlyAnchorDay).isNull()
        assertThat(fixture.plan.monthlyLastDay).isFalse()
        assertThat(activityRepository.findAll().map { it.activityType })
            .containsExactly(ActivityType.TASK_CREATED)
    }

    @Test
    fun `create weekly recurring task without due date should start cycle in seven days`() {
        val fixture = createRecurringWithoutDueAt(
            startDate = LocalDate.of(2026, 8, 1),
            recurrenceType = RecurrenceType.WEEKLY,
        )

        assertThat(fixture.task.dueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 8)))
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 15)))
        assertThat(fixture.plan.monthlyAnchorDay).isNull()
        assertThat(fixture.plan.monthlyLastDay).isFalse()
    }

    @Test
    fun `create monthly recurring task without due date should anchor to normal start day`() {
        val fixture = createRecurringWithoutDueAt(
            startDate = LocalDate.of(2026, 8, 10),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(fixture.task.dueAt).isEqualTo(endOfDay(LocalDate.of(2026, 9, 10)))
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 10, 10)))
        assertThat(fixture.plan.monthlyAnchorDay).isEqualTo(10)
        assertThat(fixture.plan.monthlyLastDay).isFalse()
    }

    @Test
    fun `create monthly recurring task without due date should preserve January 30 anchor`() {
        val fixture = createRecurringWithoutDueAt(
            startDate = LocalDate.of(2027, 1, 30),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(fixture.task.dueAt).isEqualTo(endOfDay(LocalDate.of(2027, 2, 28)))
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 3, 30)))
        assertThat(fixture.plan.monthlyAnchorDay).isEqualTo(30)
        assertThat(fixture.plan.monthlyLastDay).isFalse()
    }

    @Test
    fun `create monthly recurring task without due date should use last day anchor from January 31`() {
        val fixture = createRecurringWithoutDueAt(
            startDate = LocalDate.of(2027, 1, 31),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(fixture.task.dueAt).isEqualTo(endOfDay(LocalDate.of(2027, 2, 28)))
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 3, 31)))
        assertThat(fixture.plan.monthlyAnchorDay).isNull()
        assertThat(fixture.plan.monthlyLastDay).isTrue()
    }

    @Test
    fun `explicit recurring due date should remain first deadline and recurrence anchor`() {
        testClock.setCurrentDateTime(LocalDateTime.of(2026, 8, 1, 12, 0))
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        authenticateAs()

        val result = taskService.createTask(
            household.id!!,
            TaskCreateDTO(
                title = "Explicit weekly task",
                reward = 30,
                dueAt = LocalDate.of(2026, 8, 5).atStartOfDay(),
                recurrenceType = RecurrenceType.WEEKLY,
            ),
        )

        entityManager.flush()
        entityManager.clear()
        val savedTask = taskRepository.findById(result.id).orElseThrow()
        val savedPlan = taskPlanRepository.findById(result.taskPlanId!!).orElseThrow()

        assertThat(savedTask.dueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 5)))
        assertThat(savedPlan.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 12)))
    }

    @Test
    fun `create ordinary task should not create plan`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        authenticateAs()

        val result = taskService.createTask(
            household.id!!,
            TaskCreateDTO(title = "One-time cleaning", reward = 20),
        )

        assertThat(result.taskPlanId).isNull()
        assertThat(result.recurrenceType).isNull()
        assertThat(result.recurrenceActive).isFalse()
        assertThat(taskPlanRepository.findAll()).isEmpty()
    }

    @Test
    fun `update recurring task should change instance fields but preserve plan and due date`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val dueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(3))
        val taskPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = user,
            title = "Plan title",
            description = "Plan description",
            reward = 20,
            recurrenceType = RecurrenceType.DAILY,
        )
        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = dueAt,
            taskPlan = taskPlan,
        )
        authenticateAs()

        val result = taskService.updateTask(
            task.id!!,
            TaskUpdateDTO(
                title = "Changed instance",
                description = "Changed description",
                reward = 45,
                dueAt = null,
            ),
        )

        entityManager.flush()
        entityManager.clear()
        val savedTask = taskRepository.findById(task.id!!).orElseThrow()
        val savedPlan = taskPlanRepository.findById(taskPlan.id!!).orElseThrow()

        assertThat(result.title).isEqualTo("Changed instance")
        assertThat(result.description).isEqualTo("Changed description")
        assertThat(result.reward).isEqualTo(45)
        assertThat(result.dueAt).isEqualTo(dueAt)
        assertThat(savedTask.dueAt).isEqualTo(dueAt)
        assertThat(savedPlan.title).isEqualTo("Plan title")
        assertThat(savedPlan.description).isEqualTo("Plan description")
        assertThat(savedPlan.reward).isEqualTo(20)
        assertThat(savedPlan.recurrenceType).isEqualTo(RecurrenceType.DAILY)
    }

    @Test
    fun `update recurring task should allow same overdue calendar date without normalization`() {
        testClock.setCurrentDateTime(LocalDateTime.of(2026, 8, 27, 12, 0))
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val dueDate = LocalDate.of(2026, 8, 10)
        val taskPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = user)
        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = TaskDueDatePolicy.endOfDay(dueDate),
            taskPlan = taskPlan,
        )
        authenticateAs()

        val result = taskService.updateTask(
            task.id!!,
            TaskUpdateDTO(
                title = "Changed instance",
                reward = 25,
                dueAt = dueDate.atTime(8, 15),
            ),
        )

        assertThat(result.dueAt).isEqualTo(TaskDueDatePolicy.endOfDay(dueDate))
        assertThat(result.isOverdue).isTrue()
    }

    @Test
    fun `update recurring task should reject different due date`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val dueDate = LocalDate.now(clock).plusDays(3)
        val taskPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = user)
        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = TaskDueDatePolicy.endOfDay(dueDate),
            taskPlan = taskPlan,
        )
        authenticateAs()

        assertThatThrownBy {
            taskService.updateTask(
                task.id!!,
                TaskUpdateDTO(
                    title = "Changed instance",
                    reward = 25,
                    dueAt = dueDate.plusDays(1).atStartOfDay(),
                ),
            )
        }
            .isInstanceOf(BusinessConflictException::class.java)
            .hasMessage("Recurring task due date cannot be changed")
    }

    @Test
    fun `update recurring task should reject different past date as recurring change`() {
        testClock.setCurrentDateTime(LocalDateTime.of(2026, 8, 27, 12, 0))
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val taskPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = user)
        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = TaskDueDatePolicy.endOfDay(LocalDate.of(2026, 9, 10)),
            taskPlan = taskPlan,
        )
        authenticateAs()

        assertThatThrownBy {
            taskService.updateTask(
                task.id!!,
                TaskUpdateDTO(
                    title = "Changed instance",
                    reward = 25,
                    dueAt = LocalDate.of(2026, 8, 20).atStartOfDay(),
                ),
            )
        }
            .isInstanceOf(BusinessConflictException::class.java)
            .hasMessage("Recurring task due date cannot be changed")
    }

    private fun createRecurringWithoutDueAt(
        startDate: LocalDate,
        recurrenceType: RecurrenceType,
    ): RecurringCreationFixture {
        testClock.setCurrentDateTime(startDate.atTime(12, 0))
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        authenticateAs()

        val result = taskService.createTask(
            household.id!!,
            TaskCreateDTO(
                title = "Recurring without explicit deadline",
                description = "Created from clock date",
                reward = 30,
                recurrenceType = recurrenceType,
            ),
        )

        entityManager.flush()
        entityManager.clear()

        return RecurringCreationFixture(
            task = taskRepository.findById(result.id).orElseThrow(),
            plan = taskPlanRepository.findById(result.taskPlanId!!).orElseThrow(),
        )
    }

    private fun endOfDay(date: LocalDate): LocalDateTime =
        TaskDueDatePolicy.endOfDay(date)

    private data class RecurringCreationFixture(
        val task: com.cleaningapp.backend.task.TaskEntity,
        val plan: TaskPlanEntity,
    )
}

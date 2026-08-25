package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate

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

    @ParameterizedTest
    @EnumSource(RecurrenceType::class)
    fun `create recurring task should require due date`(recurrenceType: RecurrenceType) {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        authenticateAs()

        assertThatThrownBy {
            taskService.createTask(
                household.id!!,
                TaskCreateDTO(
                    title = "Recurring cleaning",
                    reward = 30,
                    recurrenceType = recurrenceType,
                ),
            )
        }.isInstanceOf(BusinessConflictException::class.java)

        assertThat(taskPlanRepository.findAll()).isEmpty()
        assertThat(taskRepository.findAll()).isEmpty()
        assertThat(activityRepository.findAll()).isEmpty()
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
    fun `update recurring task should allow same due date after normalization`() {
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

        val result = taskService.updateTask(
            task.id!!,
            TaskUpdateDTO(
                title = "Changed instance",
                reward = 25,
                dueAt = dueDate.atTime(8, 15),
            ),
        )

        assertThat(result.dueAt).isEqualTo(TaskDueDatePolicy.endOfDay(dueDate))
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
        }.isInstanceOf(BusinessConflictException::class.java)
    }
}

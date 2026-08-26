package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.config.MutableTestClock
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.TaskPlanNotFoundException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.user.UserEntity
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TaskPlanRecurrenceUpdateIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskPlanService: TaskPlanService

    @Autowired
    private lateinit var generationService: TaskPlanGenerationService

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var recurrenceCalculator: TaskPlanRecurrenceCalculator

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var testClock: MutableTestClock

    @AfterEach
    fun resetClock() {
        testClock.reset()
    }

    @Test
    fun `creator should change unfinished daily plan to weekly without changing current task`() {
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val membership = testDataFactory.createTestMembership(user = owner, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = endOfDay(2026, 9, 11),
        )
        val assignedAt = LocalDateTime.of(2026, 9, 1, 9, 30)
        val task = testDataFactory.createTestTask(
            household = household,
            createdBy = owner,
            title = "Current recurring task",
            description = "Must remain unchanged",
            reward = 35,
            dueAt = endOfDay(2026, 9, 10),
            taskPlan = plan,
            assignedTo = membership,
            assignedAt = assignedAt,
        )
        authenticateAs()

        taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.WEEKLY)

        val savedPlan = reloadPlan(plan.id!!)
        val savedTask = taskRepository.findById(task.id!!).orElseThrow()
        assertThat(savedPlan.recurrenceType).isEqualTo(RecurrenceType.WEEKLY)
        assertThat(savedPlan.nextDueAt).isEqualTo(endOfDay(2026, 9, 17))
        assertThat(savedPlan.monthlyAnchorDay).isNull()
        assertThat(savedPlan.monthlyLastDay).isFalse()
        assertThat(savedTask.dueAt).isEqualTo(endOfDay(2026, 9, 10))
        assertThat(savedTask.title).isEqualTo("Current recurring task")
        assertThat(savedTask.description).isEqualTo("Must remain unchanged")
        assertThat(savedTask.reward).isEqualTo(35)
        assertThat(savedTask.assignedTo?.id).isEqualTo(membership.id)
        assertThat(savedTask.assignedAt).isEqualTo(assignedAt)
        assertThat(savedTask.isCompleted).isFalse()
        assertThat(savedTask.taskPlan?.id).isEqualTo(plan.id)
    }

    @Test
    fun `unfinished weekly plan should change to daily from current task due date`() {
        val fixture = recurringPlanWithFreeTask(
            recurrenceType = RecurrenceType.WEEKLY,
            taskDueAt = endOfDay(2026, 9, 10),
            nextDueAt = endOfDay(2026, 9, 17),
        )

        taskPlanService.updateRecurrence(fixture.planId, RecurrenceType.DAILY)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.recurrenceType).isEqualTo(RecurrenceType.DAILY)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 11))
        assertThat(plan.monthlyAnchorDay).isNull()
        assertThat(plan.monthlyLastDay).isFalse()
    }

    @Test
    fun `unfinished plan should change to monthly with normal anchor`() {
        val fixture = recurringPlanWithFreeTask(
            recurrenceType = RecurrenceType.DAILY,
            taskDueAt = endOfDay(2026, 9, 10),
            nextDueAt = endOfDay(2026, 9, 11),
        )

        taskPlanService.updateRecurrence(fixture.planId, RecurrenceType.MONTHLY)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.recurrenceType).isEqualTo(RecurrenceType.MONTHLY)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 10, 10))
        assertThat(plan.monthlyAnchorDay).isEqualTo(10)
        assertThat(plan.monthlyLastDay).isFalse()
    }

    @Test
    fun `unfinished plan should derive monthly last day schedule from current task`() {
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val januaryPlan = createPlanWithFreeTask(
            owner = owner,
            household = household,
            taskDueAt = endOfDay(2027, 1, 31),
        )
        val februaryPlan = createPlanWithFreeTask(
            owner = owner,
            household = household,
            taskDueAt = endOfDay(2027, 2, 28),
        )
        authenticateAs()

        taskPlanService.updateRecurrence(januaryPlan, RecurrenceType.MONTHLY)
        taskPlanService.updateRecurrence(februaryPlan, RecurrenceType.MONTHLY)

        val afterJanuary = taskPlanRepository.findById(januaryPlan).orElseThrow()
        val afterFebruary = taskPlanRepository.findById(februaryPlan).orElseThrow()
        assertThat(afterJanuary.nextDueAt).isEqualTo(endOfDay(2027, 2, 28))
        assertThat(afterJanuary.monthlyAnchorDay).isNull()
        assertThat(afterJanuary.monthlyLastDay).isTrue()
        assertThat(afterFebruary.nextDueAt).isEqualTo(endOfDay(2027, 3, 31))
        assertThat(afterFebruary.monthlyAnchorDay).isNull()
        assertThat(afterFebruary.monthlyLastDay).isTrue()
    }

    @Test
    fun `monthly anchor 30 should clamp February and recover in March`() {
        val fixture = recurringPlanWithFreeTask(
            recurrenceType = RecurrenceType.DAILY,
            taskDueAt = endOfDay(2026, 1, 30),
            nextDueAt = endOfDay(2026, 1, 31),
        )

        taskPlanService.updateRecurrence(fixture.planId, RecurrenceType.MONTHLY)

        val plan = reloadPlan(fixture.planId)
        val followingDueAt = recurrenceCalculator.calculateNextDueAt(
            currentDueAt = plan.nextDueAt,
            recurrenceType = plan.recurrenceType,
            monthlyAnchorDay = plan.monthlyAnchorDay,
            monthlyLastDay = plan.monthlyLastDay,
        )
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 2, 28))
        assertThat(plan.monthlyAnchorDay).isEqualTo(30)
        assertThat(plan.monthlyLastDay).isFalse()
        assertThat(followingDueAt).isEqualTo(endOfDay(2026, 3, 30))
    }

    @Test
    fun `changing from monthly should clear monthly metadata for daily and weekly`() {
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val dailyPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueAt = endOfDay(2026, 3, 30),
            monthlyAnchorDay = 30,
        )
        val weeklyPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueAt = endOfDay(2026, 3, 31),
            monthlyAnchorDay = null,
            monthlyLastDay = true,
        )
        authenticateAs()

        taskPlanService.updateRecurrence(dailyPlan.id!!, RecurrenceType.DAILY)
        taskPlanService.updateRecurrence(weeklyPlan.id!!, RecurrenceType.WEEKLY)

        listOf(dailyPlan.id!!, weeklyPlan.id!!).forEach { planId ->
            val plan = taskPlanRepository.findById(planId).orElseThrow()
            assertThat(plan.monthlyAnchorDay).isNull()
            assertThat(plan.monthlyLastDay).isFalse()
        }
        assertThat(dailyPlan.nextDueAt).isEqualTo(endOfDay(2026, 3, 30))
        assertThat(weeklyPlan.nextDueAt).isEqualTo(endOfDay(2026, 3, 31))
    }

    @Test
    fun `same recurrence should be idempotent and preserve schedule metadata`() {
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueAt = endOfDay(2026, 2, 28),
            monthlyAnchorDay = 30,
            monthlyLastDay = false,
        )
        authenticateAs()

        taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.MONTHLY)

        val savedPlan = reloadPlan(plan.id!!)
        assertThat(savedPlan.nextDueAt).isEqualTo(endOfDay(2026, 2, 28))
        assertThat(savedPlan.monthlyAnchorDay).isEqualTo(30)
        assertThat(savedPlan.monthlyLastDay).isFalse()
    }

    @Test
    fun `without unfinished task monthly update should keep nearest due date and generation should use new recurrence`() {
        setNow(2026, 9, 12)
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.WEEKLY,
            nextDueAt = endOfDay(2026, 9, 12),
        )
        authenticateAs()

        taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.MONTHLY)

        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 12))
        assertThat(plan.monthlyAnchorDay).isEqualTo(12)
        assertThat(plan.monthlyLastDay).isFalse()

        val generation = generationService.generateDueTasks()
        val tasks = taskRepository.findAllByTaskPlanId(plan.id!!)
        assertThat(generation.created).isEqualTo(1)
        assertThat(tasks).hasSize(1)
        assertThat(tasks.single().dueAt).isEqualTo(endOfDay(2026, 9, 12))
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 10, 12))
    }

    @Test
    fun `without unfinished task weekly update should keep nearest due date until generation`() {
        setNow(2026, 9, 12)
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = endOfDay(2026, 9, 12),
        )
        authenticateAs()

        taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.WEEKLY)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 12))

        generationService.generateDueTasks()

        assertThat(taskRepository.findAllByTaskPlanId(plan.id!!).single().dueAt)
            .isEqualTo(endOfDay(2026, 9, 12))
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 19))
    }

    @Test
    fun `overdue completion after recurrence update should use updated recurrence`() {
        setNow(2026, 9, 15, 12, 0)
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val membership = testDataFactory.createTestMembership(user = owner, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = endOfDay(2026, 9, 11),
        )
        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = owner,
            assignedTo = membership,
            dueAt = endOfDay(2026, 9, 10),
            taskPlan = plan,
        )
        authenticateAs()

        taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.WEEKLY)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 17))

        taskService.completeTask(task.id!!)

        assertThat(plan.recurrenceType).isEqualTo(RecurrenceType.WEEKLY)
        assertThat(plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 22))
        assertThat(task.isCompleted).isTrue()
    }

    @Test
    fun `cancellation after recurrence update should keep delete semantics`() {
        val fixture = recurringPlanWithFreeTask(
            recurrenceType = RecurrenceType.DAILY,
            taskDueAt = endOfDay(2026, 9, 10),
            nextDueAt = endOfDay(2026, 9, 11),
        )

        taskPlanService.updateRecurrence(fixture.planId, RecurrenceType.WEEKLY)
        taskPlanService.cancelTaskPlan(fixture.planId)

        val plan = reloadPlan(fixture.planId)
        assertThat(plan.recurrenceType).isEqualTo(RecurrenceType.WEEKLY)
        assertThat(plan.isActive).isFalse()
        assertThat(taskRepository.findAllByTaskPlanId(fixture.planId)).hasSize(1)
    }

    @Test
    fun `household member should not update plan created by another user`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = currentUser, household = household)
        val plan = testDataFactory.createTestTaskPlan(household = household, createdBy = creator)
        authenticateAs()

        assertThatThrownBy {
            taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.WEEKLY)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `user from another household should not update task plan`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()
        val planHousehold = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = planHousehold)
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = currentUser)
        testDataFactory.createTestMembership(user = currentUser, household = otherHousehold)
        val plan = testDataFactory.createTestTaskPlan(household = planHousehold, createdBy = creator)
        authenticateAs()

        assertThatThrownBy {
            taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.WEEKLY)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `inactive membership should not update task plan`() {
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household, isUserActive = false)
        val plan = testDataFactory.createTestTaskPlan(household = household, createdBy = owner)
        authenticateAs()

        assertThatThrownBy {
            taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.WEEKLY)
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `inactive task plan should not be updated`() {
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            isActive = false,
        )
        authenticateAs()

        assertThatThrownBy {
            taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.WEEKLY)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `missing task plan should be reported`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            taskPlanService.updateRecurrence(UUID.randomUUID(), RecurrenceType.WEEKLY)
        }.isInstanceOf(TaskPlanNotFoundException::class.java)
    }

    @Test
    fun `recurring unfinished task without due date should expose inconsistent state`() {
        val fixture = recurringPlanWithFreeTask(
            recurrenceType = RecurrenceType.DAILY,
            taskDueAt = null,
            nextDueAt = endOfDay(2026, 9, 11),
        )

        assertThatThrownBy {
            taskPlanService.updateRecurrence(fixture.planId, RecurrenceType.WEEKLY)
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Recurring task must have a due date")
    }

    private fun recurringPlanWithFreeTask(
        recurrenceType: RecurrenceType,
        taskDueAt: LocalDateTime?,
        nextDueAt: LocalDateTime,
    ): PlanFixture {
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = recurrenceType,
            nextDueAt = nextDueAt,
        )
        testDataFactory.createTestFreeTask(
            household = household,
            createdBy = owner,
            dueAt = taskDueAt,
            taskPlan = plan,
        )
        authenticateAs()
        return PlanFixture(plan.id!!)
    }

    private fun createPlanWithFreeTask(
        owner: UserEntity,
        household: HouseholdEntity,
        taskDueAt: LocalDateTime,
    ): UUID {
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = taskDueAt.plusDays(1),
        )
        testDataFactory.createTestFreeTask(
            household = household,
            createdBy = owner,
            dueAt = taskDueAt,
            taskPlan = plan,
        )
        return plan.id!!
    }

    private fun reloadPlan(planId: UUID): TaskPlanEntity {
        entityManager.flush()
        entityManager.clear()
        return taskPlanRepository.findById(planId).orElseThrow()
    }

    private fun setNow(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0) {
        testClock.setCurrentDateTime(LocalDateTime.of(year, month, day, hour, minute))
    }

    private fun endOfDay(year: Int, month: Int, day: Int): LocalDateTime =
        TaskDueDatePolicy.endOfDay(LocalDate.of(year, month, day))

    private data class PlanFixture(
        val planId: UUID,
    )
}

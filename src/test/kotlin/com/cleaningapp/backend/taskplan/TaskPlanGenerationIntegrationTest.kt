package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.config.MutableTestClock
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.task.TaskService
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime

class TaskPlanGenerationIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var generationService: TaskPlanGenerationService

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

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
    fun `ready daily plan should create copied task activity and advance next due date`() {
        setNow(2026, 9, 12)
        val owner = createLocalUserForValidToken(name = "Plan creator")
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val membership = testDataFactory.createTestMembership(user = owner, household = household)
        val oldNextDueAt = endOfDay(2026, 9, 12)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            title = "Automatic cleaning",
            description = "Generated description",
            reward = 35,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = oldNextDueAt,
        )

        val result = generationService.generateDueTasks()

        entityManager.flush()
        entityManager.clear()
        val tasks = taskRepository.findAllByTaskPlanId(plan.id!!)
        val savedPlan = taskPlanRepository.findById(plan.id!!).orElseThrow()
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(result).isEqualTo(TaskPlanGenerationResult(1, 1, 0, 0))
        assertThat(tasks).hasSize(1)
        assertThat(tasks.single().household.id).isEqualTo(household.id)
        assertThat(tasks.single().createdBy.id).isEqualTo(owner.id)
        assertThat(tasks.single().title).isEqualTo("Automatic cleaning")
        assertThat(tasks.single().description).isEqualTo("Generated description")
        assertThat(tasks.single().reward).isEqualTo(35)
        assertThat(tasks.single().dueAt).isEqualTo(oldNextDueAt)
        assertThat(tasks.single().taskPlan?.id).isEqualTo(plan.id)
        assertThat(savedPlan.nextDueAt).isEqualTo(endOfDay(2026, 9, 13))
        assertThat(activities).hasSize(1)
        assertThat(activities.single().activityType).isEqualTo(ActivityType.TASK_CREATED)
        assertThat(activities.single().member.id).isEqualTo(membership.id)
    }

    @Test
    fun `future plan should not create task`() {
        setNow(2026, 9, 12)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = endOfDay(2026, 9, 13),
        )

        val result = generationService.generateDueTasks()

        assertThat(result.selected).isZero()
        assertThat(taskRepository.findAllByTaskPlanId(fixture.plan.id!!)).isEmpty()
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 13))
    }

    @Test
    fun `inactive ready plan should not create task`() {
        setNow(2026, 9, 12)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = endOfDay(2026, 9, 12),
            isActive = false,
        )

        val result = generationService.generateDueTasks()

        assertThat(result.selected).isZero()
        assertThat(taskRepository.findAllByTaskPlanId(fixture.plan.id!!)).isEmpty()
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 12))
    }

    @Test
    fun `existing free unfinished task should block generation without moving schedule`() {
        setNow(2026, 9, 12)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = endOfDay(2026, 9, 12),
        )
        val existingTask = testDataFactory.createTestFreeTask(
            household = fixture.household,
            createdBy = fixture.owner,
            dueAt = endOfDay(2026, 9, 5),
            taskPlan = fixture.plan,
        )

        generationService.generateDueTasks()

        assertThat(taskRepository.findAllByTaskPlanId(fixture.plan.id!!).map { it.id })
            .containsExactly(existingTask.id)
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 12))
    }

    @Test
    fun `existing assigned unfinished task should block generation without moving schedule`() {
        setNow(2026, 9, 12)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.WEEKLY,
            nextDueAt = endOfDay(2026, 9, 12),
        )
        val existingTask = testDataFactory.createTestAssignedTask(
            household = fixture.household,
            createdBy = fixture.owner,
            assignedTo = fixture.membership,
            dueAt = endOfDay(2026, 9, 5),
            taskPlan = fixture.plan,
        )

        generationService.generateDueTasks()

        assertThat(taskRepository.findAllByTaskPlanId(fixture.plan.id!!).map { it.id })
            .containsExactly(existingTask.id)
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 12))
    }

    @Test
    fun `catch up should create one overdue task with stored due date`() {
        setNow(2026, 9, 15)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = endOfDay(2026, 9, 12),
        )

        generationService.generateDueTasks()

        val tasks = taskRepository.findAllByTaskPlanId(fixture.plan.id!!)
        authenticateAs(fixture.owner.firebaseUid)
        val response = taskService.getTaskById(tasks.single().id!!)

        assertThat(tasks).hasSize(1)
        assertThat(tasks.single().dueAt).isEqualTo(endOfDay(2026, 9, 12))
        assertThat(response.isOverdue).isTrue()
        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 13))
    }

    @Test
    fun `weekly generation should advance seven calendar days`() {
        setNow(2026, 9, 12)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.WEEKLY,
            nextDueAt = endOfDay(2026, 9, 12),
        )

        generationService.generateDueTasks()

        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2026, 9, 19))
    }

    @Test
    fun `monthly generation should preserve normal anchor day`() {
        setNow(2026, 9, 5)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueAt = endOfDay(2026, 9, 5),
            monthlyAnchorDay = 5,
        )

        generationService.generateDueTasks()

        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2026, 10, 5))
        assertThat(fixture.plan.monthlyAnchorDay).isEqualTo(5)
        assertThat(fixture.plan.monthlyLastDay).isFalse()
    }

    @Test
    fun `monthly generation should preserve last day semantics`() {
        setNow(2027, 2, 28)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueAt = endOfDay(2027, 2, 28),
            monthlyAnchorDay = null,
            monthlyLastDay = true,
        )

        generationService.generateDueTasks()

        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2027, 3, 31))
        assertThat(fixture.plan.monthlyAnchorDay).isNull()
        assertThat(fixture.plan.monthlyLastDay).isTrue()
    }

    @Test
    fun `monthly anchor should clamp to short February`() {
        setNow(2027, 1, 30)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueAt = endOfDay(2027, 1, 30),
            monthlyAnchorDay = 30,
        )

        generationService.generateDueTasks()

        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2027, 2, 28))
        assertThat(fixture.plan.monthlyAnchorDay).isEqualTo(30)
    }

    @Test
    fun `monthly anchor should return after February`() {
        setNow(2027, 2, 28)
        val fixture = createPlan(
            recurrenceType = RecurrenceType.MONTHLY,
            nextDueAt = endOfDay(2027, 2, 28),
            monthlyAnchorDay = 30,
        )

        generationService.generateDueTasks()

        assertThat(fixture.plan.nextDueAt).isEqualTo(endOfDay(2027, 3, 30))
        assertThat(fixture.plan.monthlyAnchorDay).isEqualTo(30)
        assertThat(fixture.plan.monthlyLastDay).isFalse()
    }

    private fun createPlan(
        recurrenceType: RecurrenceType,
        nextDueAt: LocalDateTime,
        monthlyAnchorDay: Int? = null,
        monthlyLastDay: Boolean = false,
        isActive: Boolean = true,
    ): PlanFixture {
        val owner = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val membership = testDataFactory.createTestMembership(user = owner, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = recurrenceType,
            nextDueAt = nextDueAt,
            monthlyAnchorDay = monthlyAnchorDay,
            monthlyLastDay = monthlyLastDay,
            isActive = isActive,
        )

        return PlanFixture(owner, household, membership, plan)
    }

    private fun setNow(year: Int, month: Int, day: Int) {
        testClock.setCurrentDateTime(LocalDate.of(year, month, day).atTime(12, 0))
    }

    private fun endOfDay(year: Int, month: Int, day: Int): LocalDateTime =
        TaskDueDatePolicy.endOfDay(LocalDate.of(year, month, day))

    private data class PlanFixture(
        val owner: com.cleaningapp.backend.user.UserEntity,
        val household: com.cleaningapp.backend.household.HouseholdEntity,
        val membership: com.cleaningapp.backend.userhousehold.UserHouseholdEntity,
        val plan: TaskPlanEntity,
    )
}

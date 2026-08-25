package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Clock
import java.time.LocalDate

class TaskPlanGenerationRollbackIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var generationService: TaskPlanGenerationService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var clock: Clock

    @MockitoSpyBean
    private lateinit var recurrenceCalculator: TaskPlanRecurrenceCalculator

    @Test
    fun `one plan failure should roll back its task and allow remaining plans to continue`() {
        val today = LocalDate.now(clock)
        val failedDueAt = TaskDueDatePolicy.endOfDay(today.minusDays(1))
        val successfulDueAt = TaskDueDatePolicy.endOfDay(today)
        val failedPlan = createPlan(RecurrenceType.DAILY, failedDueAt)
        val successfulPlan = createPlan(RecurrenceType.WEEKLY, successfulDueAt)
        Mockito.doThrow(IllegalStateException("Artificial schedule failure"))
            .`when`(recurrenceCalculator)
            .calculateNextDueAt(
                currentDueAt = failedDueAt,
                recurrenceType = RecurrenceType.DAILY,
                monthlyAnchorDay = null,
                monthlyLastDay = false,
            )

        val result = generationService.generateDueTasks()

        val reloadedFailedPlan = taskPlanRepository.findById(failedPlan.id!!).orElseThrow()
        val reloadedSuccessfulPlan = taskPlanRepository.findById(successfulPlan.id!!).orElseThrow()

        assertThat(result).isEqualTo(TaskPlanGenerationResult(2, 1, 0, 1))
        assertThat(taskRepository.findAllByTaskPlanId(failedPlan.id!!)).isEmpty()
        assertThat(reloadedFailedPlan.nextDueAt).isEqualTo(failedDueAt)
        assertThat(taskRepository.findAllByTaskPlanId(successfulPlan.id!!)).hasSize(1)
        assertThat(reloadedSuccessfulPlan.nextDueAt)
            .isEqualTo(TaskDueDatePolicy.endOfDay(today.plusDays(7)))
        assertThat(activityRepository.findAll()).hasSize(1)
    }

    private fun createPlan(
        recurrenceType: RecurrenceType,
        nextDueAt: java.time.LocalDateTime,
    ): TaskPlanEntity {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        return testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = recurrenceType,
            nextDueAt = nextDueAt,
        )
    }
}

package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate

class TaskPlanGenerationConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var processor: TaskPlanGenerationProcessor

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `concurrent generation should create exactly one unfinished task`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val today = LocalDate.now(clock)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = TaskDueDatePolicy.endOfDay(today),
        )

        val results = runConcurrently(threadCount = 2) {
            processor.generateTaskForPlan(plan.id!!)
        }

        val tasks = taskRepository.findAllByTaskPlanId(plan.id!!)
        val savedPlan = taskPlanRepository.findById(plan.id!!).orElseThrow()
        val creationActivities = activityRepository.findAll().filter {
            it.activityType == ActivityType.TASK_CREATED
        }

        assertThat(failureCount(results)).isZero()
        assertThat(results.map { it.getOrThrow() }).containsExactlyInAnyOrder(true, false)
        assertThat(tasks.filterNot { it.isCompleted }).hasSize(1)
        assertThat(savedPlan.nextDueAt).isEqualTo(TaskDueDatePolicy.endOfDay(today.plusDays(1)))
        assertThat(creationActivities).hasSize(1)
    }
}

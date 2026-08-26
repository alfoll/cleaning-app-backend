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

class TaskPlanRecurrenceUpdateConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var taskPlanService: TaskPlanService

    @Autowired
    private lateinit var generationProcessor: TaskPlanGenerationProcessor

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `recurrence update and generation should serialize through task plan lock`() {
        val owner = testDataFactory.createTestUser(
            firebaseUid = defaultFirebaseUid,
            email = defaultFirebaseEmail,
        )
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)
        val today = LocalDate.now(clock)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = TaskDueDatePolicy.endOfDay(today),
        )

        val results = runConcurrently(threadCount = 2) { index ->
            if (index == 0) {
                authenticatedAs(defaultFirebaseUid) {
                    taskPlanService.updateRecurrence(plan.id!!, RecurrenceType.MONTHLY)
                }
                true
            } else {
                generationProcessor.generateTaskForPlan(plan.id!!)
            }
        }

        val savedPlan = taskPlanRepository.findById(plan.id!!).orElseThrow()
        val tasks = taskRepository.findAllByTaskPlanId(plan.id!!)
        val creationActivities = activityRepository.findAll().filter {
            it.activityType == ActivityType.TASK_CREATED
        }

        assertThat(failureCount(results)).isZero()
        assertThat(results.map { it.getOrThrow() }).containsOnly(true)
        assertThat(tasks).hasSize(1)
        assertThat(tasks.single().dueAt).isEqualTo(TaskDueDatePolicy.endOfDay(today))
        assertThat(savedPlan.recurrenceType).isEqualTo(RecurrenceType.MONTHLY)
        assertThat(savedPlan.monthlyAnchorDay).isEqualTo(today.dayOfMonth)
        assertThat(savedPlan.monthlyLastDay).isFalse()
        assertThat(savedPlan.nextDueAt).isEqualTo(TaskDueDatePolicy.endOfDay(today.plusMonths(1)))
        assertThat(creationActivities).hasSize(1)
    }
}

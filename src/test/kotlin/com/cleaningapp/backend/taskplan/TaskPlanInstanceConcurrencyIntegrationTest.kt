package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate

class TaskPlanInstanceConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var taskPlanInstanceService: TaskPlanInstanceService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `concurrent instance creation should leave exactly one unfinished task`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val taskPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = owner)
        val dueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(1))

        val results = runConcurrently(threadCount = 2) {
            taskPlanInstanceService.createTaskInstance(taskPlan.id!!, dueAt)
        }

        val unfinishedTasks = taskRepository.findAllByTaskPlanId(taskPlan.id!!)
            .filterNot { it.isCompleted }

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, BusinessConflictException::class.java)
        assertThat(unfinishedTasks).hasSize(1)
    }
}

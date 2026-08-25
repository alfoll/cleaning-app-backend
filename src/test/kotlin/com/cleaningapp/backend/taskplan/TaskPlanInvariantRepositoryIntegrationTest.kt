package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskEntity
import com.cleaningapp.backend.task.TaskRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.LocalDate

class TaskPlanInvariantRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `database should reject two unfinished tasks for same plan`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val taskPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = owner)
        testDataFactory.createTestFreeTask(
            household = household,
            createdBy = owner,
            dueAt = futureDueAt(1),
            taskPlan = taskPlan,
        )
        entityManager.flush()

        val secondTask = TaskEntity(
            title = "Second unfinished task",
            reward = 20,
            dueAt = futureDueAt(2),
        ).apply {
            this.household = household
            this.createdBy = owner
            this.taskPlan = taskPlan
        }

        assertThatThrownBy {
            taskRepository.saveAndFlush(secondTask)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should allow multiple completed tasks for same plan`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val membership = testDataFactory.createTestMembership(user = owner, household = household)
        val taskPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = owner)

        repeat(3) { index ->
            testDataFactory.createTestCompletedTask(
                household = household,
                createdBy = owner,
                completedBy = membership,
                dueAt = futureDueAt(index.toLong() + 1),
                taskPlan = taskPlan,
            )
        }
        entityManager.flush()

        assertThat(taskRepository.findAllByTaskPlanId(taskPlan.id!!))
            .hasSize(3)
            .allMatch { it.isCompleted }
    }

    @Test
    fun `ordinary unfinished tasks should not be limited by plan index`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        repeat(3) { index ->
            testDataFactory.createTestFreeTask(
                household = household,
                createdBy = owner,
                dueAt = futureDueAt(index.toLong() + 1),
            )
        }
        entityManager.flush()

        assertThat(taskRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!))
            .hasSize(3)
            .allMatch { it.taskPlan == null && !it.isCompleted }
    }

    private fun futureDueAt(days: Long) =
        TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(days))
}

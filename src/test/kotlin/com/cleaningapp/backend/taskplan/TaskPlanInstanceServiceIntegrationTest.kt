package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate

class TaskPlanInstanceServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskPlanInstanceService: TaskPlanInstanceService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `free unfinished task should block next instance`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val taskPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = owner)
        testDataFactory.createTestFreeTask(
            household = household,
            createdBy = owner,
            dueAt = futureDueAt(1),
            taskPlan = taskPlan,
        )

        assertThatThrownBy {
            taskPlanInstanceService.createTaskInstance(taskPlan.id!!, futureDueAt(2))
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `assigned unfinished task should block next instance`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val membership = testDataFactory.createTestMembership(user = owner, household = household)
        val taskPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = owner)
        testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = owner,
            assignedTo = membership,
            dueAt = futureDueAt(1),
            taskPlan = taskPlan,
        )

        assertThatThrownBy {
            taskPlanInstanceService.createTaskInstance(taskPlan.id!!, futureDueAt(2))
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `completed task should allow next instance copied from plan`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val membership = testDataFactory.createTestMembership(user = owner, household = household)
        val taskPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            title = "Plan title",
            description = "Plan description",
            reward = 35,
        )
        testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = owner,
            completedBy = membership,
            dueAt = futureDueAt(1),
            taskPlan = taskPlan,
        )
        val requestedDueAt = LocalDate.now(clock).plusDays(2).atTime(9, 15)

        val created = taskPlanInstanceService.createTaskInstance(taskPlan.id!!, requestedDueAt)

        entityManager.flush()
        entityManager.clear()
        val saved = taskRepository.findById(created.id!!).orElseThrow()

        assertThat(saved.household.id).isEqualTo(household.id)
        assertThat(saved.createdBy.id).isEqualTo(owner.id)
        assertThat(saved.title).isEqualTo("Plan title")
        assertThat(saved.description).isEqualTo("Plan description")
        assertThat(saved.reward).isEqualTo(35)
        assertThat(saved.taskPlan?.id).isEqualTo(taskPlan.id)
        assertThat(saved.dueAt).isEqualTo(TaskDueDatePolicy.endOfDay(requestedDueAt.toLocalDate()))
        assertThat(saved.isCompleted).isFalse()
        assertThat(saved.assignedTo).isNull()
        assertThat(saved.assignedAt).isNull()
    }

    @Test
    fun `different plans should each allow one unfinished task`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val firstPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = owner)
        val secondPlan = testDataFactory.createTestTaskPlan(household = household, createdBy = owner)

        taskPlanInstanceService.createTaskInstance(firstPlan.id!!, futureDueAt(1))
        taskPlanInstanceService.createTaskInstance(secondPlan.id!!, futureDueAt(1))

        assertThat(taskRepository.existsByTaskPlanIdAndIsCompletedFalse(firstPlan.id!!)).isTrue()
        assertThat(taskRepository.existsByTaskPlanIdAndIsCompletedFalse(secondPlan.id!!)).isTrue()
    }

    @Test
    fun `inactive plan should reject task instance creation`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val taskPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = owner,
            isActive = false,
        )

        assertThatThrownBy {
            taskPlanInstanceService.createTaskInstance(taskPlan.id!!, futureDueAt(1))
        }.isInstanceOf(BusinessConflictException::class.java)

        assertThat(taskRepository.findAllByTaskPlanId(taskPlan.id!!)).isEmpty()
    }

    private fun futureDueAt(days: Long) =
        TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(days))
}

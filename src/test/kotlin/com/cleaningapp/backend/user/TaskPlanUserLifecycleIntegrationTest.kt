package com.cleaningapp.backend.user

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.taskplan.TaskPlanGenerationService
import com.cleaningapp.backend.taskplan.TaskPlanRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate

class TaskPlanUserLifecycleIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var generationService: TaskPlanGenerationService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `delete user should deactivate all owned plans and preserve other owners plans and tasks`() {
        val deletedUser = createLocalUserForValidToken()
        val firstOtherUser = testDataFactory.createTestUser()
        val secondOtherUser = testDataFactory.createTestUser()
        val firstHousehold = testDataFactory.createTestHousehold(createdBy = deletedUser)
        val secondHousehold = testDataFactory.createTestHousehold(createdBy = deletedUser)
        val legacyHousehold = testDataFactory.createTestHousehold(createdBy = firstOtherUser)
        val firstMembership = testDataFactory.createTestMembership(user = deletedUser, household = firstHousehold)
        val secondMembership = testDataFactory.createTestMembership(user = deletedUser, household = secondHousehold)
        testDataFactory.createTestMembership(user = firstOtherUser, household = firstHousehold)
        testDataFactory.createTestMembership(user = secondOtherUser, household = secondHousehold)
        testDataFactory.createTestMembership(user = firstOtherUser, household = legacyHousehold)
        testDataFactory.createTestMembership(
            user = deletedUser,
            household = legacyHousehold,
            isUserActive = false,
        )
        val firstOwnedPlan = testDataFactory.createTestTaskPlan(
            household = firstHousehold,
            createdBy = deletedUser,
        )
        val readyOwnedPlan = testDataFactory.createTestTaskPlan(
            household = secondHousehold,
            createdBy = deletedUser,
            nextDueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock)),
        )
        val legacyOwnedPlan = testDataFactory.createTestTaskPlan(
            household = legacyHousehold,
            createdBy = deletedUser,
        )
        val otherOwnersFirstPlan = testDataFactory.createTestTaskPlan(
            household = firstHousehold,
            createdBy = firstOtherUser,
        )
        val otherOwnersSecondPlan = testDataFactory.createTestTaskPlan(
            household = secondHousehold,
            createdBy = secondOtherUser,
        )
        val unfinishedTask = testDataFactory.createTestFreeTask(
            household = firstHousehold,
            createdBy = deletedUser,
            dueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(1)),
            taskPlan = firstOwnedPlan,
        )
        authenticateAs()

        userService.deleteUser()

        entityManager.flush()
        entityManager.clear()
        assertThat(userRepository.findById(deletedUser.id!!).orElseThrow().isActive).isFalse()
        assertThat(userHouseholdRepository.findById(firstMembership.id!!).orElseThrow().isUserActive).isFalse()
        assertThat(userHouseholdRepository.findById(secondMembership.id!!).orElseThrow().isUserActive).isFalse()
        assertThat(taskPlanRepository.findById(firstOwnedPlan.id!!).orElseThrow().isActive).isFalse()
        assertThat(taskPlanRepository.findById(readyOwnedPlan.id!!).orElseThrow().isActive).isFalse()
        assertThat(taskPlanRepository.findById(legacyOwnedPlan.id!!).orElseThrow().isActive).isFalse()
        assertThat(taskPlanRepository.findById(otherOwnersFirstPlan.id!!).orElseThrow().isActive).isTrue()
        assertThat(taskPlanRepository.findById(otherOwnersSecondPlan.id!!).orElseThrow().isActive).isTrue()
        assertThat(taskRepository.findById(unfinishedTask.id!!)).isPresent

        generationService.generateDueTasks()
        assertThat(taskRepository.findAllByTaskPlanId(readyOwnedPlan.id!!)).isEmpty()
    }
}

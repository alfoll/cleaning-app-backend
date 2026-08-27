package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.taskplan.RecurrenceType
import com.cleaningapp.backend.taskplan.TaskPlanGenerationService
import com.cleaningapp.backend.taskplan.TaskPlanRepository
import com.cleaningapp.backend.tasktemplate.TaskTemplateRepository
import com.cleaningapp.backend.tasktemplate.TaskTemplateService
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate

class TaskPlanMembershipLifecycleIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var userHouseholdService: UserHouseholdService

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var generationService: TaskPlanGenerationService

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var taskTemplateRepository: TaskTemplateRepository

    @Autowired
    private lateinit var taskTemplateService: TaskTemplateService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `leave household should deactivate only leaving users plans keep tasks and block generation`() {
        val leavingUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = leavingUser)
        val leavingMembership = testDataFactory.createTestMembership(user = leavingUser, household = household)
        testDataFactory.createTestMembership(user = otherUser, household = household)
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = leavingUser)
        testDataFactory.createTestMembership(user = leavingUser, household = otherHousehold)
        val leavingPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = leavingUser,
            recurrenceType = RecurrenceType.WEEKLY,
        )
        val readyLeavingPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = leavingUser,
            nextDueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock)),
        )
        val otherUsersPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = otherUser,
        )
        val leavingUsersOtherHouseholdPlan = testDataFactory.createTestTaskPlan(
            household = otherHousehold,
            createdBy = leavingUser,
        )
        val leavingTemplate = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = leavingUser,
        )
        val otherUsersTemplate = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = otherUser,
        )
        val leavingUsersOtherHouseholdTemplate = testDataFactory.createTestTaskTemplate(
            household = otherHousehold,
            createdBy = leavingUser,
        )
        val unfinishedTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = leavingUser,
            dueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(1)),
            taskPlan = leavingPlan,
        )
        authenticateAs()

        userHouseholdService.leaveHousehold(household.id!!)

        entityManager.flush()
        entityManager.clear()
        val savedMembership = userHouseholdRepository.findById(leavingMembership.id!!).orElseThrow()
        val savedLeavingPlan = taskPlanRepository.findById(leavingPlan.id!!).orElseThrow()
        val savedReadyPlan = taskPlanRepository.findById(readyLeavingPlan.id!!).orElseThrow()
        val savedOtherUsersPlan = taskPlanRepository.findById(otherUsersPlan.id!!).orElseThrow()
        val savedOtherHouseholdPlan = taskPlanRepository.findById(leavingUsersOtherHouseholdPlan.id!!).orElseThrow()
        val savedLeavingTemplate = taskTemplateRepository.findById(leavingTemplate.id!!).orElseThrow()
        val savedOtherUsersTemplate = taskTemplateRepository.findById(otherUsersTemplate.id!!).orElseThrow()
        val savedOtherHouseholdTemplate = taskTemplateRepository
            .findById(leavingUsersOtherHouseholdTemplate.id!!)
            .orElseThrow()

        assertThat(savedMembership.isUserActive).isFalse()
        assertThat(savedLeavingPlan.isActive).isFalse()
        assertThat(savedReadyPlan.isActive).isFalse()
        assertThat(savedOtherUsersPlan.isActive).isTrue()
        assertThat(savedOtherHouseholdPlan.isActive).isTrue()
        assertThat(savedLeavingTemplate.isActive).isFalse()
        assertThat(savedOtherUsersTemplate.isActive).isTrue()
        assertThat(savedOtherHouseholdTemplate.isActive).isTrue()
        assertThat(taskRepository.findById(unfinishedTask.id!!)).isPresent

        generationService.generateDueTasks()
        assertThat(taskRepository.findAllByTaskPlanId(readyLeavingPlan.id!!)).isEmpty()

        authenticateAs(otherUser.firebaseUid)
        assertThat(taskTemplateService.getHouseholdTemplates(household.id!!).map { it.id })
            .containsExactly(otherUsersTemplate.id)
        val taskResponse = taskService.getTaskById(unfinishedTask.id!!)
        assertThat(taskResponse.taskPlanId).isEqualTo(leavingPlan.id)
        assertThat(taskResponse.recurrenceType).isEqualTo(RecurrenceType.WEEKLY)
        assertThat(taskResponse.recurrenceActive).isFalse()
        assertThat(activityRepository.findAll().map { it.activityType })
            .containsExactly(ActivityType.USER_LEFT)
    }

    @Test
    fun `remove member should deactivate only removed users plans in target household`() {
        val actor = createLocalUserForValidToken()
        val removedUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = actor)
        testDataFactory.createTestMembership(user = actor, household = household)
        val removedMembership = testDataFactory.createTestMembership(user = removedUser, household = household)
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = removedUser)
        testDataFactory.createTestMembership(user = removedUser, household = otherHousehold)
        val removedUsersPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = removedUser,
        )
        val actorsPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = actor,
        )
        val removedUsersOtherHouseholdPlan = testDataFactory.createTestTaskPlan(
            household = otherHousehold,
            createdBy = removedUser,
        )
        val removedUsersTemplate = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = removedUser,
        )
        val actorsTemplate = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = actor,
        )
        val removedUsersOtherHouseholdTemplate = testDataFactory.createTestTaskTemplate(
            household = otherHousehold,
            createdBy = removedUser,
        )
        authenticateAs()

        userHouseholdService.removeUserFromHousehold(household.id!!, removedUser.id!!)

        entityManager.flush()
        entityManager.clear()
        assertThat(userHouseholdRepository.findById(removedMembership.id!!).orElseThrow().isUserActive).isFalse()
        assertThat(taskPlanRepository.findById(removedUsersPlan.id!!).orElseThrow().isActive).isFalse()
        assertThat(taskPlanRepository.findById(actorsPlan.id!!).orElseThrow().isActive).isTrue()
        assertThat(taskPlanRepository.findById(removedUsersOtherHouseholdPlan.id!!).orElseThrow().isActive).isTrue()
        assertThat(taskTemplateRepository.findById(removedUsersTemplate.id!!).orElseThrow().isActive).isFalse()
        assertThat(taskTemplateRepository.findById(actorsTemplate.id!!).orElseThrow().isActive).isTrue()
        assertThat(
            taskTemplateRepository.findById(removedUsersOtherHouseholdTemplate.id!!).orElseThrow().isActive
        ).isTrue()
        assertThat(activityRepository.findAll().map { it.activityType })
            .containsExactly(ActivityType.USER_REMOVED)
    }

    @Test
    fun `rejoin should not reactivate template deactivated on leave`() {
        val returningUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(
            createdBy = returningUser,
            inviteCode = "TMPLREJN",
        )
        testDataFactory.createTestMembership(user = returningUser, household = household)
        testDataFactory.createTestMembership(user = otherUser, household = household)
        val oldTemplate = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = returningUser,
        )
        authenticateAs()

        userHouseholdService.leaveHousehold(household.id!!)
        userHouseholdService.joinHousehold("TMPLREJN")

        entityManager.flush()
        entityManager.clear()
        assertThat(userHouseholdRepository.findByUserIdAndHouseholdId(returningUser.id!!, household.id!!)?.isUserActive)
            .isTrue()
        assertThat(taskTemplateRepository.findById(oldTemplate.id!!).orElseThrow().isActive).isFalse()
        assertThat(taskTemplateService.getHouseholdTemplates(household.id!!)).isEmpty()
    }
}

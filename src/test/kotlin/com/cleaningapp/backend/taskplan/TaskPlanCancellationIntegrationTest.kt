package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.task.TaskDueDatePolicy
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDate

class TaskPlanCancellationIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskPlanService: TaskPlanService

    @Autowired
    private lateinit var taskPlanInstanceService: TaskPlanInstanceService

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `creator should cancel active plan while free task remains linked`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val plan = testDataFactory.createTestTaskPlan(household = household, createdBy = user)
        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = futureDueAt(1),
            taskPlan = plan,
        )
        authenticateAs()

        taskPlanService.cancelTaskPlan(plan.id!!)

        entityManager.flush()
        entityManager.clear()
        val savedPlan = taskPlanRepository.findById(plan.id!!).orElseThrow()
        val savedTask = taskRepository.findById(task.id!!).orElseThrow()
        val response = taskService.getTaskById(task.id!!)

        assertThat(savedPlan.isActive).isFalse()
        assertThat(savedTask.taskPlan?.id).isEqualTo(plan.id)
        assertThat(response.taskPlanId).isEqualTo(plan.id)
        assertThat(response.recurrenceType).isEqualTo(plan.recurrenceType)
        assertThat(response.recurrenceActive).isFalse()
    }

    @Test
    fun `household member should not cancel plan created by another user`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = currentUser, household = household)
        val plan = testDataFactory.createTestTaskPlan(household = household, createdBy = creator)
        authenticateAs()

        assertThatThrownBy {
            taskPlanService.cancelTaskPlan(plan.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)

        assertThat(taskPlanRepository.findById(plan.id!!).orElseThrow().isActive).isTrue()
    }

    @Test
    fun `user from another household should not cancel task plan`() {
        val currentUser = createLocalUserForValidToken()
        val currentHousehold = testDataFactory.createTestHousehold(createdBy = currentUser)
        testDataFactory.createTestMembership(user = currentUser, household = currentHousehold)
        val creator = testDataFactory.createTestUser()
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = otherHousehold)
        val plan = testDataFactory.createTestTaskPlan(household = otherHousehold, createdBy = creator)
        authenticateAs()

        assertThatThrownBy {
            taskPlanService.cancelTaskPlan(plan.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)

        assertThat(taskPlanRepository.findById(plan.id!!).orElseThrow().isActive).isTrue()
    }

    @Test
    fun `cancelling inactive plan should be rejected predictably`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = user,
            isActive = false,
        )
        authenticateAs()

        assertThatThrownBy {
            taskPlanService.cancelTaskPlan(plan.id!!)
        }
            .isInstanceOf(BusinessConflictException::class.java)
            .hasMessage("Task plan is already inactive")
    }

    @Test
    fun `assigned task should remain completable after plan cancellation`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 10,
        )
        val originalNextDueAt = futureDueAt(1)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = user,
            recurrenceType = RecurrenceType.DAILY,
            nextDueAt = originalNextDueAt,
        )
        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 25,
            dueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock).minusDays(2)),
            taskPlan = plan,
        )
        authenticateAs()

        taskPlanService.cancelTaskPlan(plan.id!!)
        val assignedTask = taskRepository.findById(task.id!!).orElseThrow()
        assertThat(assignedTask.assignedTo?.id).isEqualTo(membership.id)

        taskService.completeTask(task.id!!)

        entityManager.flush()
        entityManager.clear()
        val savedTask = taskRepository.findById(task.id!!).orElseThrow()
        val savedPlan = taskPlanRepository.findById(plan.id!!).orElseThrow()
        val savedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val transactions = transactionRepository.findAll().filter { it.task?.id == task.id }
        val activities = activityRepository.findAll().filter {
            it.activityType == ActivityType.TASK_COMPLETED
        }

        assertThat(savedTask.isCompleted).isTrue()
        assertThat(savedMembership.balance).isEqualTo(35)
        assertThat(transactions).hasSize(1)
        assertThat(transactions.single().type).isEqualTo(TransactionType.TASK_COMPLETION)
        assertThat(activities).hasSize(1)
        assertThat(savedPlan.isActive).isFalse()
        assertThat(savedPlan.nextDueAt).isEqualTo(originalNextDueAt)
    }

    @Test
    fun `deleting free recurring task should deactivate plan and block new instances`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val plan = testDataFactory.createTestTaskPlan(household = household, createdBy = user)
        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = futureDueAt(1),
            taskPlan = plan,
        )
        authenticateAs()

        taskService.deleteTask(task.id!!)

        entityManager.flush()
        entityManager.clear()
        assertThat(taskRepository.findById(task.id!!)).isEmpty
        assertThat(taskPlanRepository.findById(plan.id!!)).isPresent
        assertThat(taskPlanRepository.findById(plan.id!!).orElseThrow().isActive).isFalse()
        assertThatThrownBy {
            taskPlanInstanceService.createTaskInstance(plan.id!!, futureDueAt(2))
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `assigned recurring task delete rejection should not deactivate plan`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)
        val plan = testDataFactory.createTestTaskPlan(household = household, createdBy = user)
        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            dueAt = futureDueAt(1),
            taskPlan = plan,
        )
        authenticateAs()

        assertThatThrownBy {
            taskService.deleteTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)

        assertThat(taskRepository.findById(task.id!!)).isPresent
        assertThat(taskPlanRepository.findById(plan.id!!).orElseThrow().isActive).isTrue()
    }

    @Test
    fun `ordinary free task delete should not involve task plan logic`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val task = testDataFactory.createTestFreeTask(household = household, createdBy = user)
        authenticateAs()

        taskService.deleteTask(task.id!!)

        entityManager.flush()
        assertThat(taskRepository.findById(task.id!!)).isEmpty
        assertThat(taskPlanRepository.findAll()).isEmpty()
    }

    @Test
    fun `historical completed task should remain linked and readable after cancellation`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)
        val plan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = user,
            recurrenceType = RecurrenceType.MONTHLY,
            monthlyAnchorDay = 27,
        )
        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            dueAt = futureDueAt(1),
            taskPlan = plan,
        )
        authenticateAs()

        taskPlanService.cancelTaskPlan(plan.id!!)

        val response = taskService.getTaskById(task.id!!)
        assertThat(taskRepository.findById(task.id!!)).isPresent
        assertThat(response.isCompleted).isTrue()
        assertThat(response.taskPlanId).isEqualTo(plan.id)
        assertThat(response.recurrenceType).isEqualTo(RecurrenceType.MONTHLY)
        assertThat(response.recurrenceActive).isFalse()
    }

    private fun futureDueAt(days: Long) =
        TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(days))
}

package com.cleaningapp.backend.task

import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.TaskNotFoundException
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TaskConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Test
    fun `two users cannot assign same task concurrently`() {
        val user1 = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val user2 = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = user1)

        val membership1 = testDataFactory.createTestMembership(
            user = user1,
            household = household,
        )

        val membership2 = testDataFactory.createTestMembership(
            user = user2,
            household = household,
        )

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user1,
            reward = 50,
        )

        val results = runConcurrently(threadCount = 2) { index ->
            val firebaseUid = if (index == 0) user1.firebaseUid else user2.firebaseUid

            authenticatedAs(firebaseUid) {
                taskService.assignTask(task.id!!)
            }
        }

        val savedTask = taskRepository.findById(task.id!!).orElseThrow()

        Assertions.assertThat(successCount(results)).isEqualTo(1)
        Assertions.assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, BusinessConflictException::class.java)

        Assertions.assertThat(savedTask.isCompleted).isFalse()
        Assertions.assertThat(savedTask.assignedTo?.id).isIn(membership1.id, membership2.id)
        Assertions.assertThat(savedTask.completedBy).isNull()
        Assertions.assertThat(savedTask.completedAt).isNull()
    }

    @Test
    fun `same assigned task cannot be completed twice concurrently`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val household = testDataFactory.createTestHousehold(createdBy = user)

        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 0,
        )

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 50,
        )

        val results = runConcurrently(threadCount = 2) {
            authenticatedAs(user.firebaseUid) {
                taskService.completeTask(task.id!!)
            }
        }

        val savedTask = taskRepository.findById(task.id!!).orElseThrow()
        val savedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        val taskTransactions = transactionRepository.findAll()
            .filter {
                it.task?.id == task.id &&
                        it.type == TransactionType.TASK_COMPLETION
            }

        Assertions.assertThat(successCount(results)).isEqualTo(1)
        Assertions.assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, BusinessConflictException::class.java)

        Assertions.assertThat(savedTask.isCompleted).isTrue()
        Assertions.assertThat(savedTask.completedBy?.id).isEqualTo(membership.id)
        Assertions.assertThat(savedTask.assignedTo).isNull()
        Assertions.assertThat(savedTask.assignedAt).isNull()

        Assertions.assertThat(savedMembership.balance).isEqualTo(50)
        Assertions.assertThat(taskTransactions).hasSize(1)
        Assertions.assertThat(taskTransactions.single().amount).isEqualTo(50)
    }

    @Test
    fun `completeTask and unassignTask cannot both succeed`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val household = testDataFactory.createTestHousehold(createdBy = user)

        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 0,
        )

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 50,
        )

        val results = runConcurrently(threadCount = 2) { index ->
            authenticatedAs(user.firebaseUid) {
                if (index == 0) {
                    taskService.completeTask(task.id!!)
                } else {
                    taskService.unassignTask(task.id!!)
                }
            }
        }

        val savedTask = taskRepository.findById(task.id!!).orElseThrow()
        val savedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        val taskTransactions = transactionRepository.findAll()
            .filter {
                it.task?.id == task.id &&
                        it.type == TransactionType.TASK_COMPLETION
            }

        Assertions.assertThat(successCount(results)).isEqualTo(1)
        Assertions.assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, BusinessConflictException::class.java)

        if (savedTask.isCompleted) {
            Assertions.assertThat(savedTask.completedBy?.id).isEqualTo(membership.id)
            Assertions.assertThat(savedTask.assignedTo).isNull()
            Assertions.assertThat(savedMembership.balance).isEqualTo(50)
            Assertions.assertThat(taskTransactions).hasSize(1)
        } else {
            Assertions.assertThat(savedTask.assignedTo).isNull()
            Assertions.assertThat(savedTask.completedBy).isNull()
            Assertions.assertThat(savedTask.completedAt).isNull()
            Assertions.assertThat(savedMembership.balance).isEqualTo(0)
            Assertions.assertThat(taskTransactions).isEmpty()
        }
    }

    @Test
    fun `assignTask and deleteTask cannot both succeed on same free task`() {
        val creator = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val assignee = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = creator)

        testDataFactory.createTestMembership(
            user = creator,
            household = household,
        )

        val assigneeMembership = testDataFactory.createTestMembership(
            user = assignee,
            household = household,
        )

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = creator,
            reward = 50,
        )

        val results = runConcurrently(threadCount = 2) { index ->
            if (index == 0) {
                authenticatedAs(creator.firebaseUid) {
                    taskService.deleteTask(task.id!!)
                }
            } else {
                authenticatedAs(assignee.firebaseUid) {
                    taskService.assignTask(task.id!!)
                }
            }
        }

        val savedTask = taskRepository.findById(task.id!!)

        Assertions.assertThat(successCount(results)).isEqualTo(1)
        Assertions.assertThat(failureCount(results)).isEqualTo(1)

        if (savedTask.isPresent) {
            assertSingleFailureOfType(results, BusinessConflictException::class.java)

            Assertions.assertThat(savedTask.orElseThrow().assignedTo?.id).isEqualTo(assigneeMembership.id)
            Assertions.assertThat(savedTask.orElseThrow().isCompleted).isFalse()
        } else {
            assertSingleFailureOfType(results, TaskNotFoundException::class.java)
        }
    }
}

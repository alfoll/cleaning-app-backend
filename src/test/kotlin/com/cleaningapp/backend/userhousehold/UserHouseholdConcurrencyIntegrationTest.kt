package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class UserHouseholdConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var userHouseholdService: UserHouseholdService

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var householdRepository: HouseholdRepository

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Test
    fun `joinHousehold cannot exceed six active members under concurrency`() {
        val owner = testDataFactory.createTestUser(firebaseUid = "firebase-owner")
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        val candidates = (1..10).map { index ->
            testDataFactory.createTestUser(firebaseUid = "firebase-candidate-$index")
        }

        val results = runConcurrently(threadCount = candidates.size) { index ->
            val user = candidates[index]

            authenticatedAs(user.firebaseUid) {
                userHouseholdService.joinHousehold(household.inviteCode)
            }
        }

        val activeMembers = userHouseholdRepository
            .findAllByHouseholdIdAndIsUserActiveTrue(household.id!!)

        assertThat(activeMembers).hasSize(6)

        // 1 owner already exists, so only 5 more users can join.
        assertThat(successCount(results)).isEqualTo(5)
        assertThat(failureCount(results)).isEqualTo(candidates.size - 5)
        assertAllFailuresOfType(results, BusinessConflictException::class.java)
    }

    @Test
    fun `same user cannot join same household twice concurrently`() {
        val owner = testDataFactory.createTestUser(firebaseUid = "firebase-owner")
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        val results = runConcurrently(threadCount = 2) {
            authenticatedAs(user.firebaseUid) {
                userHouseholdService.joinHousehold(household.inviteCode)
            }
        }

        val memberships = userHouseholdRepository
            .findAllByUserIdAndIsUserActiveTrue(user.id!!)
            .filter { it.household.id == household.id }

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, BusinessConflictException::class.java)
        assertThat(memberships).hasSize(1)
    }

    @Test
    fun `same inactive membership is reactivated only once under concurrency`() {
        val owner = testDataFactory.createTestUser(firebaseUid = "firebase-owner")
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        val inactiveMembership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 70,
            isUserActive = false,
        )

        val results = runConcurrently(threadCount = 2) {
            authenticatedAs(user.firebaseUid) {
                userHouseholdService.joinHousehold(household.inviteCode)
            }
        }

        val savedMembership = userHouseholdRepository.findById(inactiveMembership.id!!).orElseThrow()
        val activeMemberships = userHouseholdRepository
            .findAllByUserIdAndIsUserActiveTrue(user.id!!)
            .filter { it.household.id == household.id }

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, BusinessConflictException::class.java)
        assertThat(activeMemberships).hasSize(1)
        assertThat(savedMembership.isUserActive).isTrue()
        assertThat(savedMembership.balance).isEqualTo(0)
    }

    @Test
    fun `two parallel leaveHousehold calls from same user leave membership inactive once`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val otherUser = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = user)

        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        val results = runConcurrently(threadCount = 2) {
            authenticatedAs(user.firebaseUid) {
                userHouseholdService.leaveHousehold(household.id!!)
            }
        }

        val savedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, MembershipNotActiveException::class.java)

        assertThat(savedMembership.isUserActive).isFalse()
        assertThat(savedMembership.balance).isEqualTo(0)
    }

    @Test
    fun `completeTask and leaveHousehold keep task state and balance consistent`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val otherUser = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = user)

        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 0,
        )

        testDataFactory.createTestMembership(
            user = otherUser,
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
            if (index == 0) {
                authenticatedAs(user.firebaseUid) {
                    taskService.completeTask(task.id!!)
                }
            } else {
                authenticatedAs(user.firebaseUid) {
                    userHouseholdService.leaveHousehold(household.id!!)
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

        val resetTransactions = transactionRepository.findAll()
            .filter {
                it.household.id == household.id &&
                        it.member.id == membership.id &&
                        it.type == TransactionType.BALANCE_RESET
            }

        assertThat(savedMembership.isUserActive).isFalse()
        assertThat(savedMembership.balance).isEqualTo(0)

        if (failureCount(results) == 1) {
            assertThat(successCount(results)).isEqualTo(1)
            assertSingleFailureOfType(results, MembershipNotActiveException::class.java)

            assertThat(savedTask.isCompleted).isFalse()
            assertThat(savedTask.assignedTo).isNull()
            assertThat(savedTask.completedBy).isNull()
            assertThat(savedTask.completedAt).isNull()
            assertThat(taskTransactions).isEmpty()
            assertThat(resetTransactions).isEmpty()
        } else {
            assertThat(successCount(results)).isEqualTo(2)
            assertThat(failureCount(results)).isEqualTo(0)

            assertThat(savedTask.isCompleted).isTrue()
            assertThat(savedTask.completedBy?.id).isEqualTo(membership.id)
            assertThat(savedTask.assignedTo).isNull()
            assertThat(savedTask.assignedAt).isNull()
            assertThat(taskTransactions).hasSize(1)
            assertThat(taskTransactions.single().amount).isEqualTo(50)
            assertThat(resetTransactions).hasSize(1)
            assertThat(resetTransactions.single().amount).isEqualTo(-50)
        }
    }

    @Test
    fun `leaveHousehold and removeUserFromHousehold cannot both deactivate same membership twice`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val remover = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = remover)

        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 0,
        )

        testDataFactory.createTestMembership(
            user = remover,
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
            if (index == 0) {
                authenticatedAs(user.firebaseUid) {
                    userHouseholdService.leaveHousehold(household.id!!)
                }
            } else {
                authenticatedAs(remover.firebaseUid) {
                    userHouseholdService.removeUserFromHousehold(household.id!!, user.id!!)
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

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(
            results,
            MembershipNotActiveException::class.java,
            BusinessConflictException::class.java,
        )

        assertThat(savedTask.assignedTo).isNull()
        assertThat(savedTask.completedBy).isNull()
        assertThat(savedTask.completedAt).isNull()
        assertThat(savedMembership.isUserActive).isFalse()
        assertThat(savedMembership.balance).isEqualTo(0)
        assertThat(taskTransactions).isEmpty()
    }
}

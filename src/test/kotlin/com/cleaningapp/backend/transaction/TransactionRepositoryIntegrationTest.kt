package com.cleaningapp.backend.transaction

import com.cleaningapp.backend.base.BaseIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class TransactionRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `exists queries should detect task and privilege transactions only by linked entity id`() {
        val user = createLocalUserForValidToken()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val taskWithTransaction = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            reward = 20,
        )
        val taskWithoutTransaction = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            reward = 30,
        )

        val privilegeWithTransaction = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = membership,
        )
        val privilegeWithoutTransaction = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 60,
            isAvailable = false,
            boughtBy = membership,
        )

        testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = membership,
            task = taskWithTransaction,
            amount = 20,
        )
        testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = membership,
            privilege = privilegeWithTransaction,
            amount = -50,
        )

        assertThat(transactionRepository.existsByTaskId(taskWithTransaction.id!!)).isTrue()
        assertThat(transactionRepository.existsByTaskId(taskWithoutTransaction.id!!)).isFalse()

        assertThat(transactionRepository.existsByPrivilegeId(privilegeWithTransaction.id!!)).isTrue()
        assertThat(transactionRepository.existsByPrivilegeId(privilegeWithoutTransaction.id!!)).isFalse()
    }

    @Test
    fun `findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc should return only member transactions sorted desc`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        val currentMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        val otherMembership = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        val otherHousehold = testDataFactory.createTestHousehold(createdBy = currentUser)
        val otherHouseholdMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = otherHousehold,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val olderTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = currentUser,
            completedBy = currentMembership,
            reward = 20,
        )
        val olderTransaction = testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = currentMembership,
            task = olderTask,
            amount = 20,
            createdAt = baseTime.minusDays(3),
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = currentUser,
            cost = 50,
            isAvailable = false,
            boughtBy = currentMembership,
        )
        val newerTransaction = testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = currentMembership,
            privilege = privilege,
            amount = -50,
            createdAt = baseTime.minusDays(1),
        )

        val newestTransaction = testDataFactory.createTestBalanceResetTransaction(
            household = household,
            member = currentMembership,
            amount = -10,
            createdAt = baseTime,
        )

        val otherMemberTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = otherUser,
            completedBy = otherMembership,
            reward = 40,
        )
        val otherMemberTransaction = testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = otherMembership,
            task = otherMemberTask,
            amount = 40,
            createdAt = baseTime.plusDays(1),
        )

        val otherHouseholdTask = testDataFactory.createTestCompletedTask(
            household = otherHousehold,
            createdBy = currentUser,
            completedBy = otherHouseholdMembership,
            reward = 30,
        )
        val otherHouseholdTransaction = testDataFactory.createTestTaskCompletionTransaction(
            household = otherHousehold,
            member = otherHouseholdMembership,
            task = otherHouseholdTask,
            amount = 30,
            createdAt = baseTime.plusDays(2),
        )

        val result =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                currentMembership.id!!,
            )

        assertThat(result.map { it.id })
            .containsExactly(
                newestTransaction.id,
                newerTransaction.id,
                olderTransaction.id,
            )
            .doesNotContain(
                otherMemberTransaction.id,
                otherHouseholdTransaction.id,
            )
    }

    @Test
    fun `deleteAllByHouseholdId should delete only transactions of selected household`() {
        val user = createLocalUserForValidToken()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        val otherHouseholdMembership = testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            reward = 20,
        )
        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = membership,
        )
        val otherHouseholdTask = testDataFactory.createTestCompletedTask(
            household = otherHousehold,
            createdBy = user,
            completedBy = otherHouseholdMembership,
            reward = 30,
        )

        val transactionOne = testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = membership,
            task = task,
            amount = 20,
        )
        val transactionTwo = testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = membership,
            privilege = privilege,
            amount = -50,
        )
        val otherHouseholdTransaction = testDataFactory.createTestTaskCompletionTransaction(
            household = otherHousehold,
            member = otherHouseholdMembership,
            task = otherHouseholdTask,
            amount = 30,
        )

        val deletedCount = transactionRepository.deleteAllByHouseholdId(household.id!!)

        entityManager.flush()
        entityManager.clear()

        assertThat(deletedCount).isEqualTo(2)
        assertThat(transactionRepository.findById(transactionOne.id!!)).isEmpty()
        assertThat(transactionRepository.findById(transactionTwo.id!!)).isEmpty()
        assertThat(transactionRepository.findById(otherHouseholdTransaction.id!!)).isPresent
    }
}
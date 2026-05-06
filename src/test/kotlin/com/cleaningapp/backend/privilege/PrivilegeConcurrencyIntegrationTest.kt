package com.cleaningapp.backend.privilege

import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.PrivilegeNotFoundException
import com.cleaningapp.backend.transaction.BalanceResetTransactionCommand
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionService
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PrivilegeConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var privilegeService: PrivilegeService

    @Autowired
    private lateinit var privilegeRepository: PrivilegeRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var transactionService: TransactionService

    @Test
    fun `two users cannot buy same privilege concurrently`() {
        val user1 = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val user2 = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = user1)

        val membership1 = testDataFactory.createTestMembership(
            user = user1,
            household = household,
            balance = 100,
        )

        val membership2 = testDataFactory.createTestMembership(
            user = user2,
            household = household,
            balance = 100,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user1,
            cost = 50,
            isAvailable = true,
            boughtBy = null,
        )

        val results = runConcurrently(threadCount = 2) { index ->
            val firebaseUid = if (index == 0) user1.firebaseUid else user2.firebaseUid

            authenticatedAs(firebaseUid) {
                privilegeService.buyPrivilege(privilege.id!!)
            }
        }

        val savedPrivilege = privilegeRepository.findById(privilege.id!!).orElseThrow()
        val savedMembership1 = userHouseholdRepository.findById(membership1.id!!).orElseThrow()
        val savedMembership2 = userHouseholdRepository.findById(membership2.id!!).orElseThrow()

        val purchaseTransactions = transactionRepository.findAll()
            .filter {
                it.privilege?.id == privilege.id &&
                        it.type == TransactionType.PRIVILEGE_BOUGHT
            }

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, BusinessConflictException::class.java)

        assertThat(savedPrivilege.isAvailable).isFalse()
        assertThat(savedPrivilege.boughtBy?.id).isIn(membership1.id, membership2.id)

        assertThat(purchaseTransactions).hasSize(1)
        assertThat(purchaseTransactions.single().amount).isEqualTo(-50)

        val balances = listOf(savedMembership1.balance, savedMembership2.balance)

        assertThat(balances).containsExactlyInAnyOrder(50, 100)
    }

    @Test
    fun `buyPrivilege and deletePrivilege cannot both succeed`() {
        val creator = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val buyer = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = creator)

        testDataFactory.createTestMembership(
            user = creator,
            household = household,
            balance = 0,
        )

        val buyerMembership = testDataFactory.createTestMembership(
            user = buyer,
            household = household,
            balance = 100,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = creator,
            cost = 50,
            isAvailable = true,
            boughtBy = null,
        )

        val results = runConcurrently(threadCount = 2) { index ->
            if (index == 0) {
                authenticatedAs(creator.firebaseUid) {
                    privilegeService.deletePrivilege(privilege.id!!)
                }
            } else {
                authenticatedAs(buyer.firebaseUid) {
                    privilegeService.buyPrivilege(privilege.id!!)
                }
            }
        }

        val savedPrivilege = privilegeRepository.findById(privilege.id!!)
        val savedBuyerMembership = userHouseholdRepository.findById(buyerMembership.id!!).orElseThrow()
        val purchaseTransactions = transactionRepository.findAll()
            .filter {
                it.privilege?.id == privilege.id &&
                        it.type == TransactionType.PRIVILEGE_BOUGHT
            }

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)

        if (savedPrivilege.isPresent) {
            assertSingleFailureOfType(results, BusinessConflictException::class.java)

            assertThat(savedPrivilege.orElseThrow().isAvailable).isFalse()
            assertThat(savedPrivilege.orElseThrow().boughtBy?.id).isEqualTo(buyerMembership.id)
            assertThat(savedBuyerMembership.balance).isEqualTo(50)
            assertThat(purchaseTransactions).hasSize(1)
        } else {
            assertSingleFailureOfType(results, PrivilegeNotFoundException::class.java)

            assertThat(savedBuyerMembership.balance).isEqualTo(100)
            assertThat(purchaseTransactions).isEmpty()
        }
    }

    @Test
    fun `buyPrivilege and resetBalance keep final balance and transactions consistent`() {
        val creator = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val buyer = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = creator)

        testDataFactory.createTestMembership(
            user = creator,
            household = household,
            balance = 0,
        )

        val buyerMembership = testDataFactory.createTestMembership(
            user = buyer,
            household = household,
            balance = 100,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = creator,
            cost = 50,
            isAvailable = true,
            boughtBy = null,
        )

        val results = runConcurrently(threadCount = 2) { index ->
            if (index == 0) {
                authenticatedAs(buyer.firebaseUid) {
                    privilegeService.buyPrivilege(privilege.id!!)
                }
            } else {
                transactionService.resetBalance(
                    BalanceResetTransactionCommand(
                        householdId = household.id!!,
                        memberId = buyerMembership.id!!,
                    )
                )
            }
        }

        val savedPrivilege = privilegeRepository.findById(privilege.id!!).orElseThrow()
        val savedBuyerMembership = userHouseholdRepository.findById(buyerMembership.id!!).orElseThrow()

        val purchaseTransactions = transactionRepository.findAll()
            .filter {
                it.privilege?.id == privilege.id &&
                        it.type == TransactionType.PRIVILEGE_BOUGHT
            }

        val resetTransactions = transactionRepository.findAll()
            .filter {
                it.household.id == household.id &&
                        it.member.id == buyerMembership.id &&
                        it.type == TransactionType.BALANCE_RESET
            }

        assertThat(savedBuyerMembership.balance).isEqualTo(0)
        assertThat(savedBuyerMembership.balance).isGreaterThanOrEqualTo(0)

        if (failureCount(results) == 1) {
            assertThat(successCount(results)).isEqualTo(1)
            assertSingleFailureOfType(results, BusinessConflictException::class.java)

            assertThat(savedPrivilege.isAvailable).isTrue()
            assertThat(savedPrivilege.boughtBy).isNull()
            assertThat(purchaseTransactions).isEmpty()
            assertThat(resetTransactions).hasSize(1)
            assertThat(resetTransactions.single().amount).isEqualTo(-100)
        } else {
            assertThat(successCount(results)).isEqualTo(2)
            assertThat(failureCount(results)).isEqualTo(0)

            assertThat(savedPrivilege.isAvailable).isFalse()
            assertThat(savedPrivilege.boughtBy?.id).isEqualTo(buyerMembership.id)
            assertThat(purchaseTransactions).hasSize(1)
            assertThat(purchaseTransactions.single().amount).isEqualTo(-50)
            assertThat(resetTransactions).hasSize(1)
            assertThat(resetTransactions.single().amount).isEqualTo(-50)
        }
    }
}

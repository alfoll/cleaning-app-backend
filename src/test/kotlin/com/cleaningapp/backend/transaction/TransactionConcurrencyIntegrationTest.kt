package com.cleaningapp.backend.transaction

import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TransactionConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var transactionService: TransactionService

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Test
    fun `parallel resetBalance calls are idempotent and create one reset transaction`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val household = testDataFactory.createTestHousehold(createdBy = user)

        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val results = runConcurrently(threadCount = 5) {
            transactionService.resetBalance(
                BalanceResetTransactionCommand(
                    householdId = household.id!!,
                    memberId = membership.id!!,
                )
            )
        }

        val savedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        val resetTransactions = transactionRepository.findAll()
            .filter {
                it.household.id == household.id &&
                        it.member.id == membership.id &&
                        it.type == TransactionType.BALANCE_RESET
            }

        assertThat(successCount(results)).isEqualTo(5)
        assertThat(failureCount(results)).isEqualTo(0)

        assertThat(savedMembership.balance).isEqualTo(0)
        assertThat(savedMembership.balance).isGreaterThanOrEqualTo(0)
        assertThat(resetTransactions).hasSize(1)
        assertThat(resetTransactions.single().amount).isEqualTo(-100)
    }
}

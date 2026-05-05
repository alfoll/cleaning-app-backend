package com.cleaningapp.backend.leaderboard

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDateTime

class LeaderboardRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var leaderboardRepository: LeaderboardRepository

    @Autowired
    private lateinit var clock: Clock

    private fun createTaskCompletionTransaction(
        household: HouseholdEntity,
        member: UserHouseholdEntity,
        amount: Int,
        createdAt: LocalDateTime,
    ) {
        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = member.user,
            completedBy = member,
            reward = amount,
        )

        testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = member,
            task = task,
            amount = amount,
            createdAt = createdAt,
        )
    }

    @Test
    fun `findHouseholdLeaderboard should return active members with zero stats using coalesce and exclude inactive records`() {
        val currentUser = createLocalUserForValidToken(name = "Current User")
        val zeroUser = testDataFactory.createTestUser(name = "Zero User")
        val inactiveMembershipUser = testDataFactory.createTestUser(name = "Inactive Membership User")
        val inactiveUser = testDataFactory.createTestUser(
            name = "Inactive User",
            isActive = false,
        )

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        testDataFactory.createTestMembership(
            user = zeroUser,
            household = household,
        )
        val inactiveMembership = testDataFactory.createTestMembership(
            user = inactiveMembershipUser,
            household = household,
            isUserActive = false,
        )
        val inactiveUserMembership = testDataFactory.createTestMembership(
            user = inactiveUser,
            household = household,
            isUserActive = true,
        )

        val baseTime = LocalDateTime.now(clock)

        createTaskCompletionTransaction(
            household = household,
            member = inactiveMembership,
            amount = 100,
            createdAt = baseTime.minusDays(1),
        )
        createTaskCompletionTransaction(
            household = household,
            member = inactiveUserMembership,
            amount = 100,
            createdAt = baseTime.minusDays(1),
        )

        val rows = leaderboardRepository.findHouseholdLeaderboard(
            householdId = household.id!!,
            earningType = TransactionType.TASK_COMPLETION,
            periodStart = baseTime.minusDays(7),
        )

        assertThat(rows.map { it.userId })
            .containsExactlyInAnyOrder(
                currentUser.id,
                zeroUser.id,
            )
            .doesNotContain(
                inactiveMembershipUser.id,
                inactiveUser.id,
            )

        rows.forEach { row ->
            assertThat(row.earnedCoins).isZero()
            assertThat(row.earnedCoinsDelta).isZero()
            assertThat(row.completedTaskCount).isZero()
            assertThat(row.completedTasksDelta).isZero()
        }
    }

    @Test
    fun `findHouseholdLeaderboard should aggregate task completion stats and ignore other transaction types`() {
        val user = createLocalUserForValidToken(name = "Current User")

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)
        val periodStart = baseTime.minusDays(7)

        createTaskCompletionTransaction(
            household = household,
            member = membership,
            amount = 100,
            createdAt = baseTime.minusDays(8),
        )
        createTaskCompletionTransaction(
            household = household,
            member = membership,
            amount = 50,
            createdAt = baseTime.minusDays(1),
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 500,
            isAvailable = false,
            boughtBy = membership,
        )

        testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = membership,
            privilege = privilege,
            amount = -500,
            createdAt = baseTime.minusDays(1),
        )

        testDataFactory.createTestBalanceResetTransaction(
            household = household,
            member = membership,
            amount = -150,
            createdAt = baseTime.minusDays(1),
        )

        val rows = leaderboardRepository.findHouseholdLeaderboard(
            householdId = household.id!!,
            earningType = TransactionType.TASK_COMPLETION,
            periodStart = periodStart,
        )

        assertThat(rows).hasSize(1)

        val row = rows.first()

        assertThat(row.userId).isEqualTo(user.id)
        assertThat(row.name).isEqualTo("Current User")
        assertThat(row.avatarUrl).isNull()

        assertThat(row.earnedCoins).isEqualTo(150L)
        assertThat(row.earnedCoinsDelta).isEqualTo(50L)
        assertThat(row.completedTaskCount).isEqualTo(2L)
        assertThat(row.completedTasksDelta).isEqualTo(1L)
    }

    @Test
    fun `findHouseholdLeaderboard should isolate stats by household`() {
        val user = createLocalUserForValidToken(name = "Current User")

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

        val baseTime = LocalDateTime.now(clock)

        createTaskCompletionTransaction(
            household = household,
            member = membership,
            amount = 40,
            createdAt = baseTime.minusDays(1),
        )

        createTaskCompletionTransaction(
            household = otherHousehold,
            member = otherHouseholdMembership,
            amount = 100,
            createdAt = baseTime.minusDays(1),
        )

        val rows = leaderboardRepository.findHouseholdLeaderboard(
            householdId = household.id!!,
            earningType = TransactionType.TASK_COMPLETION,
            periodStart = baseTime.minusDays(7),
        )

        assertThat(rows).hasSize(1)
        assertThat(rows.first().userId).isEqualTo(user.id)
        assertThat(rows.first().earnedCoins).isEqualTo(40L)
        assertThat(rows.first().earnedCoinsDelta).isEqualTo(40L)
        assertThat(rows.first().completedTaskCount).isEqualTo(1L)
        assertThat(rows.first().completedTasksDelta).isEqualTo(1L)
    }
}
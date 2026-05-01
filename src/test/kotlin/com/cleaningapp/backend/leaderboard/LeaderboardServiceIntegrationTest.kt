package com.cleaningapp.backend.leaderboard

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

class LeaderboardServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var leaderboardService: LeaderboardService

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

    private fun createPrivilegePurchaseTransaction(
        household: HouseholdEntity,
        member: UserHouseholdEntity,
        amount: Int,
        createdAt: LocalDateTime,
    ) {
        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = member.user,
            cost = -amount,
            isAvailable = false,
            boughtBy = member,
        )

        testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = member,
            privilege = privilege,
            amount = amount,
            createdAt = createdAt,
        )
    }

    private fun LeaderboardResponseDTO.itemFor(user: UserEntity): LeaderboardItemResponseDTO =
        items.first { it.userId == user.id }

    @Test
    fun `getLeaderboard should include active zero members exclude inactive memberships and inactive users`() {
        val currentUser = createLocalUserForValidToken(name = "Current User")
        val earnerUser = testDataFactory.createTestUser(name = "Earner User")
        val zeroUser = testDataFactory.createTestUser(name = "Zero User")
        val inactiveMembershipUser = testDataFactory.createTestUser(name = "Inactive Membership User")
        val inactiveUser = testDataFactory.createTestUser(
            name = "Inactive User",
            isActive = false,
        )

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        val currentMember = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        val earnerMember = testDataFactory.createTestMembership(
            user = earnerUser,
            household = household,
        )
        val zeroMember = testDataFactory.createTestMembership(
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
            member = earnerMember,
            amount = 30,
            createdAt = baseTime.minusDays(1),
        )
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

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)

        assertThat(result.periodDays).isEqualTo(7)
        assertThat(result.items.map { it.userId })
            .containsExactlyInAnyOrder(
                currentUser.id,
                earnerUser.id,
                zeroUser.id,
            )
            .doesNotContain(
                inactiveMembershipUser.id,
                inactiveUser.id,
            )

        assertThat(result.itemFor(currentUser).earnedCoins).isZero()
        assertThat(result.itemFor(zeroUser).earnedCoins).isZero()
        assertThat(result.itemFor(earnerUser).earnedCoins).isEqualTo(30L)

        assertThat(result.items.map { it.place })
            .containsExactly(1, 2, 3)

        assertThat(result.items.filter { it.isCurrentUser })
            .hasSize(1)
        assertThat(result.itemFor(currentUser).isCurrentUser).isTrue()
        assertThat(result.itemFor(earnerUser).isCurrentUser).isFalse()
        assertThat(result.itemFor(zeroUser).isCurrentUser).isFalse()

        assertThat(currentMember.id).isNotNull()
        assertThat(zeroMember.id).isNotNull()
    }

    @Test
    fun `getLeaderboard should sort by total earned coins and ignore privilege purchase and balance reset transactions`() {
        val currentUser = createLocalUserForValidToken(name = "Current User")
        val highUser = testDataFactory.createTestUser(name = "High Earner")
        val zeroUser = testDataFactory.createTestUser(name = "Zero Earner")

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        val currentMember = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        val highMember = testDataFactory.createTestMembership(
            user = highUser,
            household = household,
        )
        testDataFactory.createTestMembership(
            user = zeroUser,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)

        createTaskCompletionTransaction(
            household = household,
            member = currentMember,
            amount = 40,
            createdAt = baseTime.minusDays(1),
        )
        createPrivilegePurchaseTransaction(
            household = household,
            member = currentMember,
            amount = -500,
            createdAt = baseTime.minusDays(1),
        )
        testDataFactory.createTestBalanceResetTransaction(
            household = household,
            member = currentMember,
            amount = -300,
            createdAt = baseTime.minusDays(1),
        )

        createTaskCompletionTransaction(
            household = household,
            member = highMember,
            amount = 60,
            createdAt = baseTime.minusDays(1),
        )

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)

        assertThat(result.items.map { it.userId })
            .containsExactly(
                highUser.id,
                currentUser.id,
                zeroUser.id,
            )

        val currentItem = result.itemFor(currentUser)

        assertThat(currentItem.earnedCoins).isEqualTo(40L)
        assertThat(currentItem.earnedCoinsDelta).isEqualTo(40L)
        assertThat(currentItem.completedTasksCount).isEqualTo(1L)
        assertThat(currentItem.completedTasksDelta).isEqualTo(1L)

        assertThat(result.itemFor(highUser).earnedCoins).isEqualTo(60L)
        assertThat(result.itemFor(zeroUser).earnedCoins).isZero()
    }

    @Test
    fun `getLeaderboard should count old transactions in total but not in seven day delta`() {
        val currentUser = createLocalUserForValidToken(name = "Old Total User")
        val recentUser = testDataFactory.createTestUser(name = "Recent User")

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        val currentMember = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        val recentMember = testDataFactory.createTestMembership(
            user = recentUser,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)

        createTaskCompletionTransaction(
            household = household,
            member = currentMember,
            amount = 100,
            createdAt = baseTime.minusDays(8),
        )
        createTaskCompletionTransaction(
            household = household,
            member = recentMember,
            amount = 50,
            createdAt = baseTime.minusDays(6),
        )

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)

        val oldTotalItem = result.itemFor(currentUser)
        val recentItem = result.itemFor(recentUser)

        assertThat(result.items.map { it.userId })
            .containsExactly(currentUser.id, recentUser.id)

        assertThat(oldTotalItem.earnedCoins).isEqualTo(100L)
        assertThat(oldTotalItem.earnedCoinsDelta).isZero()
        assertThat(oldTotalItem.completedTasksCount).isEqualTo(1L)
        assertThat(oldTotalItem.completedTasksDelta).isZero()

        assertThat(recentItem.earnedCoins).isEqualTo(50L)
        assertThat(recentItem.earnedCoinsDelta).isEqualTo(50L)
        assertThat(recentItem.completedTasksCount).isEqualTo(1L)
        assertThat(recentItem.completedTasksDelta).isEqualTo(1L)
    }

    @Test
    fun `getLeaderboard should include transaction at exact seven day boundary into delta`() {
        val currentUser = createLocalUserForValidToken(name = "Boundary User")
        val otherUser = testDataFactory.createTestUser(name = "Other User")

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        val currentMember = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)
        val periodStart = baseTime.minusDays(7)

        createTaskCompletionTransaction(
            household = household,
            member = currentMember,
            amount = 40,
            createdAt = periodStart,
        )
        createTaskCompletionTransaction(
            household = household,
            member = currentMember,
            amount = 30,
            createdAt = periodStart.minusSeconds(1),
        )

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)
        val currentItem = result.itemFor(currentUser)

        assertThat(currentItem.earnedCoins).isEqualTo(70L)
        assertThat(currentItem.earnedCoinsDelta).isEqualTo(40L)
        assertThat(currentItem.completedTasksCount).isEqualTo(2L)
        assertThat(currentItem.completedTasksDelta).isEqualTo(1L)
    }

    @Test
    fun `getLeaderboard should use earned coins delta as tie breaker when total earned coins are equal`() {
        val oldUser = createLocalUserForValidToken(name = "Old User")
        val recentUser = testDataFactory.createTestUser(name = "Recent User")

        val household = testDataFactory.createTestHousehold(createdBy = oldUser)

        val oldMember = testDataFactory.createTestMembership(
            user = oldUser,
            household = household,
        )
        val recentMember = testDataFactory.createTestMembership(
            user = recentUser,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)

        createTaskCompletionTransaction(
            household = household,
            member = oldMember,
            amount = 100,
            createdAt = baseTime.minusDays(8),
        )
        createTaskCompletionTransaction(
            household = household,
            member = recentMember,
            amount = 100,
            createdAt = baseTime.minusDays(1),
        )

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)

        assertThat(result.items.map { it.userId })
            .containsExactly(recentUser.id, oldUser.id)

        assertThat(result.itemFor(oldUser).earnedCoins).isEqualTo(100L)
        assertThat(result.itemFor(oldUser).earnedCoinsDelta).isZero()

        assertThat(result.itemFor(recentUser).earnedCoins).isEqualTo(100L)
        assertThat(result.itemFor(recentUser).earnedCoinsDelta).isEqualTo(100L)
    }

    @Test
    fun `getLeaderboard should use recent completed tasks count as tie breaker when total and delta coins are equal`() {
        val oneTaskUser = createLocalUserForValidToken(name = "One Task User")
        val twoTasksUser = testDataFactory.createTestUser(name = "Two Tasks User")

        val household = testDataFactory.createTestHousehold(createdBy = oneTaskUser)

        val oneTaskMember = testDataFactory.createTestMembership(
            user = oneTaskUser,
            household = household,
        )
        val twoTasksMember = testDataFactory.createTestMembership(
            user = twoTasksUser,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)

        createTaskCompletionTransaction(
            household = household,
            member = oneTaskMember,
            amount = 100,
            createdAt = baseTime.minusDays(1),
        )

        createTaskCompletionTransaction(
            household = household,
            member = twoTasksMember,
            amount = 50,
            createdAt = baseTime.minusDays(1),
        )
        createTaskCompletionTransaction(
            household = household,
            member = twoTasksMember,
            amount = 50,
            createdAt = baseTime.minusDays(2),
        )

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)

        assertThat(result.items.map { it.userId })
            .containsExactly(twoTasksUser.id, oneTaskUser.id)

        assertThat(result.itemFor(oneTaskUser).earnedCoins).isEqualTo(100L)
        assertThat(result.itemFor(oneTaskUser).earnedCoinsDelta).isEqualTo(100L)
        assertThat(result.itemFor(oneTaskUser).completedTasksDelta).isEqualTo(1L)

        assertThat(result.itemFor(twoTasksUser).earnedCoins).isEqualTo(100L)
        assertThat(result.itemFor(twoTasksUser).earnedCoinsDelta).isEqualTo(100L)
        assertThat(result.itemFor(twoTasksUser).completedTasksDelta).isEqualTo(2L)
    }

    @Test
    fun `getLeaderboard should use total completed tasks count as tie breaker when recent task count is equal`() {
        val twoTasksUser = createLocalUserForValidToken(name = "Two Tasks User")
        val threeTasksUser = testDataFactory.createTestUser(name = "Three Tasks User")

        val household = testDataFactory.createTestHousehold(createdBy = twoTasksUser)

        val twoTasksMember = testDataFactory.createTestMembership(
            user = twoTasksUser,
            household = household,
        )
        val threeTasksMember = testDataFactory.createTestMembership(
            user = threeTasksUser,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)

        createTaskCompletionTransaction(
            household = household,
            member = twoTasksMember,
            amount = 50,
            createdAt = baseTime.minusDays(8),
        )
        createTaskCompletionTransaction(
            household = household,
            member = twoTasksMember,
            amount = 50,
            createdAt = baseTime.minusDays(1),
        )

        createTaskCompletionTransaction(
            household = household,
            member = threeTasksMember,
            amount = 25,
            createdAt = baseTime.minusDays(9),
        )
        createTaskCompletionTransaction(
            household = household,
            member = threeTasksMember,
            amount = 25,
            createdAt = baseTime.minusDays(8),
        )
        createTaskCompletionTransaction(
            household = household,
            member = threeTasksMember,
            amount = 50,
            createdAt = baseTime.minusDays(1),
        )

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)

        assertThat(result.items.map { it.userId })
            .containsExactly(threeTasksUser.id, twoTasksUser.id)

        assertThat(result.itemFor(twoTasksUser).earnedCoins).isEqualTo(100L)
        assertThat(result.itemFor(twoTasksUser).earnedCoinsDelta).isEqualTo(50L)
        assertThat(result.itemFor(twoTasksUser).completedTasksDelta).isEqualTo(1L)
        assertThat(result.itemFor(twoTasksUser).completedTasksCount).isEqualTo(2L)

        assertThat(result.itemFor(threeTasksUser).earnedCoins).isEqualTo(100L)
        assertThat(result.itemFor(threeTasksUser).earnedCoinsDelta).isEqualTo(50L)
        assertThat(result.itemFor(threeTasksUser).completedTasksDelta).isEqualTo(1L)
        assertThat(result.itemFor(threeTasksUser).completedTasksCount).isEqualTo(3L)
    }

    @Test
    fun `getLeaderboard should use joinedAt as technical stable tie breaker when statistics fully match`() {
        val olderUser = createLocalUserForValidToken(name = "Older User")
        val middleUser = testDataFactory.createTestUser(name = "Middle User")
        val newestUser = testDataFactory.createTestUser(name = "Newest User")

        val household = testDataFactory.createTestHousehold(createdBy = olderUser)

        val olderMember = testDataFactory.createTestMembership(
            user = olderUser,
            household = household,
        )
        val middleMember = testDataFactory.createTestMembership(
            user = middleUser,
            household = household,
        )
        val newestMember = testDataFactory.createTestMembership(
            user = newestUser,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)

        testDataFactory.updateMembershipJoinedAt(
            membershipId = olderMember.id!!,
            joinedAt = baseTime.minusDays(3),
        )
        testDataFactory.updateMembershipJoinedAt(
            membershipId = middleMember.id!!,
            joinedAt = baseTime.minusDays(2),
        )
        testDataFactory.updateMembershipJoinedAt(
            membershipId = newestMember.id!!,
            joinedAt = baseTime.minusDays(1),
        )

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)

        assertThat(result.items.map { it.userId })
            .containsExactly(
                newestUser.id,
                middleUser.id,
                olderUser.id,
            )

        assertThat(result.items.map { it.earnedCoins })
            .containsOnly(0L)

        assertThat(result.items.map { it.earnedCoinsDelta })
            .containsOnly(0L)

        assertThat(result.items.map { it.completedTasksCount })
            .containsOnly(0L)

        assertThat(result.items.map { it.completedTasksDelta })
            .containsOnly(0L)
    }

    @Test
    fun `getLeaderboard should use user id as final stable tie breaker when statistics and joinedAt match`() {
        val currentUser = createLocalUserForValidToken(name = "Current User")
        val secondUser = testDataFactory.createTestUser(name = "Second User")
        val thirdUser = testDataFactory.createTestUser(name = "Third User")

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        val currentMember = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        val secondMember = testDataFactory.createTestMembership(
            user = secondUser,
            household = household,
        )
        val thirdMember = testDataFactory.createTestMembership(
            user = thirdUser,
            household = household,
        )

        val sameJoinedAt = LocalDateTime.now(clock).minusDays(1)

        testDataFactory.updateMembershipJoinedAt(
            membershipId = currentMember.id!!,
            joinedAt = sameJoinedAt,
        )
        testDataFactory.updateMembershipJoinedAt(
            membershipId = secondMember.id!!,
            joinedAt = sameJoinedAt,
        )
        testDataFactory.updateMembershipJoinedAt(
            membershipId = thirdMember.id!!,
            joinedAt = sameJoinedAt,
        )

        authenticateAs()

        val result = leaderboardService.getLeaderboard(household.id!!)
        val orderedUserIds = result.items.map { it.userId.toString() }

        assertThat(orderedUserIds)
            .containsExactlyElementsOf(orderedUserIds.sorted())
    }

    @Test
    fun `getLeaderboard should reject inactive current user`() {
        createLocalUserForValidToken(isActive = false)

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        authenticateAs()

        assertThatThrownBy {
            leaderboardService.getLeaderboard(household.id!!)
        }.isInstanceOf(UserNotActiveException::class.java)
    }

    @Test
    fun `getLeaderboard should reject nonexistent household`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            leaderboardService.getLeaderboard(UUID.randomUUID())
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @Test
    fun `getLeaderboard should reject inactive household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = true,
        )

        authenticateAs()

        assertThatThrownBy {
            leaderboardService.getLeaderboard(household.id!!)
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `getLeaderboard should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            leaderboardService.getLeaderboard(household.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getLeaderboard should reject inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        authenticateAs()

        assertThatThrownBy {
            leaderboardService.getLeaderboard(household.id!!)
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }
}

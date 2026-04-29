package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired


class UserHouseholdServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var userHouseholdService: UserHouseholdService

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var householdRepository: HouseholdRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `joinHousehold should create membership and activity record`() {
        val currentUser = createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(
            createdBy = owner,
            inviteCode = "JOIN1234",
        )
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        authenticateAs()

        val result = userHouseholdService.joinHousehold("JOIN1234")

        val membership =
            userHouseholdRepository.findByUserIdAndHouseholdId(currentUser.id!!, household.id!!)

        val activities =
            activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(result.householdId).isEqualTo(household.id)
        assertThat(result.balance).isZero()
        assertThat(result.isUserActive).isTrue()

        assertThat(membership).isNotNull
        assertThat(membership?.isUserActive).isTrue()
        assertThat(membership?.balance).isZero()

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.USER_JOINED)
    }

    @Test
    fun `joinHousehold should reject nonexistent invite code`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.joinHousehold("NOPE1234")
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @Test
    fun `joinHousehold should reject already active member`() {
        val currentUser = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = currentUser,
            inviteCode = "ACTIVE12",
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.joinHousehold("ACTIVE12")
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `joinHousehold should reactivate existing inactive membership and reset balance`() {
        val currentUser = createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(
            createdBy = owner,
            inviteCode = "REJOIN12",
        )
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        val inactiveMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            balance = 150,
            isUserActive = false,
        )

        authenticateAs()

        val result = userHouseholdService.joinHousehold("REJOIN12")

        val reactivatedMembership =
            userHouseholdRepository.findById(inactiveMembership.id!!).orElseThrow()

        assertThat(result.id).isEqualTo(inactiveMembership.id)
        assertThat(result.isUserActive).isTrue()
        assertThat(result.balance).isZero()

        assertThat(reactivatedMembership.isUserActive).isTrue()
        assertThat(reactivatedMembership.balance).isZero()
    }

    @Test
    fun `joinHousehold should reject household with six active members`() {
        val currentUser = createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(
            createdBy = owner,
            inviteCode = "FULL1234",
        )

        repeat(6) {
            val user = testDataFactory.createTestUser()
            testDataFactory.createTestMembership(
                user = user,
                household = household,
            )
        }

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.joinHousehold("FULL1234")
        }.isInstanceOf(BusinessConflictException::class.java)

        assertThat(
            userHouseholdRepository.findByUserIdAndHouseholdId(currentUser.id!!, household.id!!)
        ).isNull()
    }

    @Test
    fun `joinHousehold should reject inactive household`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        testDataFactory.createTestHousehold(
            createdBy = owner,
            inviteCode = "INACTIVE",
            isActive = false,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.joinHousehold("INACTIVE")
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `joinHousehold should reject user with three active households`() {
        val currentUser = createLocalUserForValidToken()

        repeat(3) {
            val household = testDataFactory.createTestHousehold(createdBy = currentUser)
            testDataFactory.createTestMembership(
                user = currentUser,
                household = household,
            )
        }

        val owner = testDataFactory.createTestUser()
        val targetHousehold = testDataFactory.createTestHousehold(
            createdBy = owner,
            inviteCode = "LIMIT123",
        )
        testDataFactory.createTestMembership(
            user = owner,
            household = targetHousehold,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.joinHousehold("LIMIT123")
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `leaveHousehold should deactivate membership reset balance and create activity when other members remain`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        val currentMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            balance = 100,
        )
        testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        authenticateAs()

        userHouseholdService.leaveHousehold(household.id!!)

        entityManager.flush()
        entityManager.clear()

        val updatedMembership =
            userHouseholdRepository.findById(currentMembership.id!!).orElseThrow()

        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                currentMembership.id!!,
            )

        val activities =
            activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(updatedMembership.isUserActive).isFalse()
        assertThat(updatedMembership.balance).isZero()

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.BALANCE_RESET)
        assertThat(transactions.first().amount).isEqualTo(-100)

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.USER_LEFT)
    }

    @Test
    fun `leaveHousehold should release assigned unfinished tasks`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        val currentMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            balance = 30,
        )
        testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        val assignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = currentMembership,
            reward = 20,
        )

        authenticateAs()

        userHouseholdService.leaveHousehold(household.id!!)

        entityManager.flush()
        entityManager.clear()

        val releasedTask = taskRepository.findById(assignedTask.id!!).orElseThrow()
        val updatedMembership =
            userHouseholdRepository.findById(currentMembership.id!!).orElseThrow()

        assertThat(releasedTask.assignedTo).isNull()
        assertThat(releasedTask.assignedAt).isNull()
        assertThat(releasedTask.isCompleted).isFalse()
        assertThat(updatedMembership.isUserActive).isFalse()
        assertThat(updatedMembership.balance).isZero()
    }

    @Test
    fun `leaveHousehold should delete household when current user is last active member`() {
        val currentUser = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        val membership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            balance = 50,
        )

        authenticateAs()

        userHouseholdService.leaveHousehold(household.id!!)

        entityManager.flush()
        entityManager.clear()

        val updatedHousehold = householdRepository.findById(household.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        assertThat(updatedHousehold.isActive).isFalse()
        assertThat(updatedMembership.isUserActive).isFalse()
        assertThat(updatedMembership.balance).isZero()
    }

    @Test
    fun `removeUserFromHousehold should deactivate removed user reset balance and create activity`() {
        val actor = createLocalUserForValidToken()
        val removedUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = actor)

        val actorMembership = testDataFactory.createTestMembership(
            user = actor,
            household = household,
        )

        val removedMembership = testDataFactory.createTestMembership(
            user = removedUser,
            household = household,
            balance = 80,
        )

        authenticateAs()

        userHouseholdService.removeUserFromHousehold(
            householdId = household.id!!,
            userToRemoveId = removedUser.id!!,
        )

        entityManager.flush()
        entityManager.clear()

        val updatedActorMembership =
            userHouseholdRepository.findById(actorMembership.id!!).orElseThrow()
        val updatedRemovedMembership =
            userHouseholdRepository.findById(removedMembership.id!!).orElseThrow()

        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                removedMembership.id!!,
            )

        val activities =
            activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(updatedActorMembership.isUserActive).isTrue()

        assertThat(updatedRemovedMembership.isUserActive).isFalse()
        assertThat(updatedRemovedMembership.balance).isZero()

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.BALANCE_RESET)
        assertThat(transactions.first().amount).isEqualTo(-80)

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.USER_REMOVED)
    }

    @Test
    fun `removeUserFromHousehold should release removed users assigned unfinished tasks`() {
        val actor = createLocalUserForValidToken()
        val removedUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = actor)
        testDataFactory.createTestMembership(
            user = actor,
            household = household,
        )
        val removedMembership = testDataFactory.createTestMembership(
            user = removedUser,
            household = household,
            balance = 40,
        )

        val assignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = actor,
            assignedTo = removedMembership,
            reward = 20,
        )

        authenticateAs()

        userHouseholdService.removeUserFromHousehold(
            householdId = household.id!!,
            userToRemoveId = removedUser.id!!,
        )

        entityManager.flush()
        entityManager.clear()

        val releasedTask = taskRepository.findById(assignedTask.id!!).orElseThrow()
        val updatedRemovedMembership =
            userHouseholdRepository.findById(removedMembership.id!!).orElseThrow()

        assertThat(releasedTask.assignedTo).isNull()
        assertThat(releasedTask.assignedAt).isNull()
        assertThat(releasedTask.isCompleted).isFalse()
        assertThat(updatedRemovedMembership.isUserActive).isFalse()
        assertThat(updatedRemovedMembership.balance).isZero()
    }

    @Test
    fun `removeUserFromHousehold should reject removing yourself`() {
        val currentUser = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.removeUserFromHousehold(
                householdId = household.id!!,
                userToRemoveId = currentUser.id!!,
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `removeUserFromHousehold should reject user who is not a member`() {
        val actor = createLocalUserForValidToken()
        val outsider = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = actor)
        testDataFactory.createTestMembership(
            user = actor,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.removeUserFromHousehold(
                householdId = household.id!!,
                userToRemoveId = outsider.id!!,
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `removeUserFromHousehold should reject already inactive member`() {
        val actor = createLocalUserForValidToken()
        val removedUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = actor)
        testDataFactory.createTestMembership(
            user = actor,
            household = household,
        )
        testDataFactory.createTestMembership(
            user = removedUser,
            household = household,
            isUserActive = false,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.removeUserFromHousehold(
                householdId = household.id!!,
                userToRemoveId = removedUser.id!!,
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `getHouseholdMembers should reject non member`() {
        val currentUser = createLocalUserForValidToken()
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
            isUserActive = true,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.getHouseholdMembers(household.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getHouseholdMembers should reject inactive member`() {
        val currentUser = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            isUserActive = false,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.getHouseholdMembers(household.id!!)
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `getUserHouseholds should reflect leave and rejoin lifecycle`() {
        val currentUser = createLocalUserForValidToken()
        val owner = testDataFactory.createTestUser()
        val secondOwner = testDataFactory.createTestUser()
        val thirdOwner = testDataFactory.createTestUser()

        val stableHousehold = testDataFactory.createTestHousehold(
            createdBy = currentUser,
            name = "Stable Household",
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = stableHousehold,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = owner,
            household = stableHousehold,
            isUserActive = true,
        )

        val leaveHousehold = testDataFactory.createTestHousehold(
            createdBy = owner,
            inviteCode = "LEAVE123",
            name = "Leave Household",
        )
        testDataFactory.createTestMembership(
            user = owner,
            household = leaveHousehold,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = leaveHousehold,
            isUserActive = true,
        )

        val rejoinHousehold = testDataFactory.createTestHousehold(
            createdBy = secondOwner,
            inviteCode = "REACT123",
            name = "Rejoin Household",
        )
        testDataFactory.createTestMembership(
            user = secondOwner,
            household = rejoinHousehold,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = rejoinHousehold,
            isUserActive = false,
            balance = 70,
        )

        val inactiveHousehold = testDataFactory.createTestHousehold(
            createdBy = thirdOwner,
            name = "Inactive Household",
            isActive = false,
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = inactiveHousehold,
            isUserActive = true,
        )

        authenticateAs()

        userHouseholdService.leaveHousehold(leaveHousehold.id!!)
        userHouseholdService.joinHousehold("REACT123")

        entityManager.flush()
        entityManager.clear()

        val result = userHouseholdService.getUserHouseholds()

        assertThat(result.map { it.householdId })
            .containsExactlyInAnyOrder(stableHousehold.id, rejoinHousehold.id)
        assertThat(result.map { it.householdId })
            .doesNotContain(leaveHousehold.id, inactiveHousehold.id)
    }

    @Test
    fun `removeUserFromHousehold should reject inactive household`() {
        val actor = createLocalUserForValidToken()
        val removedUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(
            createdBy = actor,
            isActive = false,
        )
        testDataFactory.createTestMembership(
            user = actor,
            household = household,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = removedUser,
            household = household,
            isUserActive = true,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.removeUserFromHousehold(
                householdId = household.id!!,
                userToRemoveId = removedUser.id!!,
            )
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `removeUserFromHousehold should reject inactive actor membership`() {
        val actor = createLocalUserForValidToken()
        val removedUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = actor)
        testDataFactory.createTestMembership(
            user = actor,
            household = household,
            isUserActive = false,
        )
        testDataFactory.createTestMembership(
            user = removedUser,
            household = household,
            isUserActive = true,
        )

        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.removeUserFromHousehold(
                householdId = household.id!!,
                userToRemoveId = removedUser.id!!,
            )
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `getUserHouseholds should return only active memberships in active households`() {
        val currentUser = createLocalUserForValidToken()

        val activeHousehold = testDataFactory.createTestHousehold(
            createdBy = currentUser,
            name = "Active Household",
        )
        val inactiveMembershipHousehold = testDataFactory.createTestHousehold(
            createdBy = currentUser,
            name = "Inactive Membership Household",
        )
        val inactiveHousehold = testDataFactory.createTestHousehold(
            createdBy = currentUser,
            name = "Inactive Household",
            isActive = false,
        )

        testDataFactory.createTestMembership(
            user = currentUser,
            household = activeHousehold,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = inactiveMembershipHousehold,
            isUserActive = false,
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = inactiveHousehold,
            isUserActive = true,
        )

        authenticateAs()

        val result = userHouseholdService.getUserHouseholds()

        assertThat(result).hasSize(1)
        assertThat(result.first().householdId).isEqualTo(activeHousehold.id)
    }

    @Test
    fun `getHouseholdMembers should return only active members`() {
        val currentUser = createLocalUserForValidToken()
        val activeUser = testDataFactory.createTestUser(name = "Active User")
        val inactiveUser = testDataFactory.createTestUser(name = "Inactive User")

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = activeUser,
            household = household,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = inactiveUser,
            household = household,
            isUserActive = false,
        )

        authenticateAs()

        val result = userHouseholdService.getHouseholdMembers(household.id!!)

        assertThat(result.map { it.id })
            .containsExactlyInAnyOrder(currentUser.id, activeUser.id)

        assertThat(result.map { it.id })
            .doesNotContain(inactiveUser.id)
    }
}

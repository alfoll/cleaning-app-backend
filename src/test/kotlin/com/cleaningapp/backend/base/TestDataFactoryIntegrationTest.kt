package com.cleaningapp.backend.base

import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDateTime

class TestDataFactoryIntegrationTest() : BaseIntegrationTest() {

    @Autowired
    lateinit var testDataFactory: TestDataFactory

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `factory create valid user household membership and activity`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )
        val activity = testDataFactory.createTestActivity(
            household = household,
            member =  membership,
            activityType = ActivityType.USER_JOINED,
        )

        assertThat(user.id).isNotNull()
        assertThat(household.id).isNotNull()
        assertThat(membership.id).isNotNull()
        assertThat(activity.id).isNotNull()

        assertThat(membership.user.id).isEqualTo(user.id)
        assertThat(membership.household.id).isEqualTo(household.id)
        assertThat(activity.household.id).isEqualTo(household.id)
        assertThat(activity.member.id).isEqualTo(membership.id)
    }

    @Test
    fun `createTestMembership should create consistent default user household membership graph`() {
        val membership = testDataFactory.createTestMembership()

        assertThat(membership.id).isNotNull()
        assertThat(membership.user.id).isNotNull()
        assertThat(membership.household.id).isNotNull()

        assertThat(membership.balance).isZero()
        assertThat(membership.isUserActive).isTrue()

        assertThat(membership.household.createdByUser.id)
            .isEqualTo(membership.user.id)
    }

    @Test
    fun `task factory methods should create valid free assigned and completed task states`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val now = LocalDateTime.now(clock)

        val freeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            reward = 20,
        )

        assertThat(freeTask.id).isNotNull()
        assertThat(freeTask.household.id).isEqualTo(household.id)
        assertThat(freeTask.createdBy.id).isEqualTo(user.id)
        assertThat(freeTask.reward).isEqualTo(20)
        assertThat(freeTask.assignedTo).isNull()
        assertThat(freeTask.assignedAt).isNull()
        assertThat(freeTask.isCompleted).isFalse()
        assertThat(freeTask.completedBy).isNull()
        assertThat(freeTask.completedAt).isNull()

        val assignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = member,
            reward = 30,
        )

        assertThat(assignedTask.id).isNotNull()
        assertThat(assignedTask.household.id).isEqualTo(household.id)
        assertThat(assignedTask.assignedTo?.id).isEqualTo(member.id)
        assertThat(assignedTask.assignedAt).isEqualTo(now)
        assertThat(assignedTask.isCompleted).isFalse()
        assertThat(assignedTask.completedBy).isNull()
        assertThat(assignedTask.completedAt).isNull()

        val completedTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
            reward = 40,
        )

        assertThat(completedTask.id).isNotNull()
        assertThat(completedTask.household.id).isEqualTo(household.id)
        assertThat(completedTask.assignedTo).isNull()
        assertThat(completedTask.assignedAt).isNull()
        assertThat(completedTask.isCompleted).isTrue()
        assertThat(completedTask.completedBy?.id).isEqualTo(member.id)
        assertThat(completedTask.completedAt).isEqualTo(now)
    }

    @Test
    fun `createTestPrivilege should create available and bought privilege states`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val availablePrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
        )

        assertThat(availablePrivilege.id).isNotNull()
        assertThat(availablePrivilege.household.id).isEqualTo(household.id)
        assertThat(availablePrivilege.createdBy.id).isEqualTo(user.id)
        assertThat(availablePrivilege.cost).isEqualTo(50)
        assertThat(availablePrivilege.isAvailable).isTrue()
        assertThat(availablePrivilege.boughtBy).isNull()

        val boughtPrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 70,
            isAvailable = false,
            boughtBy = member,
        )

        assertThat(boughtPrivilege.id).isNotNull()
        assertThat(boughtPrivilege.household.id).isEqualTo(household.id)
        assertThat(boughtPrivilege.cost).isEqualTo(70)
        assertThat(boughtPrivilege.isAvailable).isFalse()
        assertThat(boughtPrivilege.boughtBy?.id).isEqualTo(member.id)
    }

    @Test
    fun `transaction factory methods should create transactions with correct type amount and createdAt`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val createdAt = LocalDateTime.now(clock).minusDays(3)

        val completedTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
            reward = 20,
        )
        val taskCompletionTransaction =
            testDataFactory.createTestTaskCompletionTransaction(
                household = household,
                member = member,
                task = completedTask,
                createdAt = createdAt,
            )

        assertThat(taskCompletionTransaction.id).isNotNull()
        assertThat(taskCompletionTransaction.household.id).isEqualTo(household.id)
        assertThat(taskCompletionTransaction.member.id).isEqualTo(member.id)
        assertThat(taskCompletionTransaction.task?.id).isEqualTo(completedTask.id)
        assertThat(taskCompletionTransaction.privilege).isNull()
        assertThat(taskCompletionTransaction.type).isEqualTo(TransactionType.TASK_COMPLETION)
        assertThat(taskCompletionTransaction.amount).isEqualTo(20)
        assertThat(taskCompletionTransaction.createdAt).isEqualTo(createdAt)

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        val privilegePurchaseTransaction =
            testDataFactory.createTestPrivilegePurchaseTransaction(
                household = household,
                member = member,
                privilege = privilege,
                createdAt = createdAt,
            )

        assertThat(privilegePurchaseTransaction.id).isNotNull()
        assertThat(privilegePurchaseTransaction.household.id).isEqualTo(household.id)
        assertThat(privilegePurchaseTransaction.member.id).isEqualTo(member.id)
        assertThat(privilegePurchaseTransaction.task).isNull()
        assertThat(privilegePurchaseTransaction.privilege?.id).isEqualTo(privilege.id)
        assertThat(privilegePurchaseTransaction.type).isEqualTo(TransactionType.PRIVILEGE_BOUGHT)
        assertThat(privilegePurchaseTransaction.amount).isEqualTo(-50)
        assertThat(privilegePurchaseTransaction.createdAt).isEqualTo(createdAt)

        val balanceResetTransaction =
            testDataFactory.createTestBalanceResetTransaction(
                household = household,
                member = member,
                createdAt = createdAt,
            )

        assertThat(balanceResetTransaction.id).isNotNull()
        assertThat(balanceResetTransaction.household.id).isEqualTo(household.id)
        assertThat(balanceResetTransaction.member.id).isEqualTo(member.id)
        assertThat(balanceResetTransaction.task).isNull()
        assertThat(balanceResetTransaction.privilege).isNull()
        assertThat(balanceResetTransaction.type).isEqualTo(TransactionType.BALANCE_RESET)
        assertThat(balanceResetTransaction.amount).isEqualTo(-100)
        assertThat(balanceResetTransaction.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `transaction factory methods should not update member balance automatically`(){
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val completedTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
            reward = 20,
        )

        testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = member,
            task = completedTask,
        )

        val memberAfterTaskCompletion =
            userHouseholdRepository.findById(member.id!!).orElseThrow()

        assertThat(memberAfterTaskCompletion.balance).isEqualTo(100)

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = member,
            privilege = privilege,
        )

        val memberAfterPrivilegePurchase =
            userHouseholdRepository.findById(member.id!!).orElseThrow()

        assertThat(memberAfterPrivilegePurchase.balance).isEqualTo(100)
    }

    @Test
    fun `createTestActivity should create activity linked to household and member`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val activity = testDataFactory.createTestActivity(
            household = household,
            member = member,
            activityType = ActivityType.TASK_COMPLETED,
        )

        assertThat(activity.id).isNotNull()
        assertThat(activity.household.id).isEqualTo(household.id)
        assertThat(activity.member.id).isEqualTo(member.id)
        assertThat(activity.activityType).isEqualTo(ActivityType.TASK_COMPLETED)
        assertThat(activity.title).isEqualTo("Test activity")
        assertThat(activity.description).isEqualTo("Test activity description")
    }

    @Test
    fun `factory should reject member from another household for activity`() {
        val userA = testDataFactory.createTestUser()
        val householdA = testDataFactory.createTestHousehold(createdBy = userA)

        val userB = testDataFactory.createTestUser()
        val householdB = testDataFactory.createTestHousehold(createdBy = userB)
        val memberB = testDataFactory.createTestMembership(
            user = userB,
            household = householdB,
        )

        assertThatThrownBy {
            testDataFactory.createTestActivity(
                household = householdA,
                member = memberB,
                activityType = ActivityType.TASK_CREATED,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
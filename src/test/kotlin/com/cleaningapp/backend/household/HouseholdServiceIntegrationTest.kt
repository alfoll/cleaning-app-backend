package com.cleaningapp.backend.household

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.privilege.PrivilegeRepository
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.taskplan.TaskPlanRepository
import com.cleaningapp.backend.tasktemplate.TaskTemplateRepository
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired


class HouseholdServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var householdService: HouseholdService

    @Autowired
    private lateinit var householdRepository: HouseholdRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var taskTemplateRepository: TaskTemplateRepository

    @Autowired
    private lateinit var privilegeRepository: PrivilegeRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `createHousehold should create household creator membership and activity records`() {
        val user = createLocalUserForValidToken()
        authenticateAs()

        val result = householdService.createHousehold(
            HouseholdRegisterDTO(name = "Kitchen Flat")
        )

        val savedHousehold = householdRepository.findById(result.id).orElseThrow()
        val creatorMembership =
            userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, result.id)

        val activities =
            activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(result.id)

        assertThat(result.name).isEqualTo("Kitchen Flat")
        assertThat(result.inviteCode).hasSize(8)
        assertThat(result.createdByUser).isEqualTo(user.id)
        assertThat(result.isActive).isTrue()

        assertThat(savedHousehold.isActive).isTrue()
        assertThat(savedHousehold.createdByUser.id).isEqualTo(user.id)

        assertThat(creatorMembership).isNotNull
        assertThat(creatorMembership?.isUserActive).isTrue()
        assertThat(creatorMembership?.balance).isZero()

        assertThat(activities.map { it.activityType })
            .containsExactlyInAnyOrder(
                ActivityType.HOUSEHOLD_CREATED,
                ActivityType.USER_JOINED,
            )
    }

    @Test
    fun `createHousehold should reject user with three active households`() {
        val user = createLocalUserForValidToken()

        repeat(3) {
            val household = testDataFactory.createTestHousehold(createdBy = user)
            testDataFactory.createTestMembership(
                user = user,
                household = household,
            )
        }

        authenticateAs()

        assertThatThrownBy {
            householdService.createHousehold(
                HouseholdRegisterDTO(name = "Fourth Household")
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `findHouseholdById should return household for active member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            name = "Active Household",
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        authenticateAs()

        val result = householdService.findHouseholdById(household.id!!)

        assertThat(result.id).isEqualTo(household.id)
        assertThat(result.name).isEqualTo("Active Household")
    }

    @Test
    fun `findHouseholdById should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        authenticateAs()

        assertThatThrownBy {
            householdService.findHouseholdById(household.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `updateHousehold should update household name for active member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            name = "Old Household Name",
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        authenticateAs()

        val result = householdService.updateHousehold(
            householdId = household.id!!,
            newHousehold = HouseholdRegisterDTO(name = "New Household Name"),
        )

        val updatedHousehold = householdRepository.findById(household.id!!).orElseThrow()

        assertThat(result.name).isEqualTo("New Household Name")
        assertThat(updatedHousehold.name).isEqualTo("New Household Name")
    }

    @Test
    fun `deleteHousehold should deactivate household deactivate memberships and delete household data`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val taskPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = user,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            taskPlan = taskPlan,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
        )

        val taskTemplate = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = user,
        )

        testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_COMPLETED,
        )

        testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = membership,
            task = task,
            amount = 20,
        )

        authenticateAs()

        householdService.deleteHousehold(household.id!!)

        entityManager.flush()
        entityManager.clear()

        val deletedHousehold = householdRepository.findById(household.id!!).orElseThrow()
        val deletedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        assertThat(deletedHousehold.isActive).isFalse()
        assertThat(deletedMembership.isUserActive).isFalse()
        assertThat(deletedMembership.balance).isZero()

        assertThat(taskRepository.findById(task.id!!)).isEmpty
        assertThat(taskPlanRepository.findById(taskPlan.id!!)).isEmpty
        assertThat(taskTemplateRepository.findById(taskTemplate.id!!)).isEmpty
        assertThat(privilegeRepository.findById(privilege.id!!)).isEmpty

        assertThat(
            activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)
        ).isEmpty()

        assertThat(
            transactionRepository.findAll()
                .filter { it.household.id == household.id }
        ).isEmpty()
    }

    @Test
    fun `findHouseholdByInviteCode should return active household without membership check`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(
            createdBy = owner,
            name = "Invite Household",
            inviteCode = "ABCD1234",
        )

        val result = householdService.findHouseholdByInviteCode("ABCD1234")

        assertThat(result.id).isEqualTo(household.id)
        assertThat(result.name).isEqualTo("Invite Household")
        assertThat(result.inviteCode).isEqualTo("ABCD1234")
    }

    @Test
    fun `findHouseholdByInviteCode should reject inactive household`() {
        val owner = testDataFactory.createTestUser()
        testDataFactory.createTestHousehold(
            createdBy = owner,
            inviteCode = "INACT123",
            isActive = false,
        )

        assertThatThrownBy {
            householdService.findHouseholdByInviteCode("INACT123")
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `findHouseholdById should reject inactive household`() {
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
            householdService.findHouseholdById(household.id!!)
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `updateHousehold should reject user without membership`() {
        val currentUser = createLocalUserForValidToken()
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(
            createdBy = owner,
            name = "Foreign Household",
        )
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            householdService.updateHousehold(
                householdId = household.id!!,
                newHousehold = HouseholdRegisterDTO(name = "Should Not Update"),
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `deleteHousehold should reject user without membership`() {
        createLocalUserForValidToken()
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            householdService.deleteHousehold(household.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)

        val unchangedHousehold = householdRepository.findById(household.id!!).orElseThrow()
        assertThat(unchangedHousehold.isActive).isTrue()
    }

    @Test
    fun `findHouseholdById should reject nonexistent household`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            householdService.findHouseholdById(java.util.UUID.randomUUID())
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @Test
    fun `findHouseholdByInviteCode should reject nonexistent invite code`() {
        assertThatThrownBy {
            householdService.findHouseholdByInviteCode("MISS1234")
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @Test
    fun `deleteHousehold should deactivate all memberships and clear all balances`() {
        val owner = createLocalUserForValidToken()
        val secondUser = testDataFactory.createTestUser()
        val thirdUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val ownerMembership = testDataFactory.createTestMembership(
            user = owner,
            household = household,
            balance = 100,
        )
        val secondMembership = testDataFactory.createTestMembership(
            user = secondUser,
            household = household,
            balance = 60,
        )
        val thirdMembership = testDataFactory.createTestMembership(
            user = thirdUser,
            household = household,
            balance = 10,
            isUserActive = false,
        )

        authenticateAs()

        householdService.deleteHousehold(household.id!!)

        entityManager.flush()
        entityManager.clear()

        val updatedHousehold = householdRepository.findById(household.id!!).orElseThrow()
        val updatedOwnerMembership = userHouseholdRepository.findById(ownerMembership.id!!).orElseThrow()
        val updatedSecondMembership = userHouseholdRepository.findById(secondMembership.id!!).orElseThrow()
        val updatedThirdMembership = userHouseholdRepository.findById(thirdMembership.id!!).orElseThrow()

        assertThat(updatedHousehold.isActive).isFalse()
        assertThat(updatedOwnerMembership.isUserActive).isFalse()
        assertThat(updatedOwnerMembership.balance).isZero()
        assertThat(updatedSecondMembership.isUserActive).isFalse()
        assertThat(updatedSecondMembership.balance).isZero()
        assertThat(updatedThirdMembership.isUserActive).isFalse()
        assertThat(updatedThirdMembership.balance).isZero()
    }
}

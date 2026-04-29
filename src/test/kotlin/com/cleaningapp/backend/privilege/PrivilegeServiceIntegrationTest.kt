package com.cleaningapp.backend.privilege

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.PrivilegeNotFoundException
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID
import java.util.stream.Stream

class PrivilegeServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var privilegeService: PrivilegeService

    @Autowired
    private lateinit var privilegeRepository: PrivilegeRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    enum class PrivilegeOperation {
        UPDATE,
        DELETE,
        BUY,
        GET_BY_ID,
    }

    companion object {
        @JvmStatic
        fun privilegeOperations(): Stream<PrivilegeOperation> =
            Stream.of(
                PrivilegeOperation.UPDATE,
                PrivilegeOperation.DELETE,
                PrivilegeOperation.BUY,
                PrivilegeOperation.GET_BY_ID,
            )
    }

    private fun executePrivilegeOperation(
        operation: PrivilegeOperation,
        privilegeId: UUID,
    ) {
        when (operation) {
            PrivilegeOperation.UPDATE -> privilegeService.updatePrivilege(
                privilegeId = privilegeId,
                newPrivilege = PrivilegeRegisterDTO(
                    title = "Updated privilege",
                    description = "Updated description",
                    cost = 40,
                )
            )

            PrivilegeOperation.DELETE -> privilegeService.deletePrivilege(privilegeId)

            PrivilegeOperation.BUY -> privilegeService.buyPrivilege(privilegeId)

            PrivilegeOperation.GET_BY_ID -> privilegeService.getPrivilegeById(privilegeId)
        }
    }

    @Test
    fun `createPrivilege should create privilege and activity for active household member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        authenticateAs()

        val result = privilegeService.createPrivilege(
            householdId = household.id!!,
            privilege = PrivilegeRegisterDTO(
                title = "Choose movie",
                description = "Buyer chooses movie for the evening",
                cost = 50,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val savedPrivilege = privilegeRepository.findById(result.id).orElseThrow()
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(result.householdId).isEqualTo(household.id)
        assertThat(result.createdBy).isEqualTo(user.id)
        assertThat(result.title).isEqualTo("Choose movie")
        assertThat(result.description).isEqualTo("Buyer chooses movie for the evening")
        assertThat(result.cost).isEqualTo(50)
        assertThat(result.isAvailable).isTrue()
        assertThat(result.boughtBy).isNull()

        assertThat(savedPrivilege.household.id).isEqualTo(household.id)
        assertThat(savedPrivilege.createdBy.id).isEqualTo(user.id)
        assertThat(savedPrivilege.title).isEqualTo("Choose movie")
        assertThat(savedPrivilege.cost).isEqualTo(50)
        assertThat(savedPrivilege.isAvailable).isTrue()
        assertThat(savedPrivilege.boughtBy).isNull()

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.PRIVILEGE_CREATED)
        assertThat(activities.first { it.activityType == ActivityType.PRIVILEGE_CREATED }.member.id)
            .isEqualTo(membership.id)
    }

    @Test
    fun `createPrivilege should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.createPrivilege(
                householdId = household.id!!,
                privilege = PrivilegeRegisterDTO(
                    title = "Foreign privilege",
                    description = null,
                    cost = 50,
                )
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `createPrivilege should reject inactive household`() {
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
            privilegeService.createPrivilege(
                householdId = household.id!!,
                privilege = PrivilegeRegisterDTO(
                    title = "Inactive household privilege",
                    description = null,
                    cost = 50,
                )
            )
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `createPrivilege should reject inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.createPrivilege(
                householdId = household.id!!,
                privilege = PrivilegeRegisterDTO(
                    title = "Inactive membership privilege",
                    description = null,
                    cost = 50,
                )
            )
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `createPrivilege should reject nonexistent household`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            privilegeService.createPrivilege(
                householdId = UUID.randomUUID(),
                privilege = PrivilegeRegisterDTO(
                    title = "Missing household privilege",
                    description = null,
                    cost = 50,
                )
            )
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @Test
    fun `updatePrivilege should update available privilege by creator`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Old privilege",
            description = "Old description",
            cost = 40,
        )

        authenticateAs()

        val result = privilegeService.updatePrivilege(
            privilegeId = privilege.id!!,
            newPrivilege = PrivilegeRegisterDTO(
                title = "Updated privilege",
                description = "Updated description",
                cost = 60,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val updatedPrivilege = privilegeRepository.findById(privilege.id!!).orElseThrow()

        assertThat(result.title).isEqualTo("Updated privilege")
        assertThat(result.description).isEqualTo("Updated description")
        assertThat(result.cost).isEqualTo(60)
        assertThat(result.isAvailable).isTrue()
        assertThat(result.boughtBy).isNull()

        assertThat(updatedPrivilege.title).isEqualTo("Updated privilege")
        assertThat(updatedPrivilege.description).isEqualTo("Updated description")
        assertThat(updatedPrivilege.cost).isEqualTo(60)
    }

    @Test
    fun `updatePrivilege should reject non creator`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = currentUser, household = household)

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = creator,
            cost = 50,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.updatePrivilege(
                privilegeId = privilege.id!!,
                newPrivilege = PrivilegeRegisterDTO(
                    title = "Illegal update",
                    description = null,
                    cost = 60,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `updatePrivilege should reject bought privilege`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.updatePrivilege(
                privilegeId = privilege.id!!,
                newPrivilege = PrivilegeRegisterDTO(
                    title = "Cannot update bought",
                    description = null,
                    cost = 60,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `updatePrivilege should reject nonexistent privilege`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            privilegeService.updatePrivilege(
                privilegeId = UUID.randomUUID(),
                newPrivilege = PrivilegeRegisterDTO(
                    title = "Missing privilege",
                    description = null,
                    cost = 50,
                )
            )
        }.isInstanceOf(PrivilegeNotFoundException::class.java)
    }

    @Test
    fun `deletePrivilege should delete available privilege by creator`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
        )

        authenticateAs()

        privilegeService.deletePrivilege(privilege.id!!)

        entityManager.flush()
        entityManager.clear()

        assertThat(privilegeRepository.findById(privilege.id!!)).isEmpty
    }

    @Test
    fun `deletePrivilege should reject non creator`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = currentUser, household = household)

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = creator,
            cost = 50,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.deletePrivilege(privilege.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `deletePrivilege should reject bought privilege`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.deletePrivilege(privilege.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `buyPrivilege should buy available privilege decrease balance create transaction and activity`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 90,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Skip cleaning",
            cost = 50,
        )

        authenticateAs()

        val result = privilegeService.buyPrivilege(privilege.id!!)

        entityManager.flush()
        entityManager.clear()

        val updatedPrivilege = privilegeRepository.findById(privilege.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val transactions = transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
            household.id!!,
            membership.id!!,
        )
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(result.isAvailable).isFalse()
        assertThat(result.boughtBy).isEqualTo(user.id)

        assertThat(updatedPrivilege.isAvailable).isFalse()
        assertThat(updatedPrivilege.boughtBy?.id).isEqualTo(membership.id)
        assertThat(updatedMembership.balance).isEqualTo(40)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.PRIVILEGE_BOUGHT)
        assertThat(transactions.first().amount).isEqualTo(-50)
        assertThat(transactions.first().privilege?.id).isEqualTo(privilege.id)

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.PRIVILEGE_BOUGHT)
    }

    @Test
    fun `buyPrivilege should reject insufficient balance`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 20,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.buyPrivilege(privilege.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)

        entityManager.flush()
        entityManager.clear()

        val updatedPrivilege = privilegeRepository.findById(privilege.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val transactions = transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
            household.id!!,
            membership.id!!,
        )

        assertThat(updatedPrivilege.isAvailable).isTrue()
        assertThat(updatedPrivilege.boughtBy).isNull()
        assertThat(updatedMembership.balance).isEqualTo(20)
        assertThat(transactions).isEmpty()
    }

    @Test
    fun `buyPrivilege should reject already bought privilege`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.buyPrivilege(privilege.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)

        entityManager.flush()
        entityManager.clear()

        val updatedPrivilege = privilegeRepository.findById(privilege.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val transactions = transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
            household.id!!,
            membership.id!!,
        )

        assertThat(updatedPrivilege.isAvailable).isFalse()
        assertThat(updatedPrivilege.boughtBy?.id).isEqualTo(membership.id)
        assertThat(updatedMembership.balance).isEqualTo(100)
        assertThat(transactions).isEmpty()
    }

    @Test
    fun `buyPrivilege should reject duplicate purchase and charge balance once`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
        )

        authenticateAs()

        privilegeService.buyPrivilege(privilege.id!!)

        assertThatThrownBy {
            privilegeService.buyPrivilege(privilege.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)

        entityManager.flush()
        entityManager.clear()

        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val transactions = transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
            household.id!!,
            membership.id!!,
        )

        assertThat(updatedMembership.balance).isEqualTo(50)
        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.PRIVILEGE_BOUGHT)
        assertThat(transactions.first().amount).isEqualTo(-50)
    }

    @Test
    fun `getPrivilegeById should return privilege for active household member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Choose dinner",
            cost = 50,
        )

        authenticateAs()

        val result = privilegeService.getPrivilegeById(privilege.id!!)

        assertThat(result.id).isEqualTo(privilege.id)
        assertThat(result.householdId).isEqualTo(household.id)
        assertThat(result.createdBy).isEqualTo(user.id)
        assertThat(result.title).isEqualTo("Choose dinner")
        assertThat(result.cost).isEqualTo(50)
    }

    @Test
    fun `getPrivilegeById should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = owner,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.getPrivilegeById(privilege.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getHouseholdPrivileges should reject inactive household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.getHouseholdPrivileges(household.id!!, PrivilegeFilterType.ALL)
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `getHouseholdPrivileges should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )
        testDataFactory.createTestPrivilege(
            household = household,
            createdBy = owner,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.getHouseholdPrivileges(household.id!!, PrivilegeFilterType.ALL)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getHouseholdPrivileges should reject inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )
        testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
        )

        authenticateAs()

        assertThatThrownBy {
            privilegeService.getHouseholdPrivileges(household.id!!, PrivilegeFilterType.ALL)
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `getHouseholdPrivileges should reject nonexistent household`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            privilegeService.getHouseholdPrivileges(UUID.randomUUID(), PrivilegeFilterType.ALL)
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @ParameterizedTest
    @MethodSource("privilegeOperations")
    fun `privilege operation should reject non member`(operation: PrivilegeOperation) {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
            balance = 100,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = owner,
            cost = 50,
        )

        authenticateAs()

        assertThatThrownBy {
            executePrivilegeOperation(operation, privilege.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @ParameterizedTest
    @MethodSource("privilegeOperations")
    fun `privilege operation should reject inactive membership`(operation: PrivilegeOperation) {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
            isUserActive = false,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
        )

        authenticateAs()

        assertThatThrownBy {
            executePrivilegeOperation(operation, privilege.id!!)
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @ParameterizedTest
    @MethodSource("privilegeOperations")
    fun `privilege operation should reject inactive household`(operation: PrivilegeOperation) {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
            isUserActive = true,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
        )

        authenticateAs()

        assertThatThrownBy {
            executePrivilegeOperation(operation, privilege.id!!)
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `getHouseholdPrivileges should return privileges sorted according to filter contract`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val olderAvailablePrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Older available",
            cost = 20,
        )
        val newerAvailablePrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Newer available",
            cost = 30,
        )
        val olderBoughtPrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Older bought",
            cost = 40,
            isAvailable = false,
            boughtBy = membership,
        )
        val newerBoughtPrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Newer bought",
            cost = 50,
            isAvailable = false,
            boughtBy = membership,
        )

        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = olderAvailablePrivilege.id!!,
            createdAt = baseTime.minusDays(4),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = newerAvailablePrivilege.id!!,
            createdAt = baseTime.minusDays(1),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = olderBoughtPrivilege.id!!,
            createdAt = baseTime.minusDays(3),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = newerBoughtPrivilege.id!!,
            createdAt = baseTime.minusDays(2),
        )

        authenticateAs()

        val all = privilegeService.getHouseholdPrivileges(household.id!!, PrivilegeFilterType.ALL)
        val available = privilegeService.getHouseholdPrivileges(household.id!!, PrivilegeFilterType.AVAILABLE)
        val my = privilegeService.getHouseholdPrivileges(household.id!!, PrivilegeFilterType.MY)

        assertThat(all.map { it.id })
            .containsExactly(
                newerAvailablePrivilege.id,
                newerBoughtPrivilege.id,
                olderBoughtPrivilege.id,
                olderAvailablePrivilege.id,
            )

        assertThat(available.map { it.id })
            .containsExactly(
                newerAvailablePrivilege.id,
                olderAvailablePrivilege.id,
            )

        assertThat(my.map { it.id })
            .containsExactly(
                newerBoughtPrivilege.id,
                olderBoughtPrivilege.id,
            )
    }

    @Test
    fun `getHouseholdPrivileges MY should include only privileges bought by current member`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        val currentMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            balance = 100,
        )
        val otherMembership = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
            balance = 100,
        )

        val myBoughtPrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = currentUser,
            title = "My bought privilege",
            cost = 40,
            isAvailable = false,
            boughtBy = currentMembership,
        )
        testDataFactory.createTestPrivilege(
            household = household,
            createdBy = otherUser,
            title = "Other bought privilege",
            cost = 50,
            isAvailable = false,
            boughtBy = otherMembership,
        )
        testDataFactory.createTestPrivilege(
            household = household,
            createdBy = currentUser,
            title = "Available privilege",
            cost = 30,
        )

        authenticateAs()

        val my = privilegeService.getHouseholdPrivileges(household.id!!, PrivilegeFilterType.MY)

        assertThat(my.map { it.id })
            .containsExactly(myBoughtPrivilege.id)
    }
}

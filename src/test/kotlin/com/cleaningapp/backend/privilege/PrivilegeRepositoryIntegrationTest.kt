package com.cleaningapp.backend.privilege

import com.cleaningapp.backend.base.BaseIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class PrivilegeRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var privilegeRepository: PrivilegeRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `privilege filter queries should return expected privileges with repository sorting contract`() {
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

        val olderAvailablePrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = currentUser,
            title = "Older available",
            isAvailable = true,
            boughtBy = null,
        )
        val newerAvailablePrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = currentUser,
            title = "Newer available",
            isAvailable = true,
            boughtBy = null,
        )

        val olderBoughtByCurrent = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = currentUser,
            title = "Older bought by current",
            isAvailable = false,
            boughtBy = currentMembership,
        )
        val newerBoughtByCurrent = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = currentUser,
            title = "Newer bought by current",
            isAvailable = false,
            boughtBy = currentMembership,
        )

        val boughtByOther = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = otherUser,
            title = "Bought by other",
            isAvailable = false,
            boughtBy = otherMembership,
        )

        val inconsistentUnavailableWithoutBuyer = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = currentUser,
            title = "Unavailable without buyer",
            isAvailable = false,
            boughtBy = null,
        )

        val otherHouseholdPrivilege = testDataFactory.createTestPrivilege(
            household = otherHousehold,
            createdBy = currentUser,
            title = "Other household privilege",
            isAvailable = false,
            boughtBy = otherHouseholdMembership,
        )

        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = olderAvailablePrivilege.id!!,
            createdAt = baseTime.minusDays(7),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = newerAvailablePrivilege.id!!,
            createdAt = baseTime.minusDays(1),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = olderBoughtByCurrent.id!!,
            createdAt = baseTime.minusDays(6),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = newerBoughtByCurrent.id!!,
            createdAt = baseTime.minusDays(2),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = boughtByOther.id!!,
            createdAt = baseTime.minusDays(3),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = inconsistentUnavailableWithoutBuyer.id!!,
            createdAt = baseTime.minusDays(4),
        )
        testDataFactory.updatePrivilegeCreatedAt(
            privilegeId = otherHouseholdPrivilege.id!!,
            createdAt = baseTime.plusDays(1),
        )

        val allPrivileges = privilegeRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)
        val availablePrivileges =
            privilegeRepository.findAllByHouseholdIdAndIsAvailableTrueAndBoughtByIsNullOrderByCreatedAtDesc(
                household.id!!,
            )
        val myPrivileges =
            privilegeRepository.findAllByHouseholdIdAndBoughtByIdOrderByCreatedAtDesc(
                household.id!!,
                currentMembership.id!!,
            )

        assertThat(allPrivileges.map { it.id })
            .containsExactly(
                newerAvailablePrivilege.id,
                newerBoughtByCurrent.id,
                boughtByOther.id,
                inconsistentUnavailableWithoutBuyer.id,
                olderBoughtByCurrent.id,
                olderAvailablePrivilege.id,
            )
            .doesNotContain(otherHouseholdPrivilege.id)

        assertThat(availablePrivileges.map { it.id })
            .containsExactly(
                newerAvailablePrivilege.id,
                olderAvailablePrivilege.id,
            )

        assertThat(myPrivileges.map { it.id })
            .containsExactly(
                newerBoughtByCurrent.id,
                olderBoughtByCurrent.id,
            )
    }

    @Test
    fun `deleteAllByHouseholdId should delete only privileges of selected household`() {
        val user = createLocalUserForValidToken()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        val otherMembership = testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
        )

        val privilegeOne = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            isAvailable = true,
            boughtBy = null,
        )
        val privilegeTwo = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            isAvailable = false,
            boughtBy = membership,
        )
        val otherHouseholdPrivilege = testDataFactory.createTestPrivilege(
            household = otherHousehold,
            createdBy = user,
            isAvailable = false,
            boughtBy = otherMembership,
        )

        val deletedCount = privilegeRepository.deleteAllByHouseholdId(household.id!!)

        entityManager.flush()
        entityManager.clear()

        assertThat(deletedCount).isEqualTo(2)
        assertThat(privilegeRepository.findById(privilegeOne.id!!)).isEmpty()
        assertThat(privilegeRepository.findById(privilegeTwo.id!!)).isEmpty()
        assertThat(privilegeRepository.findById(otherHouseholdPrivilege.id!!)).isPresent
    }
}
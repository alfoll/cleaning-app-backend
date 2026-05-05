package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.base.BaseIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class UserHouseholdRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Test
    fun `user household repository should find membership by exact user and household pair`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)

        val targetMembership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
            isUserActive = true,
        )

        val found = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
        val missingPair = userHouseholdRepository.findByUserIdAndHouseholdId(otherUser.id!!, otherHousehold.id!!)

        assertThat(found?.id).isEqualTo(targetMembership.id)
        assertThat(missingPair).isNull()
    }

    @Test
    fun `user household repository should apply active filters and counts for household and user queries`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = currentUser)

        val activeInHousehold = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            isUserActive = true,
        )
        val inactiveInHousehold = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
            isUserActive = false,
        )
        val activeInOtherHousehold = testDataFactory.createTestMembership(
            user = currentUser,
            household = otherHousehold,
            isUserActive = true,
        )
        val inactiveInOtherHousehold = testDataFactory.createTestMembership(
            user = currentUser,
            household = testDataFactory.createTestHousehold(createdBy = currentUser),
            isUserActive = false,
        )

        val allByHousehold = userHouseholdRepository.findAllByHouseholdId(household.id!!)
        val activeByHousehold = userHouseholdRepository.findAllByHouseholdIdAndIsUserActiveTrue(household.id!!)
        val activeCountByHousehold = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)

        val activeByUser = userHouseholdRepository.findAllByUserIdAndIsUserActiveTrue(currentUser.id!!)
        val activeCountByUser = userHouseholdRepository.countByUserIdAndIsUserActiveTrue(currentUser.id!!)

        assertThat(allByHousehold.map { it.id })
            .containsExactlyInAnyOrder(activeInHousehold.id, inactiveInHousehold.id)

        assertThat(activeByHousehold.map { it.id })
            .containsExactly(activeInHousehold.id)

        assertThat(activeCountByHousehold).isEqualTo(1)

        assertThat(activeByUser.map { it.id })
            .containsExactlyInAnyOrder(activeInHousehold.id, activeInOtherHousehold.id)
            .doesNotContain(inactiveInOtherHousehold.id)

        assertThat(activeCountByUser).isEqualTo(2)
    }
}

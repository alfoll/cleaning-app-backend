package com.cleaningapp.backend.household

import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class HouseholdConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var householdService: HouseholdService

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var householdRepository: HouseholdRepository

    @Test
    fun `createHousehold cannot exceed three active households per user under concurrency`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")

        val results = runConcurrently(threadCount = 5) { index ->
            authenticatedAs(user.firebaseUid) {
                householdService.createHousehold(
                    HouseholdRegisterDTO(
                        name = "Household ${UUID.randomUUID()}-$index",
                    )
                )
            }
        }

        val activeMemberships = userHouseholdRepository
            .findAllByUserIdAndIsUserActiveTrue(user.id!!)

        assertThat(activeMemberships).hasSize(3)
        assertThat(successCount(results)).isEqualTo(3)
        assertThat(failureCount(results)).isEqualTo(2)
        assertAllFailuresOfType(results, BusinessConflictException::class.java)
    }

    @Test
    fun `deleteHousehold and updateHousehold keep final state consistent`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val originalName = household.name

        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val results = runConcurrently(threadCount = 2) { index ->
            authenticatedAs(user.firebaseUid) {
                if (index == 0) {
                    householdService.deleteHousehold(household.id!!)
                } else {
                    householdService.updateHousehold(
                        householdId = household.id!!,
                        newHousehold = HouseholdRegisterDTO(
                            name = "Updated household",
                        )
                    )
                }
            }
        }

        val savedHousehold = householdRepository.findById(household.id!!).orElseThrow()

        assertThat(savedHousehold.isActive).isFalse()

        if (failureCount(results) == 1) {
            assertThat(successCount(results)).isEqualTo(1)
            assertSingleFailureOfType(results, HouseholdNotActiveException::class.java)
            assertThat(savedHousehold.name).isEqualTo(originalName)
        } else {
            assertThat(failureCount(results)).isEqualTo(0)
            assertThat(successCount(results)).isEqualTo(2)
            assertThat(savedHousehold.name).isEqualTo("Updated household")
        }
    }
}

package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.household.HouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class UserHouseholdControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var householdRepository: HouseholdRepository

    @Test
    fun `join household should return membership dto and persist membership`() {
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

        val requestBody = """
            {
              "inviteCode": "JOIN1234"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.householdId").value(household.id.toString()))
            .andExpect(jsonPath("$.balance").value(0))
            .andExpect(jsonPath("$.isUserActive").value(true))

        val membership =
            userHouseholdRepository.findByUserIdAndHouseholdId(currentUser.id!!, household.id!!)
        assertThat(membership).isNotNull
        assertThat(membership?.isUserActive).isTrue()
    }

    @Test
    fun `join household should return 400 for invalid invite code body`() {
        createLocalUserForValidToken()

        val requestBody = """
            {
              "inviteCode": ""
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `leave household should return 204 and deactivate membership`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        val membership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
            balance = 40,
        )
        testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        mockMvc.perform(
            delete("/api/households/${household.id}/leave")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNoContent)

        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        assertThat(updatedMembership.isUserActive).isFalse()
        assertThat(updatedMembership.balance).isZero()
    }

    @Test
    fun `get my households should return only active memberships in active households`() {
        val currentUser = createLocalUserForValidToken()
        val activeHousehold = testDataFactory.createTestHousehold(createdBy = currentUser)
        val inactiveHousehold = testDataFactory.createTestHousehold(
            createdBy = currentUser,
            isActive = false,
        )

        testDataFactory.createTestMembership(
            user = currentUser,
            household = activeHousehold,
            isUserActive = true,
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = inactiveHousehold,
            isUserActive = true,
        )

        mockMvc.perform(
            get("/api/households/myHouseholds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].householdId").value(activeHousehold.id.toString()))

        val unchangedInactive = householdRepository.findById(inactiveHousehold.id!!).orElseThrow()
        assertThat(unchangedInactive.isActive).isFalse()
    }

    @Test
    fun `get household members should return only active users`() {
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

        mockMvc.perform(
            get("/api/households/${household.id}/members")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").exists())

        val result = userHouseholdRepository.findAllByHouseholdIdAndIsUserActiveTrue(household.id!!)
        assertThat(result.map { it.user.id })
            .containsExactlyInAnyOrder(currentUser.id, activeUser.id)
            .doesNotContain(inactiveUser.id)
    }

    @Test
    fun `remove user from household should return 204 and deactivate removed membership`() {
        val actor = createLocalUserForValidToken()
        val removedUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = actor)
        testDataFactory.createTestMembership(
            user = actor,
            household = household,
            isUserActive = true,
        )
        val removedMembership = testDataFactory.createTestMembership(
            user = removedUser,
            household = household,
            balance = 35,
            isUserActive = true,
        )

        mockMvc.perform(
            delete("/api/households/${household.id}/members/${removedUser.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNoContent)

        val updatedRemovedMembership =
            userHouseholdRepository.findById(removedMembership.id!!).orElseThrow()
        assertThat(updatedRemovedMembership.isUserActive).isFalse()
        assertThat(updatedRemovedMembership.balance).isZero()
    }
}

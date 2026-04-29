package com.cleaningapp.backend.household

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class HouseholdControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var householdRepository: HouseholdRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Test
    fun `create household should return 201 and create creator membership`() {
        val user = createLocalUserForValidToken(name = "Owner User")

        val requestBody = """
            {
              "name": "Kitchen Flat"
            }
        """.trimIndent()

        val response = mockMvc.perform(
            post("/api/households")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Kitchen Flat"))
            .andExpect(jsonPath("$.createdByUser").value(user.id.toString()))
            .andExpect(jsonPath("$.inviteCode").isString)
            .andReturn()

        val responseBody = response.response.contentAsString
        assertThat(responseBody).contains("Kitchen Flat")

        val savedHousehold = householdRepository.findAll().single()
        val membership =
            userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, savedHousehold.id!!)

        assertThat(savedHousehold.isActive).isTrue()
        assertThat(membership).isNotNull
        assertThat(membership?.isUserActive).isTrue()
    }

    @Test
    fun `create household should return 400 for invalid body`() {
        createLocalUserForValidToken()

        val requestBody = """
            {
              "name": ""
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `get household should return household for active member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            name = "Shared Home",
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(household.id.toString()))
            .andExpect(jsonPath("$.name").value("Shared Home"))
    }

    @Test
    fun `update household should return updated household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            name = "Old Name",
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val requestBody = """
            {
              "name": "New Name"
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/households/${household.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(household.id.toString()))
            .andExpect(jsonPath("$.name").value("New Name"))

        val updatedHousehold = householdRepository.findById(household.id!!).orElseThrow()
        assertThat(updatedHousehold.name).isEqualTo("New Name")
    }

    @Test
    fun `delete household should return 204 and deactivate household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 25,
        )

        mockMvc.perform(
            delete("/api/households/${household.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNoContent)

        val updatedHousehold = householdRepository.findById(household.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        assertThat(updatedHousehold.isActive).isFalse()
        assertThat(updatedMembership.isUserActive).isFalse()
        assertThat(updatedMembership.balance).isZero()
    }
}

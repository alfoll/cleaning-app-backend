package com.cleaningapp.backend.contract

import com.cleaningapp.backend.base.BaseIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ApiValidationContractIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `validation contract should return 400 for blank required string field`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val requestBody = """
            {
              "title": "",
              "description": "Valid description",
              "reward": 20
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `validation contract should return 400 for numeric range violation`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val requestBody = """
            {
              "title": "Valid privilege",
              "description": "Valid description",
              "cost": 501
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/privileges")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `validation contract should return 400 for max length violation`() {
        createLocalUserForValidToken()

        val longAvatarUrl = "a".repeat(501)

        val requestBody = """
            {
              "name": "Valid Name",
              "avatarUrl": "$longAvatarUrl"
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `validation contract should return 400 for task reward below minimum`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val requestBody = """
        {
          "title": "Valid task",
          "description": "Valid description",
          "reward": 4
        }
    """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `validation contract should return 400 for task reward above maximum`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val requestBody = """
        {
          "title": "Valid task",
          "description": "Valid description",
          "reward": 101
        }
    """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `validation contract should return 400 for too long description`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val longDescription = "a".repeat(2001)

        val requestBody = """
        {
          "title": "Valid task",
          "description": "$longDescription",
          "reward": 20
        }
    """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }
}

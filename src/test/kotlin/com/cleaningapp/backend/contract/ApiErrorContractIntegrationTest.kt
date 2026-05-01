package com.cleaningapp.backend.contract

import com.cleaningapp.backend.base.BaseIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class ApiErrorContractIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `security error should return 401 when token is missing`() {
        mockMvc.perform(
            get("/api/users/me")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Firebase token required"))
    }

    @Test
    fun `global error contract should include error message and time for validation error`() {
        createLocalUserForValidToken()

        val requestBody = """
            {
              "name": "",
              "avatarUrl": null
            }
        """.trimIndent()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.time").exists())
    }

    @Test
    fun `global error contract should include error message and time for malformed request`() {
        createLocalUserForValidToken()

        val malformedJson = """
            {
              "name": "Valid Name",
              "avatarUrl": "https://example.com/avatar.png",
        """.trimIndent()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.message").value("Request body is invalid or malformed"))
            .andExpect(jsonPath("$.time").exists())
    }

    @Test
    fun `global error contract should include error message and time for invalid parameter`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/households/not-a-uuid/leaderboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 INVALID_PARAMETER"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.time").exists())
    }

    @Test
    fun `global error contract should include error message and time for not found error`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/tasks/${UUID.randomUUID()}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 TASK_NOT_FOUND"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.time").exists())
    }

    @Test
    fun `global error contract should include error message and time for forbidden error`() {
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

        mockMvc.perform(
            get("/api/households/${household.id}/leaderboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("403 HOUSEHOLD_NOT_ACTIVE"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.time").exists())
    }

    @Test
    fun `global error contract should include error message and time for business conflict`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )

        val requestBody = """
            {
              "title": "Updated assigned task",
              "description": null,
              "reward": 35
            }
        """.trimIndent()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/tasks/${task.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.time").exists())
    }

}

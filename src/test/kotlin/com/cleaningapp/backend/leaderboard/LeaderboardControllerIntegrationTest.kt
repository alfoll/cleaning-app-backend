package com.cleaningapp.backend.leaderboard

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

class LeaderboardControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var clock: Clock

    private fun createTaskCompletionTransaction(
        household: HouseholdEntity,
        member: UserHouseholdEntity,
        amount: Int,
        createdAt: LocalDateTime,
    ) {
        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = member.user,
            completedBy = member,
            reward = amount,
        )

        testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = member,
            task = task,
            amount = amount,
            createdAt = createdAt,
        )
    }

    @Test
    fun `get leaderboard should return leaderboard for active household member`() {
        val currentUser = createLocalUserForValidToken(name = "Current User")
        val leaderUser = testDataFactory.createTestUser(name = "Leader User")
        val zeroUser = testDataFactory.createTestUser(name = "Zero User")

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)

        val currentMember = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        val leaderMember = testDataFactory.createTestMembership(
            user = leaderUser,
            household = household,
        )
        testDataFactory.createTestMembership(
            user = zeroUser,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)

        createTaskCompletionTransaction(
            household = household,
            member = leaderMember,
            amount = 100,
            createdAt = baseTime.minusDays(1),
        )
        createTaskCompletionTransaction(
            household = household,
            member = currentMember,
            amount = 50,
            createdAt = baseTime.minusDays(1),
        )

        mockMvc.perform(
            get("/api/households/${household.id}/leaderboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.periodDays").value(7))
            .andExpect(jsonPath("$.items.length()").value(3))

            .andExpect(jsonPath("$.items[0].place").value(1))
            .andExpect(jsonPath("$.items[0].userId").value(leaderUser.id.toString()))
            .andExpect(jsonPath("$.items[0].displayName").value("Leader User"))
            .andExpect(jsonPath("$.items[0].earnedCoins").value(100))
            .andExpect(jsonPath("$.items[0].earnedCoinsDelta").value(100))
            .andExpect(jsonPath("$.items[0].completedTasksCount").value(1))
            .andExpect(jsonPath("$.items[0].completedTasksDelta").value(1))
            .andExpect(jsonPath("$.items[0].isCurrentUser").value(false))

            .andExpect(jsonPath("$.items[1].place").value(2))
            .andExpect(jsonPath("$.items[1].userId").value(currentUser.id.toString()))
            .andExpect(jsonPath("$.items[1].displayName").value("Current User"))
            .andExpect(jsonPath("$.items[1].earnedCoins").value(50))
            .andExpect(jsonPath("$.items[1].earnedCoinsDelta").value(50))
            .andExpect(jsonPath("$.items[1].completedTasksCount").value(1))
            .andExpect(jsonPath("$.items[1].completedTasksDelta").value(1))
            .andExpect(jsonPath("$.items[1].isCurrentUser").value(true))

            .andExpect(jsonPath("$.items[2].place").value(3))
            .andExpect(jsonPath("$.items[2].userId").value(zeroUser.id.toString()))
            .andExpect(jsonPath("$.items[2].earnedCoins").value(0))
            .andExpect(jsonPath("$.items[2].earnedCoinsDelta").value(0))
            .andExpect(jsonPath("$.items[2].completedTasksCount").value(0))
            .andExpect(jsonPath("$.items[2].completedTasksDelta").value(0))
            .andExpect(jsonPath("$.items[2].isCurrentUser").value(false))
    }

    @Test
    fun `get leaderboard should return 404 for nonexistent household`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/households/${UUID.randomUUID()}/leaderboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 HOUSEHOLD_NOT_FOUND"))
    }

    @Test
    fun `get leaderboard should return 403 for inactive household`() {
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
    }

    @Test
    fun `get leaderboard should return 404 for non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/leaderboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 MEMBERSHIP_NOT_FOUND"))
    }

    @Test
    fun `get leaderboard should return 403 for inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/leaderboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("403 MEMBERSHIP_NOT_ACTIVE"))
    }

    @Test
    fun `get leaderboard should return 400 when household id is invalid`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/households/not-a-uuid/leaderboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 INVALID_PARAMETER"))
    }
}
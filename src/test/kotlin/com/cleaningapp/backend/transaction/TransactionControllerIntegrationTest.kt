package com.cleaningapp.backend.transaction

import com.cleaningapp.backend.base.BaseIntegrationTest
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

class TransactionControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `get my transactions should return current member transactions sorted by createdAt desc`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val otherUser = testDataFactory.createTestUser()
        val otherMember = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
            balance = 100,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
            reward = 20,
        )
        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        val olderTransaction = testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = member,
            task = task,
            amount = 20,
            createdAt = baseTime.minusDays(2),
        )
        val newerTransaction = testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = member,
            privilege = privilege,
            amount = -50,
            createdAt = baseTime.minusDays(1),
        )

        val otherTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = otherUser,
            completedBy = otherMember,
            reward = 15,
        )
        testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = otherMember,
            task = otherTask,
            amount = 15,
            createdAt = baseTime,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(newerTransaction.id.toString()))
            .andExpect(jsonPath("$[0].householdId").value(household.id.toString()))
            .andExpect(jsonPath("$[0].userId").value(user.id.toString()))
            .andExpect(jsonPath("$[0].amount").value(-50))
            .andExpect(jsonPath("$[0].type").value("PRIVILEGE_BOUGHT"))
            .andExpect(jsonPath("$[0].privilegeId").value(privilege.id.toString()))
            .andExpect(jsonPath("$[0].createdAt").exists())
            .andExpect(jsonPath("$[1].id").value(olderTransaction.id.toString()))
            .andExpect(jsonPath("$[1].amount").value(20))
            .andExpect(jsonPath("$[1].type").value("TASK_COMPLETION"))
            .andExpect(jsonPath("$[1].taskId").value(task.id.toString()))
            .andExpect(
                jsonPath(
                    "$[*].id",
                    containsInAnyOrder(
                        newerTransaction.id.toString(),
                        olderTransaction.id.toString(),
                    )
                )
            )
    }

    @Test
    fun `get my transactions should return at most 150 newest transactions`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val transactions = (1..151).map { index ->
            testDataFactory.createTestBalanceResetTransaction(
                household = household,
                member = member,
                amount = -index,
                createdAt = baseTime.plusMinutes(index.toLong()),
            )
        }

        mockMvc.perform(
            get("/api/households/${household.id}/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(150))
            .andExpect(jsonPath("$[0].id").value(transactions.last().id.toString()))
            .andExpect(jsonPath("$[149].id").value(transactions[1].id.toString()))
    }

    @Test
    fun `get my transactions should return empty list when current member has no transactions`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `get my transactions should return 403 for inactive household`() {
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
            get("/api/households/${household.id}/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("403 HOUSEHOLD_NOT_ACTIVE"))
    }

    @Test
    fun `get my transactions should return 404 for non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 MEMBERSHIP_NOT_FOUND"))
    }

    @Test
    fun `get my transactions should return 403 for inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("403 MEMBERSHIP_NOT_ACTIVE"))
    }

    @Test
    fun `get my transactions should return 400 when household id is invalid`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/households/not-a-uuid/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 INVALID_PARAMETER"))
    }

    @Test
    fun `get my transactions should return 404 when household does not exist`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/households/${UUID.randomUUID()}/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 HOUSEHOLD_NOT_FOUND"))
    }
}

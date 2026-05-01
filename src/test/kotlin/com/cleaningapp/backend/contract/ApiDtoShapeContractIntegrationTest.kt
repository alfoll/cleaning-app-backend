package com.cleaningapp.backend.contract

import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class ApiDtoShapeContractIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `task dto should expose user ids not membership ids for assigned task`() {
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
            reward = 20,
        )

        mockMvc.perform(
            get("/api/tasks/${task.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(task.id.toString()))
            .andExpect(jsonPath("$.householdId").value(household.id.toString()))
            .andExpect(jsonPath("$.createdBy").value(user.id.toString()))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.isAssigned").value(true))
            .andExpect(jsonPath("$.assignedTo").value(user.id.toString()))
            .andExpect(jsonPath("$.assignedTo").value(org.hamcrest.Matchers.not(membership.id.toString())))
            .andExpect(jsonPath("$.assignedAt").exists())
            .andExpect(jsonPath("$.isCompleted").value(false))
            .andExpect(jsonPath("$.completedBy").doesNotExist())
            .andExpect(jsonPath("$.completedAt").doesNotExist())
    }

    @Test
    fun `task dto should expose user ids not membership ids for completed task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            reward = 20,
        )

        mockMvc.perform(
            get("/api/tasks/${task.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(task.id.toString()))
            .andExpect(jsonPath("$.householdId").value(household.id.toString()))
            .andExpect(jsonPath("$.createdBy").value(user.id.toString()))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.isAssigned").value(false))
            .andExpect(jsonPath("$.assignedTo").doesNotExist())
            .andExpect(jsonPath("$.assignedAt").doesNotExist())
            .andExpect(jsonPath("$.isCompleted").value(true))
            .andExpect(jsonPath("$.completedBy").value(user.id.toString()))
            .andExpect(jsonPath("$.completedBy").value(org.hamcrest.Matchers.not(membership.id.toString())))
            .andExpect(jsonPath("$.completedAt").exists())
    }

    @Test
    fun `privilege dto should expose user id not membership id for bought privilege`() {
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

        mockMvc.perform(
            get("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(privilege.id.toString()))
            .andExpect(jsonPath("$.householdId").value(household.id.toString()))
            .andExpect(jsonPath("$.createdBy").value(user.id.toString()))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.isAvailable").value(false))
            .andExpect(jsonPath("$.boughtBy").value(user.id.toString()))
            .andExpect(jsonPath("$.boughtBy").value(org.hamcrest.Matchers.not(membership.id.toString())))
    }

    @Test
    fun `privilege dto should expose null boughtBy for available privilege`() {
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
            isAvailable = true,
            boughtBy = null,
        )

        mockMvc.perform(
            get("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(privilege.id.toString()))
            .andExpect(jsonPath("$.isAvailable").value(true))
            .andExpect(jsonPath("$.boughtBy").doesNotExist())
    }

    @Test
    fun `transaction dto should expose user id not membership id`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            reward = 20,
        )

        val transaction = testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = membership,
            task = task,
            amount = 20,
            createdAt = baseTime,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/transactions/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(transaction.id.toString()))
            .andExpect(jsonPath("$[0].householdId").value(household.id.toString()))
            .andExpect(jsonPath("$[0].userId").value(user.id.toString()))
            .andExpect(jsonPath("$[0].userId").value(org.hamcrest.Matchers.not(membership.id.toString())))
            .andExpect(jsonPath("$[0].taskId").value(task.id.toString()))
            .andExpect(jsonPath("$[0].privilegeId").doesNotExist())
            .andExpect(jsonPath("$[0].createdAt").exists())
    }

    @Test
    fun `activity dto should expose user id not membership id`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val activity = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_CREATED,
            title = "Task created",
            description = "User created a task",
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(activity.id.toString()))
            .andExpect(jsonPath("$[0].householdId").value(household.id.toString()))
            .andExpect(jsonPath("$[0].userId").value(user.id.toString()))
            .andExpect(jsonPath("$[0].userId").value(org.hamcrest.Matchers.not(membership.id.toString())))
            .andExpect(jsonPath("$[0].activityType").value("TASK_CREATED"))
            .andExpect(jsonPath("$[0].title").value("Task created"))
            .andExpect(jsonPath("$[0].description").value("User created a task"))
            .andExpect(jsonPath("$[0].createdAt").exists())
    }
}
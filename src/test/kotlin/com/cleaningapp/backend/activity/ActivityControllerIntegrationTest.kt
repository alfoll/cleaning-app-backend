package com.cleaningapp.backend.activity

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

class ActivityControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `get household activity should return all activity by default sorted by createdAt desc`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )
        val otherMembership = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        val otherHouseholdMembership = testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val olderActivity = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_CREATED,
            title = "Older activity",
            description = "Older description",
        )
        val newerActivity = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_COMPLETED,
            title = "Newer activity",
            description = "Newer description",
        )
        val otherMemberActivity = testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.PRIVILEGE_CREATED,
            title = "Other member activity",
            description = "Other member description",
        )
        testDataFactory.createTestActivity(
            household = otherHousehold,
            member = otherHouseholdMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other household activity",
            description = "Other household description",
        )

        testDataFactory.updateActivityCreatedAt(
            activityId = olderActivity.id!!,
            createdAt = baseTime.minusDays(2),
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = otherMemberActivity.id!!,
            createdAt = baseTime.minusDays(1),
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = newerActivity.id!!,
            createdAt = baseTime,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].id").value(newerActivity.id.toString()))
            .andExpect(jsonPath("$[0].householdId").value(household.id.toString()))
            .andExpect(jsonPath("$[0].userId").value(user.id.toString()))
            .andExpect(jsonPath("$[0].activityType").value("TASK_COMPLETED"))
            .andExpect(jsonPath("$[0].title").value("Newer activity"))
            .andExpect(jsonPath("$[0].description").value("Newer description"))
            .andExpect(jsonPath("$[0].createdAt").exists())
            .andExpect(jsonPath("$[1].id").value(otherMemberActivity.id.toString()))
            .andExpect(jsonPath("$[1].userId").value(otherUser.id.toString()))
            .andExpect(jsonPath("$[1].activityType").value("PRIVILEGE_CREATED"))
            .andExpect(jsonPath("$[2].id").value(olderActivity.id.toString()))
            .andExpect(jsonPath("$[2].activityType").value("TASK_CREATED"))
    }

    @Test
    fun `get household activity should return at most 150 newest activities`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val activities = (1..151).map { index ->
            val activity = testDataFactory.createTestActivity(
                household = household,
                member = membership,
                activityType = ActivityType.TASK_CREATED,
                title = "Activity $index",
            )

            testDataFactory.updateActivityCreatedAt(
                activityId = activity.id!!,
                createdAt = baseTime.plusMinutes(index.toLong()),
            )

            activity
        }

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(150))
            .andExpect(jsonPath("$[0].id").value(activities.last().id.toString()))
            .andExpect(jsonPath("$[149].id").value(activities[1].id.toString()))
    }

    @Test
    fun `get household activity should filter by activity type`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )
        val otherMembership = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        val taskCreatedActivity = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_CREATED,
            title = "Task created",
        )
        val otherMemberTaskCreatedActivity = testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other task created",
        )

        testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.PRIVILEGE_CREATED,
            title = "Privilege created",
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .param("activityType", "TASK_CREATED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].activityType").value("TASK_CREATED"))
            .andExpect(jsonPath("$[1].activityType").value("TASK_CREATED"))
            .andExpect(
                jsonPath(
                    "$[*].id",
                    containsInAnyOrder(
                        taskCreatedActivity.id.toString(),
                        otherMemberTaskCreatedActivity.id.toString(),
                    )
                )
            )
    }

    @Test
    fun `get household activity should filter by actor scope MY`() {
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
        val currentUserOtherMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = otherHousehold,
        )

        val myActivity = testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "My activity",
        )

        testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other activity",
        )
        testDataFactory.createTestActivity(
            household = otherHousehold,
            member = currentUserOtherMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "My other household activity",
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .param("actorScope", "MY")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(myActivity.id.toString()))
            .andExpect(jsonPath("$[0].userId").value(currentUser.id.toString()))
    }

    @Test
    fun `get household activity should filter by activity type and actor scope MY`() {
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

        val myCompletedActivity = testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.TASK_COMPLETED,
            title = "My completed task",
        )

        testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "My created task",
        )

        testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.TASK_COMPLETED,
            title = "Other completed task",
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .param("activityType", "TASK_COMPLETED")
                .param("actorScope", "MY")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(myCompletedActivity.id.toString()))
            .andExpect(jsonPath("$[0].activityType").value("TASK_COMPLETED"))
            .andExpect(jsonPath("$[0].userId").value(currentUser.id.toString()))
    }

    @Test
    fun `get household activity should return empty list when no activity exists`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `get household activity should return 400 for invalid activity type`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .param("activityType", "UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 INVALID_PARAMETER"))
    }

    @Test
    fun `get household activity should return 400 for invalid actor scope`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .param("actorScope", "UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 INVALID_PARAMETER"))
    }

    @Test
    fun `get household activity should return 404 for nonexistent household`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/households/${UUID.randomUUID()}/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 HOUSEHOLD_NOT_FOUND"))
    }

    @Test
    fun `get household activity should return 403 for inactive household`() {
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
            get("/api/households/${household.id}/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("403 HOUSEHOLD_NOT_ACTIVE"))
    }

    @Test
    fun `get household activity should return 404 for non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 MEMBERSHIP_NOT_FOUND"))
    }

    @Test
    fun `get household activity should return 403 for inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)

        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("403 MEMBERSHIP_NOT_ACTIVE"))
    }

    @Test
    fun `get household activity should return 400 when household id is invalid`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/households/not-a-uuid/activity")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 INVALID_PARAMETER"))
    }
}

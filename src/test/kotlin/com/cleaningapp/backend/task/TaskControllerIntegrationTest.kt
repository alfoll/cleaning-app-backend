package com.cleaningapp.backend.task

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TaskControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var clock: Clock

    enum class MissingTaskEndpoint {
        UPDATE,
        DELETE,
        ASSIGN,
        UNASSIGN,
        COMPLETE,
    }

    private fun endOfDay(date: LocalDate): LocalDateTime =
        date.atTime(23, 59, 59, 999_999_000)

    private fun requestForMissingTask(
        endpoint: MissingTaskEndpoint,
        taskId: UUID,
    ): MockHttpServletRequestBuilder =
        when (endpoint) {
            MissingTaskEndpoint.UPDATE -> put("/api/tasks/$taskId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Updated task",
                      "description": "Updated description",
                      "reward": 35
                    }
                    """.trimIndent()
                )

            MissingTaskEndpoint.DELETE -> delete("/api/tasks/$taskId")
            MissingTaskEndpoint.ASSIGN -> post("/api/tasks/$taskId/assign")
            MissingTaskEndpoint.UNASSIGN -> post("/api/tasks/$taskId/unassign")
            MissingTaskEndpoint.COMPLETE -> post("/api/tasks/$taskId/complete")
        }

    @Test
    fun `create task should return 201 and persist task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val requestBody = """
            {
              "title": "Clean kitchen",
              "description": "Clean table and floor",
              "reward": 20
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.householdId").value(household.id.toString()))
            .andExpect(jsonPath("$.createdBy").value(user.id.toString()))
            .andExpect(jsonPath("$.title").value("Clean kitchen"))
            .andExpect(jsonPath("$.description").value("Clean table and floor"))
            .andExpect(jsonPath("$.reward").value(20))
            .andExpect(jsonPath("$.isAssigned").value(false))
            .andExpect(jsonPath("$.isCompleted").value(false))

        val tasks = taskRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(tasks).hasSize(1)
        assertThat(tasks.first().title).isEqualTo("Clean kitchen")
        assertThat(tasks.first().createdBy.id).isEqualTo(user.id)

        assertThat(activities.map { it.activityType }).contains(ActivityType.TASK_CREATED)
        assertThat(activities.first { it.activityType == ActivityType.TASK_CREATED }.member.id)
            .isEqualTo(membership.id)
    }

    @Test
    fun `create task should accept and return normalized due date`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val dueDate = LocalDate.now(clock).plusDays(2)

        val requestBody = """
            {
              "title": "Deadline task",
              "reward": 20,
              "dueAt": "${dueDate}T12:30:00"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.dueAt").value("${dueDate}T23:59:59.999999"))
    }

    @Test
    fun `create task should reject due date before today`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val dueDate = LocalDate.now(clock).minusDays(1)

        val requestBody = """
            {
              "title": "Past deadline task",
              "reward": 20,
              "dueAt": "${dueDate}T23:59:00"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 ILLEGAL_ARGUMENT"))
            .andExpect(jsonPath("$.message").value("Task due date cannot be in the past"))
    }

    @Test
    fun `create task should return 400 when request body is invalid`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val requestBody = """
            {
              "title": "",
              "description": "Invalid title",
              "reward": 3
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
    fun `get task by id should return task for active household member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
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
            .andExpect(jsonPath("$.title").value(task.title))
            .andExpect(jsonPath("$.reward").value(20))
    }

    @Test
    fun `get household tasks should return filtered free tasks`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val currentMembership = testDataFactory.createTestMembership(user = user, household = household)
        val otherMembership = testDataFactory.createTestMembership(user = otherUser, household = household)

        val freeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )
        testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = currentMembership,
        )
        testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = otherUser,
            assignedTo = otherMembership,
        )
        testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = currentMembership,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/tasks")
                .param("filter", "FREE")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(freeTask.id.toString()))
            .andExpect(jsonPath("$[0].isAssigned").value(false))
            .andExpect(jsonPath("$[0].isCompleted").value(false))
    }

    @Test
    fun `get household tasks FREE should return at most 150 newest tasks`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val tasks = (1..151).map { index ->
            val task = testDataFactory.createTestFreeTask(
                household = household,
                createdBy = user,
                reward = 20,
            )

            testDataFactory.updateTaskTimestamps(
                taskId = task.id!!,
                createdAt = baseTime.plusMinutes(index.toLong()),
            )

            task
        }

        mockMvc.perform(
            get("/api/households/${household.id}/tasks")
                .param("filter", "FREE")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(150))
            .andExpect(jsonPath("$[0].id").value(tasks.last().id.toString()))
            .andExpect(jsonPath("$[149].id").value(tasks[1].id.toString()))
    }

    @Test
    fun `get household tasks should return 400 for invalid filter`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        mockMvc.perform(
            get("/api/households/${household.id}/tasks")
                .param("filter", "UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 INVALID_PARAMETER"))
    }

    @Test
    fun `update task should return updated task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        val requestBody = """
            {
              "title": "Updated task",
              "description": "Updated description",
              "reward": 35
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/tasks/${task.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(task.id.toString()))
            .andExpect(jsonPath("$.title").value("Updated task"))
            .andExpect(jsonPath("$.description").value("Updated description"))
            .andExpect(jsonPath("$.reward").value(35))

        val updatedTask = taskRepository.findById(task.id!!).orElseThrow()

        assertThat(updatedTask.title).isEqualTo("Updated task")
        assertThat(updatedTask.description).isEqualTo("Updated description")
        assertThat(updatedTask.reward).isEqualTo(35)
    }

    @Test
    fun `update task should return 409 when task is assigned`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

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
            put("/api/tasks/${task.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `delete task should return 204 and remove free task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        mockMvc.perform(
            delete("/api/tasks/${task.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNoContent)

        assertThat(taskRepository.findById(task.id!!)).isEmpty
    }

    @Test
    fun `assign task should return assigned task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        mockMvc.perform(
            post("/api/tasks/${task.id}/assign")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(task.id.toString()))
            .andExpect(jsonPath("$.isAssigned").value(true))
            .andExpect(jsonPath("$.assignedTo").value(user.id.toString()))
            .andExpect(jsonPath("$.assignedAt").exists())

        val updatedTask = taskRepository.findById(task.id!!).orElseThrow()
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(updatedTask.assignedTo?.id).isEqualTo(membership.id)
        assertThat(updatedTask.assignedAt).isNotNull()
        assertThat(activities.map { it.activityType }).contains(ActivityType.TASK_ASSIGNED)
    }

    @Test
    fun `assign task should return 409 when task is already assigned`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )

        mockMvc.perform(
            post("/api/tasks/${task.id}/assign")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `unassign task should return unassigned task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )

        mockMvc.perform(
            post("/api/tasks/${task.id}/unassign")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(task.id.toString()))
            .andExpect(jsonPath("$.isAssigned").value(false))
            .andExpect(jsonPath("$.assignedTo").doesNotExist())
            .andExpect(jsonPath("$.assignedAt").doesNotExist())

        val updatedTask = taskRepository.findById(task.id!!).orElseThrow()
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(updatedTask.assignedTo).isNull()
        assertThat(updatedTask.assignedAt).isNull()
        assertThat(activities.map { it.activityType }).contains(ActivityType.TASK_UNASSIGNED)
    }

    @Test
    fun `unassign task should return 409 when task is assigned to another member`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        testDataFactory.createTestMembership(user = currentUser, household = household)
        val otherMembership = testDataFactory.createTestMembership(user = otherUser, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = otherMembership,
        )

        mockMvc.perform(
            post("/api/tasks/${task.id}/unassign")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `complete task should return completed task and reward member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 5,
        )

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 25,
        )

        mockMvc.perform(
            post("/api/tasks/${task.id}/complete")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(task.id.toString()))
            .andExpect(jsonPath("$.isCompleted").value(true))
            .andExpect(jsonPath("$.completedBy").value(user.id.toString()))
            .andExpect(jsonPath("$.completedAt").exists())
            .andExpect(jsonPath("$.isAssigned").value(false))
            .andExpect(jsonPath("$.assignedTo").doesNotExist())
            .andExpect(jsonPath("$.assignedAt").doesNotExist())

        val updatedTask = taskRepository.findById(task.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val transactions = transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
            household.id!!,
            membership.id!!,
        )
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(updatedTask.isCompleted).isTrue()
        assertThat(updatedTask.completedBy?.id).isEqualTo(membership.id)
        assertThat(updatedTask.assignedTo).isNull()

        assertThat(updatedMembership.balance).isEqualTo(30)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.TASK_COMPLETION)
        assertThat(transactions.first().amount).isEqualTo(25)

        assertThat(activities.map { it.activityType }).contains(ActivityType.TASK_COMPLETED)
    }

    @Test
    fun `complete task should return 409 when task is unassigned`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        mockMvc.perform(
            post("/api/tasks/${task.id}/complete")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `update task should return 400 when request body is invalid`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        val requestBody = """
        {
          "title": "",
          "description": "Invalid title",
          "reward": 3
        }
    """.trimIndent()

        mockMvc.perform(
            put("/api/tasks/${task.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `get household tasks WITH_DEADLINE should return unfinished deadlines sorted ascending`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)
        val otherMembership = testDataFactory.createTestMembership(user = otherUser, household = household)
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = otherHousehold)
        val now = LocalDateTime.now(clock)

        val overdueFree = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = now.minusDays(2),
        )
        val overdueAssigned = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = otherUser,
            assignedTo = otherMembership,
            dueAt = now.minusDays(1),
        )
        val today = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = endOfDay(LocalDate.now(clock)),
        )
        val future = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            dueAt = now.plusDays(2),
        )
        testDataFactory.createTestFreeTask(household = household, createdBy = user)
        testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            dueAt = now.minusDays(3),
        )
        testDataFactory.createTestFreeTask(
            household = otherHousehold,
            createdBy = user,
            dueAt = now.minusDays(4),
        )

        mockMvc.perform(
            get("/api/households/${household.id}/tasks")
                .param("filter", "WITH_DEADLINE")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[0].id").value(overdueFree.id.toString()))
            .andExpect(jsonPath("$[0].isOverdue").value(true))
            .andExpect(jsonPath("$[1].id").value(overdueAssigned.id.toString()))
            .andExpect(jsonPath("$[1].isOverdue").value(true))
            .andExpect(jsonPath("$[2].id").value(today.id.toString()))
            .andExpect(jsonPath("$[2].isOverdue").value(false))
            .andExpect(jsonPath("$[3].id").value(future.id.toString()))
            .andExpect(jsonPath("$[3].isOverdue").value(false))
    }

    @Test
    fun `get household tasks OVERDUE should return unfinished expired deadlines sorted ascending`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)
        val otherMembership = testDataFactory.createTestMembership(user = otherUser, household = household)
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = otherHousehold)
        val now = LocalDateTime.now(clock)

        val overdueFree = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = now.minusDays(3),
        )
        val overdueAssigned = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = otherUser,
            assignedTo = otherMembership,
            dueAt = now.minusDays(1),
        )
        testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = endOfDay(LocalDate.now(clock)),
        )
        testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = now.plusDays(1),
        )
        testDataFactory.createTestFreeTask(household = household, createdBy = user)
        testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            dueAt = now.minusDays(4),
        )
        testDataFactory.createTestFreeTask(
            household = otherHousehold,
            createdBy = user,
            dueAt = now.minusDays(5),
        )

        mockMvc.perform(
            get("/api/households/${household.id}/tasks")
                .param("filter", "OVERDUE")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(overdueFree.id.toString()))
            .andExpect(jsonPath("$[0].isOverdue").value(true))
            .andExpect(jsonPath("$[1].id").value(overdueAssigned.id.toString()))
            .andExpect(jsonPath("$[1].isOverdue").value(true))
    }

    @Test
    fun `get household tasks should use ALL filter by default`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val freeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )
        val assignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )
        val completedTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(
                jsonPath(
                    "$[*].id",
                    containsInAnyOrder(
                        freeTask.id.toString(),
                        assignedTask.id.toString(),
                        completedTask.id.toString(),
                    )
                )
            )
    }

    @Test
    fun `get task by id should return 404 when task does not exist`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/tasks/${UUID.randomUUID()}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 TASK_NOT_FOUND"))
    }

    @ParameterizedTest
    @EnumSource(MissingTaskEndpoint::class)
    fun `task endpoint should return 404 when task does not exist`(endpoint: MissingTaskEndpoint) {
        createLocalUserForValidToken()

        mockMvc.perform(
            requestForMissingTask(endpoint, UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 TASK_NOT_FOUND"))
    }
}

package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.task.TaskDueDatePolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.LocalDate

class TaskPlanControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `create task endpoint should accept recurrence and expose plan fields`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val dueAt = LocalDate.now(clock).plusDays(2).atTime(10, 30)

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Recurring task",
                      "description": "Every day",
                      "reward": 25,
                      "dueAt": "$dueAt",
                      "recurrenceType": "DAILY"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.taskPlanId").exists())
            .andExpect(jsonPath("$.recurrenceType").value("DAILY"))
            .andExpect(jsonPath("$.recurrenceActive").value(true))
            .andExpect(jsonPath("$.dueAt").value(TaskDueDatePolicy.endOfDay(dueAt.toLocalDate()).toString()))
    }

    @Test
    fun `create recurring task endpoint should reject missing due date`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        mockMvc.perform(
            post("/api/households/${household.id}/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Recurring task","reward":25,"recurrenceType":"WEEKLY"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `update task endpoint should not change recurrence type`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val dueAt = TaskDueDatePolicy.endOfDay(LocalDate.now(clock).plusDays(2))
        val taskPlan = testDataFactory.createTestTaskPlan(
            household = household,
            createdBy = user,
            recurrenceType = RecurrenceType.DAILY,
        )
        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = dueAt,
            taskPlan = taskPlan,
        )

        mockMvc.perform(
            put("/api/tasks/${task.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Changed instance",
                      "reward": 30,
                      "dueAt": null,
                      "recurrenceType": "MONTHLY"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recurrenceType").value("DAILY"))
            .andExpect(jsonPath("$.dueAt").value(dueAt.toString()))

        assertThat(taskPlanRepository.findById(taskPlan.id!!).orElseThrow().recurrenceType)
            .isEqualTo(RecurrenceType.DAILY)
    }
}

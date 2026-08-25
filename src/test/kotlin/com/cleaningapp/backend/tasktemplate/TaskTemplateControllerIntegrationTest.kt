package com.cleaningapp.backend.tasktemplate

import com.cleaningapp.backend.base.BaseIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
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
import java.time.Clock
import java.time.LocalDateTime

class TaskTemplateControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var taskTemplateRepository: TaskTemplateRepository

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `create template should return 201 and backend-owned fields`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        mockMvc.perform(
            post("/api/households/${household.id}/task-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Wash windows",
                      "description": "All rooms",
                      "reward": 35,
                      "createdBy": "00000000-0000-0000-0000-000000000000",
                      "householdId": "00000000-0000-0000-0000-000000000000",
                      "isActive": false
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("Wash windows"))
            .andExpect(jsonPath("$.description").value("All rooms"))
            .andExpect(jsonPath("$.reward").value(35))
            .andExpect(jsonPath("$.createdBy").value(user.id.toString()))
            .andExpect(jsonPath("$.householdId").value(household.id.toString()))
            .andExpect(jsonPath("$.createdAt").exists())

        val templates = taskTemplateRepository.findAll()
        assertThat(templates).hasSize(1)
        assertThat(templates.first().isActive).isTrue()
    }

    @Test
    fun `create template should validate title description and reward`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val tooLongDescription = "x".repeat(2001)

        val invalidRequests = listOf(
            """{"title":" ","reward":20}""",
            """{"title":"A","reward":20}""",
            """{"title":"Valid title","description":"$tooLongDescription","reward":20}""",
            """{"title":"Valid title","reward":4}""",
            """{"title":"Valid title","reward":101}""",
        )

        invalidRequests.forEach { requestBody ->
            mockMvc.perform(
                post("/api/households/${household.id}/task-templates")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
        }
    }

    @Test
    fun `create template should return 404 for user without membership`() {
        createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold()

        mockMvc.perform(
            post("/api/households/${household.id}/task-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Wash windows","reward":35}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 MEMBERSHIP_NOT_FOUND"))
    }

    @Test
    fun `get templates should return only active household templates sorted newest first`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val otherHousehold = testDataFactory.createTestHousehold()
        val now = LocalDateTime.now(clock)
        val older = testDataFactory.createTestTaskTemplate(household, user, title = "Older")
        val newer = testDataFactory.createTestTaskTemplate(household, user, title = "Newer")
        testDataFactory.createTestTaskTemplate(household, user, title = "Inactive", isActive = false)
        testDataFactory.createTestTaskTemplate(otherHousehold, title = "Other")
        testDataFactory.updateTaskTemplateCreatedAt(older.id!!, now.minusDays(2))
        testDataFactory.updateTaskTemplateCreatedAt(newer.id!!, now.minusDays(1))

        mockMvc.perform(
            get("/api/households/${household.id}/task-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].id", contains(newer.id.toString(), older.id.toString())))
    }

    @Test
    fun `update template should return updated template for creator`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val template = testDataFactory.createTestTaskTemplate(household, user)

        mockMvc.perform(
            put("/api/task-templates/${template.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Updated template","description":null,"reward":45}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Updated template"))
            .andExpect(jsonPath("$.description").doesNotExist())
            .andExpect(jsonPath("$.reward").value(45))
    }

    @Test
    fun `delete template should return 204 and hide soft-deleted row`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val template = testDataFactory.createTestTaskTemplate(household, user)

        mockMvc.perform(
            delete("/api/task-templates/${template.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/households/${household.id}/task-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        assertThat(taskTemplateRepository.findById(template.id!!).orElseThrow().isActive).isFalse()
    }
}

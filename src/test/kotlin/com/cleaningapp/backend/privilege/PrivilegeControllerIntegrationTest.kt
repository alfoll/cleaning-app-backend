package com.cleaningapp.backend.privilege

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
import java.util.UUID

class PrivilegeControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var privilegeRepository: PrivilegeRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    enum class MissingPrivilegeEndpoint {
        UPDATE,
        DELETE,
        BUY,
    }

    private fun requestForMissingPrivilege(
        endpoint: MissingPrivilegeEndpoint,
        privilegeId: UUID,
    ): MockHttpServletRequestBuilder =
        when (endpoint) {
            MissingPrivilegeEndpoint.UPDATE -> put("/api/privileges/$privilegeId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Updated privilege",
                      "description": "Updated description",
                      "cost": 40
                    }
                    """.trimIndent()
                )

            MissingPrivilegeEndpoint.DELETE -> delete("/api/privileges/$privilegeId")

            MissingPrivilegeEndpoint.BUY -> post("/api/privileges/$privilegeId/buy")
        }

    @Test
    fun `create privilege should return 201 and persist privilege`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val requestBody = """
            {
              "title": "Choose movie",
              "description": "Buyer chooses movie for the evening",
              "cost": 50
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/${household.id}/privileges")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.householdId").value(household.id.toString()))
            .andExpect(jsonPath("$.createdBy").value(user.id.toString()))
            .andExpect(jsonPath("$.title").value("Choose movie"))
            .andExpect(jsonPath("$.description").value("Buyer chooses movie for the evening"))
            .andExpect(jsonPath("$.cost").value(50))
            .andExpect(jsonPath("$.isAvailable").value(true))

        val privileges = privilegeRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(privileges).hasSize(1)
        assertThat(privileges.first().title).isEqualTo("Choose movie")
        assertThat(privileges.first().createdBy.id).isEqualTo(user.id)

        assertThat(activities.map { it.activityType }).contains(ActivityType.PRIVILEGE_CREATED)
        assertThat(activities.first { it.activityType == ActivityType.PRIVILEGE_CREATED }.member.id)
            .isEqualTo(membership.id)
    }

    @Test
    fun `create privilege should return 400 when request body is invalid`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val requestBody = """
            {
              "title": "",
              "description": "Invalid title and cost",
              "cost": 3
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
    fun `create privilege should return 404 when household does not exist`() {
        createLocalUserForValidToken()

        val requestBody = """
            {
              "title": "Choose movie",
              "description": "Buyer chooses movie for the evening",
              "cost": 50
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/households/${UUID.randomUUID()}/privileges")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 HOUSEHOLD_NOT_FOUND"))
    }

    @Test
    fun `get privilege by id should return privilege for active household member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Choose dinner",
            cost = 50,
        )

        mockMvc.perform(
            get("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(privilege.id.toString()))
            .andExpect(jsonPath("$.householdId").value(household.id.toString()))
            .andExpect(jsonPath("$.createdBy").value(user.id.toString()))
            .andExpect(jsonPath("$.title").value("Choose dinner"))
            .andExpect(jsonPath("$.cost").value(50))
            .andExpect(jsonPath("$.isAvailable").value(true))
    }

    @Test
    fun `get household privileges should return filtered available privileges`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val availablePrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Available privilege",
            cost = 30,
        )

        testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Bought privilege",
            cost = 40,
            isAvailable = false,
            boughtBy = membership,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/privileges")
                .param("filter", "AVAILABLE")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(availablePrivilege.id.toString()))
            .andExpect(jsonPath("$[0].isAvailable").value(true))
    }

    @Test
    fun `get household privileges should use ALL filter by default`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val availablePrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 30,
        )
        val boughtPrivilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 40,
            isAvailable = false,
            boughtBy = membership,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/privileges")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(
                jsonPath(
                    "$[*].id",
                    containsInAnyOrder(
                        availablePrivilege.id.toString(),
                        boughtPrivilege.id.toString(),
                    )
                )
            )
    }

    @Test
    fun `get household privileges should return 400 for invalid filter`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        mockMvc.perform(
            get("/api/households/${household.id}/privileges")
                .param("filter", "UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 INVALID_PARAMETER"))
    }

    @Test
    fun `get household privileges should return 404 when household does not exist`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/households/${UUID.randomUUID()}/privileges")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 HOUSEHOLD_NOT_FOUND"))
    }

    @Test
    fun `update privilege should return updated privilege`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            title = "Old privilege",
            cost = 30,
        )

        val requestBody = """
            {
              "title": "Updated privilege",
              "description": "Updated description",
              "cost": 60
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(privilege.id.toString()))
            .andExpect(jsonPath("$.title").value("Updated privilege"))
            .andExpect(jsonPath("$.description").value("Updated description"))
            .andExpect(jsonPath("$.cost").value(60))

        val updatedPrivilege = privilegeRepository.findById(privilege.id!!).orElseThrow()

        assertThat(updatedPrivilege.title).isEqualTo("Updated privilege")
        assertThat(updatedPrivilege.description).isEqualTo("Updated description")
        assertThat(updatedPrivilege.cost).isEqualTo(60)
    }

    @Test
    fun `update privilege should return 400 when request body is invalid`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
        )

        val requestBody = """
            {
              "title": "",
              "description": "Invalid title and cost",
              "cost": 3
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("400 VALIDATION_ERROR"))
    }

    @Test
    fun `update privilege should return 409 when privilege is bought`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            isAvailable = false,
            boughtBy = membership,
        )

        val requestBody = """
            {
              "title": "Updated bought privilege",
              "description": null,
              "cost": 60
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `update privilege should return 409 when user is not creator`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(
            user = creator,
            household = household,
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = creator,
            cost = 50,
        )

        val requestBody = """
            {
              "title": "Updated foreign privilege",
              "description": "Updated description",
              "cost": 60
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `delete privilege should return 204 and remove available privilege`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
        )

        mockMvc.perform(
            delete("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNoContent)

        assertThat(privilegeRepository.findById(privilege.id!!)).isEmpty
    }

    @Test
    fun `delete privilege should return 409 when privilege is bought`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            isAvailable = false,
            boughtBy = membership,
        )

        mockMvc.perform(
            delete("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `delete privilege should return 409 when user is not creator`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(
            user = creator,
            household = household,
        )
        testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = creator,
            cost = 50,
        )

        mockMvc.perform(
            delete("/api/privileges/${privilege.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `buy privilege should return bought privilege decrease balance create transaction and activity`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 80,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
        )

        mockMvc.perform(
            post("/api/privileges/${privilege.id}/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(privilege.id.toString()))
            .andExpect(jsonPath("$.isAvailable").value(false))
            .andExpect(jsonPath("$.boughtBy").value(user.id.toString()))

        val updatedPrivilege = privilegeRepository.findById(privilege.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val transactions = transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
            household.id!!,
            membership.id!!,
        )
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(updatedPrivilege.isAvailable).isFalse()
        assertThat(updatedPrivilege.boughtBy?.id).isEqualTo(membership.id)
        assertThat(updatedMembership.balance).isEqualTo(30)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.PRIVILEGE_BOUGHT)
        assertThat(transactions.first().amount).isEqualTo(-50)

        assertThat(activities.map { it.activityType }).contains(ActivityType.PRIVILEGE_BOUGHT)
    }

    @Test
    fun `buy privilege should return 409 when balance is insufficient`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 20,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
        )

        mockMvc.perform(
            post("/api/privileges/${privilege.id}/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `buy privilege should return 409 when privilege is already bought`() {
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
            post("/api/privileges/${privilege.id}/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 BUSINESS_CONFLICT"))
    }

    @Test
    fun `get privilege by id should return 404 when privilege does not exist`() {
        createLocalUserForValidToken()

        mockMvc.perform(
            get("/api/privileges/${UUID.randomUUID()}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 PRIVILEGE_NOT_FOUND"))
    }

    @ParameterizedTest
    @EnumSource(MissingPrivilegeEndpoint::class)
    fun `privilege endpoint should return 404 when privilege does not exist`(
        endpoint: MissingPrivilegeEndpoint,
    ) {
        createLocalUserForValidToken()

        mockMvc.perform(
            requestForMissingPrivilege(endpoint, UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("404 PRIVILEGE_NOT_FOUND"))
    }
}

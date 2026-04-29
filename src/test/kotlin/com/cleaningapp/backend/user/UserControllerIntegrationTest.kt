package com.cleaningapp.backend.user

import com.cleaningapp.backend.base.BaseIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


class UserControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `get profile should return current user`() {
        val user = createLocalUserForValidToken(
            name = "Profile User",
            isActive = true,
        )

        mockMvc.perform(
            get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(user.id.toString()))
            .andExpect(jsonPath("$.firebaseUid").value(defaultFirebaseUid))
            .andExpect(jsonPath("$.email").value(defaultFirebaseEmail))
            .andExpect(jsonPath("$.name").value("Profile User"))
            .andExpect(jsonPath("$.createdAt").exists())
    }

    @Test
    fun `update profile should update name and avatar`() {
        val user = createLocalUserForValidToken(
            name = "Old Name",
            isActive = true,
        )

        val requestBody = """
            {
              "name": "New Name",
              "avatarUrl": "https://example.com/new-avatar.png"
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New Name"))
            .andExpect(jsonPath("$.avatarUrl").value("https://example.com/new-avatar.png"))

        val updatedUser = userRepository.findById(user.id!!).orElseThrow()
        assertThat(updatedUser.name).isEqualTo("New Name")
        assertThat(updatedUser.avatarUrl).isEqualTo("https://example.com/new-avatar.png")
    }

    @Test
    fun `update profile should return 400 for when name is blank`() {
        createLocalUserForValidToken()

        val requestBody = """
            {
              "name": "",
              "avatarUrl": null
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
    fun `sync email from firebase should update local email`() {
        createLocalUserForValidToken(
            name = "Email Sync User",
            isActive = true,
        )

        mockFirebaseUser(
            firebaseUid = defaultFirebaseUid,
            email = "new-email@test.com",
        )

        mockMvc.perform(
            put("/api/users/me/email/sync")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("new-email@test.com"))

        val updatedUser = userRepository.findUserByFirebaseUid(defaultFirebaseUid)
        assertThat(updatedUser?.email).isEqualTo("new-email@test.com")
    }

    @Test
    fun `sync email from firebase should return 409 when email is already used`() {
        createLocalUserForValidToken()

        testDataFactory.createTestUser(
            firebaseUid = "another-firebase-user",
            email = "used@test.com",
            name = "Another User",
        )

        mockFirebaseUser(
            firebaseUid = defaultFirebaseUid,
            email = "used@test.com",
        )

        mockMvc.perform(
            put("/api/users/me/email/sync")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("409 EMAIL_ALREADY_USED"))
    }

    @Test
    fun `delete profile should return 204 and deactivate user`() {
        val user = createLocalUserForValidToken(
            name = "Delete User",
            isActive = true,
        )

        mockMvc.perform(
            delete("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isNoContent)

        val updatedUser = userRepository.findById(user.id!!).orElseThrow()
        assertThat(updatedUser.isActive).isFalse()
    }
}

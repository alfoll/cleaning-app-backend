package com.cleaningapp.backend.security

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


// проверяет регистрауцию через fb токен
// filter достает firebaseUid
// AuthController берет principal.username
// UserService.createUser(firebaseUid, dto)
// user сохраняется в бд
class AuthControllerIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `register should create user using firebase uid from token`() {
        val requestBody = """
            {
              "name": "Test User",
              "email": "$defaultFirebaseEmail",
              "avatarUrl": null
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.firebaseUid").value(defaultFirebaseUid))
            .andExpect(jsonPath("$.email").value(defaultFirebaseEmail))
            .andExpect(jsonPath("$.name").value("Test User"))

        val savedUser = userRepository.findUserByFirebaseUid(defaultFirebaseUid)

        assertThat(savedUser).isNotNull
        assertThat(savedUser?.email).isEqualTo(defaultFirebaseEmail)
        assertThat(savedUser?.name).isEqualTo("Test User")
        assertThat(savedUser?.isActive).isTrue()
    }

    @Test
    fun `register should return 401 when authorization header is missing`() {
        val requestBody = """
            {
              "name": "Test User",
              "email": "$defaultFirebaseEmail",
              "avatarUrl": null
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Firebase token required"))
    }

    @Test
    fun `register should return 400 when request body is invalid`() {
        val requestBody = """
            {
              "name": "",
              "email": "bad-email",
              "avatarUrl": null
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
    }
}
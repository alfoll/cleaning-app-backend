package com.cleaningapp.backend.security

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.google.firebase.auth.FirebaseAuthException
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// тестирует моккирование fb компонентов
// проверяет:
//      обязательность токена,
//      ветку невалидного токена,
//      ветку валидного токена с отсутствующим юзером,
//      деактивированный пользователь отбрасывается на уровне фильтра,
//      активный юзер пропускается - полный путь
class FirebaseAuthFilterIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `protected endpoint should return 401 when authorization header is missing`() {
        mockMvc.perform(
            get("/api/users/me")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Firebase token required"))
    }

    @Test
    fun `protected endpoint should return 400 when token format is invalid`() {
        Mockito.`when`(firebaseAuthService.verifyToken("bad-token"))
            .thenThrow(IllegalArgumentException("Invalid token format"))

        mockMvc.perform(
            get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Invalid token format"))
    }

    @Test
    fun `protected endpoint should return 401 when firebase token is valid but user does not exist in database`() {
        mockMvc.perform(
            get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoint should return 403 when user is deactivated`() {
        testDataFactory.createTestUser(
            firebaseUid = defaultFirebaseUid,
            email = defaultFirebaseEmail,
            isActive = false,
        )

        mockMvc.perform(
            get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("User account is deactivated"))
    }

    // полный путь пропускаемого юзера:
    //      FirebaseAuthFilter,
    //      firebaseAuthService.verifyToken(validToken),
    //      firebaseUid,
    //      userRepository.findUserByFirebaseUid(firebaseUid),
    //      SecurityContext,
    //      UserController.getProfile()
    @Test
    fun `protected endpoint should return 200 when firebase token is valid and active user exists`() {
        testDataFactory.createTestUser(
            firebaseUid = defaultFirebaseUid,
            email = defaultFirebaseEmail,
            name = "Test User",
            isActive = true,
        )

        mockMvc.perform(
            get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.firebaseUid").value(defaultFirebaseUid))
            .andExpect(jsonPath("$.email").value(defaultFirebaseEmail))
    }

    @Test
    fun `protected endpoint should return 401 when firebase rejects token`() {
        val exception = Mockito.mock(FirebaseAuthException::class.java)

        Mockito.doAnswer {
            throw exception
        }.`when`(firebaseAuthService).verifyToken("rejected-token")

        mockMvc.perform(
            get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer rejected-token")
        )
            .andExpect(status().isUnauthorized)
    }
}
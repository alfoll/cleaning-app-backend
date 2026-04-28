package com.cleaningapp.backend.base

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.Instant


// инфраструктурные тесты
// активен тестовый профиль, используются фиктивные часы, подключена тестовая бд
// fb бины не создаются для тестов
class InfrastructureIntegrationTest: BaseIntegrationTest() {

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var applicationContext: ApplicationContext


    @Test
    fun `test profile should be active`() {
        assertThat(environment.activeProfiles)
            .contains("test")
    }

    @Test
    fun `test profile should use cleaningapp_test database`() {
        val databaseName = jdbcTemplate.queryForObject(
            "select current_database()",
            String::class.java,
        )

        assertThat(databaseName).isEqualTo("cleaningapp_test")
    }

    @Test
    fun `test clock should be fixed`() {
        val firstNow = Instant.now(clock)
        val secondNow = Instant.now(clock)

        assertThat(firstNow).isEqualTo(secondNow)
    }

    @Test
    fun `firebase admin beans should not be created in test profile`() {
        val firebaseAppBeans = applicationContext.getBeanNamesForType(FirebaseApp::class.java)
        val firebaseAuthBeans = applicationContext.getBeanNamesForType(FirebaseAuth::class.java)

        assertThat(firebaseAppBeans).isEmpty()
        assertThat(firebaseAuthBeans).isEmpty()
    }
}
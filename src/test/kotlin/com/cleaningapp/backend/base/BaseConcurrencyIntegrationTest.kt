package com.cleaningapp.backend.base

import com.cleaningapp.backend.config.TimeTestConfiguration
import com.cleaningapp.backend.security.FirebaseAuthService
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.auth.UserRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


@ResourceLock("cleaningapp_test")
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(
    TimeTestConfiguration::class,
    TestDataFactory::class,
)
abstract class BaseConcurrencyIntegrationTest {

    protected val defaultFirebaseUid = "firebase-user-1"
    protected val defaultFirebaseEmail = "user@test.com"
    protected val validToken = "valid-token"

    @Autowired
    protected lateinit var testDataFactory: TestDataFactory

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    protected lateinit var firebaseAuthService: FirebaseAuthService

    @BeforeEach
    fun setUpConcurrencyBase() {
        cleanupDatabase()

        mockValidFirebaseToken(
            idToken = validToken,
            firebaseUid = defaultFirebaseUid,
        )

        mockFirebaseUser(
            firebaseUid = defaultFirebaseUid,
            email = defaultFirebaseEmail,
        )
    }

    @AfterEach
    fun tearDownConcurrencyBase() {
        SecurityContextHolder.clearContext()
        cleanupDatabase()
    }

    protected fun authenticateAs(firebaseUid: String = defaultFirebaseUid) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(firebaseUid, null, emptyList())
    }

    protected fun <T> authenticatedAs(
        firebaseUid: String,
        block: () -> T,
    ): T {
        authenticateAs(firebaseUid)

        return try {
            block()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    protected fun mockValidFirebaseToken(
        idToken: String,
        firebaseUid: String,
    ) {
        val firebaseToken = Mockito.mock(FirebaseToken::class.java)

        Mockito.`when`(firebaseToken.uid)
            .thenReturn(firebaseUid)

        Mockito.`when`(firebaseAuthService.verifyToken(idToken))
            .thenReturn(firebaseToken)
    }

    protected fun mockFirebaseUser(
        firebaseUid: String,
        email: String,
    ) {
        val userRecord = Mockito.mock(UserRecord::class.java)

        Mockito.`when`(userRecord.email)
            .thenReturn(email)

        Mockito.`when`(firebaseAuthService.getUserByUid(firebaseUid))
            .thenReturn(userRecord)
    }

    protected fun <T> runConcurrently(
        threadCount: Int,
        timeoutSeconds: Long = 10,
        action: (Int) -> T,
    ): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)

        val futures = (0 until threadCount).map { index ->
            executor.submit(
                Callable {
                    ready.countDown()

                    if (!start.await(timeoutSeconds, TimeUnit.SECONDS)) {
                        error("Concurrent test did not start in time")
                    }

                    runCatching {
                        action(index)
                    }
                }
            )
        }

        if (!ready.await(timeoutSeconds, TimeUnit.SECONDS)) {
            executor.shutdownNow()
            error("Not all worker threads are ready")
        }

        start.countDown()

        return try {
            futures.map { it.get(timeoutSeconds, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    protected fun successCount(results: List<Result<*>>): Int =
        results.count { it.isSuccess }

    protected fun failureCount(results: List<Result<*>>): Int =
        results.count { it.isFailure }

    protected fun failures(results: List<Result<*>>): List<Throwable> =
        results.mapNotNull { it.exceptionOrNull() }

    protected fun assertSingleFailureOfType(
        results: List<Result<*>>,
        vararg expectedTypes: Class<out Throwable>,
    ) {
        val failures = failures(results)

        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOfAny(*expectedTypes)
    }

    protected fun assertAllFailuresOfType(
        results: List<Result<*>>,
        vararg expectedTypes: Class<out Throwable>,
    ) {
        failures(results).forEach { failure ->
            assertThat(failure).isInstanceOfAny(*expectedTypes)
        }
    }

    private fun cleanupDatabase() {
        jdbcTemplate.execute(
            """
            truncate table
                "transaction",
                activity,
                task,
                privilege,
                user_household,
                household,
                "user"
            restart identity cascade
            """.trimIndent()
        )
    }
}

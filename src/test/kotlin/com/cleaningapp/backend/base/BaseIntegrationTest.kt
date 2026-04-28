package com.cleaningapp.backend.base

import com.cleaningapp.backend.config.TimeTestConfiguration
import com.cleaningapp.backend.security.FirebaseAuthService
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.auth.UserRecord
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import com.cleaningapp.backend.user.UserEntity
import org.springframework.beans.factory.annotation.Autowired

// общая настройка для всех остальных классов-тестировщиков
// моккирует fb
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(
    TimeTestConfiguration::class,
    TestDataFactory::class,
)
@Transactional
abstract class BaseIntegrationTest {

    // дефолтные переменные
    protected val defaultFirebaseUid = "firebase-user-1"
    protected val defaultFirebaseEmail = "user@test.com"
    protected val validToken = "valid-token"

    @Autowired
    protected lateinit var testDataFactory: TestDataFactory

    // helper для protected endpoint-тестов
    protected fun createLocalUserForValidToken(
        name: String = "Test User",
        isActive: Boolean = true,
    ): UserEntity {
        return testDataFactory.createTestUser(
            firebaseUid = defaultFirebaseUid,
            email = defaultFirebaseEmail,
            name = name,
            isActive = isActive,
        )
    }

    // моккирует fb service -> filter работает и получает данные из mock
    @MockitoBean
    protected lateinit var firebaseAuthService: FirebaseAuthService

    // перед каждым тестом одинаково настраивает мокк fb
    @BeforeEach
    fun setUpFirebaseMock() {
        mockValidFirebaseToken(
            idToken = validToken, // всегда валидный токен
            firebaseUid = defaultFirebaseUid, // всегда базовый uid
        )

        mockFirebaseUser(
            firebaseUid = defaultFirebaseUid,
            email = defaultFirebaseEmail,
        )
    }


    // настраивает verifyToken
    // при проверке токена всегда возвращается валидный -> пользователь авторизован
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

    // настраивает getUserByUid
    // если бэк получает данные из fb -> при синхроне почты
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
}
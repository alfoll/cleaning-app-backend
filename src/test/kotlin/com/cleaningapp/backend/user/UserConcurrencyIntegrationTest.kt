package com.cleaningapp.backend.user

import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.exception.EmailAlreadyUsedException
import com.cleaningapp.backend.exception.UserAlreadyExistsException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

class UserConcurrencyIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var userHouseholdService: UserHouseholdService

    @Autowired
    private lateinit var householdRepository: HouseholdRepository

    @Test
    fun `createUser with same firebaseUid creates only one user`() {
        val firebaseUid = "same-firebase-${UUID.randomUUID()}"

        val results = runConcurrently(threadCount = 2) { index ->
            userService.createUser(
                firebaseUid = firebaseUid,
                user = UserRegisterDTO(
                    name = "User $index",
                    email = "same-firebase-$index-${UUID.randomUUID()}@test.com",
                    avatarUrl = null,
                )
            )
        }

        val savedUsers = userRepository.findAll()
            .filter { it.firebaseUid == firebaseUid }

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(
            results,
            UserAlreadyExistsException::class.java,
            DataIntegrityViolationException::class.java,
        )
        assertThat(savedUsers).hasSize(1)
    }

    @Test
    fun `createUser with same email creates only one user`() {
        val email = "same-email-${UUID.randomUUID()}@test.com"

        val results = runConcurrently(threadCount = 2) { index ->
            userService.createUser(
                firebaseUid = "firebase-$index-${UUID.randomUUID()}",
                user = UserRegisterDTO(
                    name = "User $index",
                    email = email,
                    avatarUrl = null,
                )
            )
        }

        val savedUsers = userRepository.findAll()
            .filter { it.email == email }

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(
            results,
            EmailAlreadyUsedException::class.java,
            DataIntegrityViolationException::class.java,
        )
        assertThat(savedUsers).hasSize(1)
    }

    @Test
    fun `parallel deleteUser calls deactivate user and memberships once`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")

        val household1 = testDataFactory.createTestHousehold(createdBy = user)
        val household2 = testDataFactory.createTestHousehold(createdBy = user)
        val household3 = testDataFactory.createTestHousehold(createdBy = user)

        val membership1 = testDataFactory.createTestMembership(user = user, household = household1, balance = 10)
        val membership2 = testDataFactory.createTestMembership(user = user, household = household2, balance = 20)
        val membership3 = testDataFactory.createTestMembership(user = user, household = household3, balance = 30)

        val results = runConcurrently(threadCount = 2) {
            authenticatedAs(user.firebaseUid) {
                userService.deleteUser()
            }
        }

        val savedUser = userRepository.findById(user.id!!).orElseThrow()

        val savedMemberships = listOf(
            userHouseholdRepository.findById(membership1.id!!).orElseThrow(),
            userHouseholdRepository.findById(membership2.id!!).orElseThrow(),
            userHouseholdRepository.findById(membership3.id!!).orElseThrow(),
        )

        assertThat(successCount(results)).isEqualTo(1)
        assertThat(failureCount(results)).isEqualTo(1)
        assertSingleFailureOfType(results, UserNotActiveException::class.java)

        assertThat(savedUser.isActive).isFalse()

        savedMemberships.forEach { membership ->
            assertThat(membership.isUserActive).isFalse()
            assertThat(membership.balance).isEqualTo(0)
        }
    }

    @Test
    fun `parallel deleteUser calls for two users with shared households complete without deadlock`() {
        val user1 = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val user2 = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household1 = testDataFactory.createTestHousehold(createdBy = user1)
        val household2 = testDataFactory.createTestHousehold(createdBy = user1)

        val membership11 = testDataFactory.createTestMembership(user = user1, household = household1, balance = 10)
        val membership12 = testDataFactory.createTestMembership(user = user1, household = household2, balance = 20)
        val membership21 = testDataFactory.createTestMembership(user = user2, household = household1, balance = 30)
        val membership22 = testDataFactory.createTestMembership(user = user2, household = household2, balance = 40)

        val results = runConcurrently(threadCount = 2) { index ->
            val firebaseUid = if (index == 0) user1.firebaseUid else user2.firebaseUid

            authenticatedAs(firebaseUid) {
                userService.deleteUser()
            }
        }

        val savedUser1 = userRepository.findById(user1.id!!).orElseThrow()
        val savedUser2 = userRepository.findById(user2.id!!).orElseThrow()

        val savedMemberships = listOf(
            userHouseholdRepository.findById(membership11.id!!).orElseThrow(),
            userHouseholdRepository.findById(membership12.id!!).orElseThrow(),
            userHouseholdRepository.findById(membership21.id!!).orElseThrow(),
            userHouseholdRepository.findById(membership22.id!!).orElseThrow(),
        )

        val savedHousehold1 = householdRepository.findById(household1.id!!).orElseThrow()
        val savedHousehold2 = householdRepository.findById(household2.id!!).orElseThrow()

        assertThat(successCount(results)).isEqualTo(2)
        assertThat(failureCount(results)).isEqualTo(0)

        assertThat(savedUser1.isActive).isFalse()
        assertThat(savedUser2.isActive).isFalse()
        assertThat(savedHousehold1.isActive).isFalse()
        assertThat(savedHousehold2.isActive).isFalse()

        savedMemberships.forEach { membership ->
            assertThat(membership.isUserActive).isFalse()
            assertThat(membership.balance).isEqualTo(0)
        }
    }

    @Test
    fun `deleteUser and leaveHousehold in parallel keep final state consistent`() {
        val user = testDataFactory.createTestUser(firebaseUid = "firebase-user-1")
        val otherUser = testDataFactory.createTestUser(firebaseUid = "firebase-user-2")

        val household = testDataFactory.createTestHousehold(createdBy = user)

        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
            balance = 0,
        )

        val results = runConcurrently(threadCount = 2) { index ->
            authenticatedAs(user.firebaseUid) {
                if (index == 0) {
                    userService.deleteUser()
                } else {
                    userHouseholdService.leaveHousehold(household.id!!)
                }
            }
        }

        val savedUser = userRepository.findById(user.id!!).orElseThrow()
        val savedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val savedHousehold = householdRepository.findById(household.id!!).orElseThrow()

        assertThat(successCount(results)).isIn(1, 2)
        assertThat(failureCount(results)).isIn(0, 1)

        if (failureCount(results) == 1) {
            assertSingleFailureOfType(results, UserNotActiveException::class.java)
        }

        assertThat(savedUser.isActive).isFalse()
        assertThat(savedMembership.isUserActive).isFalse()
        assertThat(savedMembership.balance).isEqualTo(0)
        assertThat(savedHousehold.isActive).isTrue()
    }

}

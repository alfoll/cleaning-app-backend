package com.cleaningapp.backend.user

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.EmailAlreadyUsedException
import com.cleaningapp.backend.exception.UserAlreadyExistsException
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired


class UserServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var householdRepository: HouseholdRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `createUser should create active user`() {
        val result = userService.createUser(
            firebaseUid = "firebase-new-user",
            user = UserRegisterDTO(
                name = "Alice",
                email = "alice@test.com",
                avatarUrl = "https://example.com/avatar.png",
            )
        )

        val savedUser = userRepository.findUserByFirebaseUid("firebase-new-user")

        assertThat(result.id).isNotNull()
        assertThat(result.firebaseUid).isEqualTo("firebase-new-user")
        assertThat(result.name).isEqualTo("Alice")
        assertThat(result.email).isEqualTo("alice@test.com")
        assertThat(result.avatarUrl).isEqualTo("https://example.com/avatar.png")

        assertThat(savedUser).isNotNull
        assertThat(savedUser?.isActive).isTrue()
    }

    @Test
    fun `createUser should reject duplicate firebase uid`() {
        testDataFactory.createTestUser(
            firebaseUid = "duplicate-firebase-uid",
            email = "first@test.com",
        )

        assertThatThrownBy {
            userService.createUser(
                firebaseUid = "duplicate-firebase-uid",
                user = UserRegisterDTO(
                    name = "Second User",
                    email = "second@test.com",
                    avatarUrl = null,
                )
            )
        }.isInstanceOf(UserAlreadyExistsException::class.java)
    }

    @Test
    fun `createUser should reject duplicate email`() {
        testDataFactory.createTestUser(
            firebaseUid = "firebase-first-user",
            email = "same@test.com",
        )

        assertThatThrownBy {
            userService.createUser(
                firebaseUid = "firebase-second-user",
                user = UserRegisterDTO(
                    name = "Second User",
                    email = "same@test.com",
                    avatarUrl = null,
                )
            )
        }.isInstanceOf(EmailAlreadyUsedException::class.java)
    }

    @Test
    fun `getProfile should return current active user`() {
        createLocalUserForValidToken(
            name = "Current User",
            isActive = true,
        )
        authenticateAs()

        val result = userService.getProfile()

        assertThat(result.firebaseUid).isEqualTo(defaultFirebaseUid)
        assertThat(result.email).isEqualTo(defaultFirebaseEmail)
        assertThat(result.name).isEqualTo("Current User")
    }

    @Test
    fun `updateProfile should update current user name and avatar`() {
        val user = createLocalUserForValidToken(
            name = "Old Name",
            isActive = true,
        )
        authenticateAs()

        val result = userService.updateProfile(
            UserUpdateDTO(
                name = "New Name",
                avatarUrl = "https://example.com/new-avatar.png",
            )
        )

        val updatedUser = userRepository.findById(user.id!!).orElseThrow()

        assertThat(result.name).isEqualTo("New Name")
        assertThat(result.avatarUrl).isEqualTo("https://example.com/new-avatar.png")

        assertThat(updatedUser.name).isEqualTo("New Name")
        assertThat(updatedUser.avatarUrl).isEqualTo("https://example.com/new-avatar.png")
    }

    @Test
    fun `syncEmailFromFirebase should update local email from Firebase`() {
        val user = testDataFactory.createTestUser(
            firebaseUid = defaultFirebaseUid,
            email = "old@test.com",
            name = "Current User",
        )

        mockFirebaseUser(
            firebaseUid = defaultFirebaseUid,
            email = "new@test.com",
        )
        authenticateAs()

        val result = userService.syncEmailFromFirebase()

        val updatedUser = userRepository.findById(user.id!!).orElseThrow()

        assertThat(result.email).isEqualTo("new@test.com")
        assertThat(updatedUser.email).isEqualTo("new@test.com")
    }

    @Test
    fun `syncEmailFromFirebase should not fail when Firebase email is the same`() {
        val user = createLocalUserForValidToken()
        mockFirebaseUser(
            firebaseUid = defaultFirebaseUid,
            email = defaultFirebaseEmail,
        )
        authenticateAs()

        val result = userService.syncEmailFromFirebase()

        val updatedUser = userRepository.findById(user.id!!).orElseThrow()

        assertThat(result.email).isEqualTo(defaultFirebaseEmail)
        assertThat(updatedUser.email).isEqualTo(defaultFirebaseEmail)
    }

    @Test
    fun `syncEmailFromFirebase should reject email used by another user`() {
        createLocalUserForValidToken()

        testDataFactory.createTestUser(
            firebaseUid = "another-firebase-uid",
            email = "used@test.com",
        )

        mockFirebaseUser(
            firebaseUid = defaultFirebaseUid,
            email = "used@test.com",
        )
        authenticateAs()

        assertThatThrownBy {
            userService.syncEmailFromFirebase()
        }.isInstanceOf(EmailAlreadyUsedException::class.java)
    }

    @Test
    fun `deleteUser should deactivate user and delete last household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        authenticateAs()

        userService.deleteUser()

        entityManager.flush()
        entityManager.clear()

        val updatedUser = userRepository.findById(user.id!!).orElseThrow()
        val updatedHousehold = householdRepository.findById(household.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        assertThat(updatedUser.isActive).isFalse()
        assertThat(updatedHousehold.isActive).isFalse()
        assertThat(updatedMembership.isUserActive).isFalse()
        assertThat(updatedMembership.balance).isZero()
    }

    @Test
    fun `deleteUser should process shared and last households in one call`() {
        val user = createLocalUserForValidToken(name = "Current User")
        val otherUser = testDataFactory.createTestUser(name = "Other User")

        val sharedHousehold = testDataFactory.createTestHousehold(
            createdBy = user,
            name = "Shared Household",
        )
        val sharedMembership = testDataFactory.createTestMembership(
            user = user,
            household = sharedHousehold,
            balance = 90,
        )
        testDataFactory.createTestMembership(
            user = otherUser,
            household = sharedHousehold,
        )

        val assignedTask = testDataFactory.createTestAssignedTask(
            household = sharedHousehold,
            createdBy = user,
            assignedTo = sharedMembership,
            reward = 20,
        )

        val soloHousehold = testDataFactory.createTestHousehold(
            createdBy = user,
            name = "Solo Household",
        )
        val soloMembership = testDataFactory.createTestMembership(
            user = user,
            household = soloHousehold,
            balance = 50,
        )

        authenticateAs()

        userService.deleteUser()

        entityManager.flush()
        entityManager.clear()

        val updatedUser = userRepository.findById(user.id!!).orElseThrow()
        val updatedSharedHousehold = householdRepository.findById(sharedHousehold.id!!).orElseThrow()
        val updatedSharedMembership =
            userHouseholdRepository.findById(sharedMembership.id!!).orElseThrow()
        val updatedSoloHousehold = householdRepository.findById(soloHousehold.id!!).orElseThrow()
        val updatedSoloMembership =
            userHouseholdRepository.findById(soloMembership.id!!).orElseThrow()
        val releasedTask = taskRepository.findById(assignedTask.id!!).orElseThrow()

        val sharedTransactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                sharedHousehold.id!!,
                sharedMembership.id!!,
            )
        val sharedActivities =
            activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(sharedHousehold.id!!)

        assertThat(updatedUser.isActive).isFalse()

        assertThat(updatedSharedHousehold.isActive).isTrue()
        assertThat(updatedSharedMembership.isUserActive).isFalse()
        assertThat(updatedSharedMembership.balance).isZero()
        assertThat(releasedTask.assignedTo).isNull()
        assertThat(releasedTask.assignedAt).isNull()
        assertThat(sharedTransactions).hasSize(1)
        assertThat(sharedTransactions.first().type).isEqualTo(TransactionType.BALANCE_RESET)
        assertThat(sharedTransactions.first().amount).isEqualTo(-90)
        assertThat(sharedActivities.map { it.activityType }).contains(ActivityType.USER_LEFT)

        assertThat(updatedSoloHousehold.isActive).isFalse()
        assertThat(updatedSoloMembership.isUserActive).isFalse()
        assertThat(updatedSoloMembership.balance).isZero()
    }
}

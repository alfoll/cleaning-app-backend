package com.cleaningapp.backend.activity

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID

class ActivityServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `createActivityRecord should create activity for household member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = household.id!!,
                memberId = membership.id!!,
                activityType = ActivityType.TASK_CREATED,
                title = "Task created",
                description = "User created a task",
            )
        )

        entityManager.flush()
        entityManager.clear()

        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(activities).hasSize(1)

        val activity = activities.first()

        assertThat(activity.household.id).isEqualTo(household.id)
        assertThat(activity.member.id).isEqualTo(membership.id)
        assertThat(activity.activityType).isEqualTo(ActivityType.TASK_CREATED)
        assertThat(activity.title).isEqualTo("Task created")
        assertThat(activity.description).isEqualTo("User created a task")
        assertThat(activity.createdAt).isNotNull()
    }

    @Test
    fun `createActivityRecord should allow inactive membership because history must survive member leaving`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val inactiveMembership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = household.id!!,
                memberId = inactiveMembership.id!!,
                activityType = ActivityType.USER_LEFT,
                title = "User left",
                description = "User left household",
            )
        )

        entityManager.flush()
        entityManager.clear()

        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(activities).hasSize(1)
        assertThat(activities.first().member.id).isEqualTo(inactiveMembership.id)
        assertThat(activities.first().activityType).isEqualTo(ActivityType.USER_LEFT)
    }

    @Test
    fun `createActivityRecord should reject nonexistent household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        assertThatThrownBy {
            activityService.createActivityRecord(
                RecordActivityCommand(
                    householdId = UUID.randomUUID(),
                    memberId = membership.id!!,
                    activityType = ActivityType.TASK_CREATED,
                    title = "Task created",
                    description = null,
                )
            )
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @Test
    fun `createActivityRecord should reject inactive household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        assertThatThrownBy {
            activityService.createActivityRecord(
                RecordActivityCommand(
                    householdId = household.id!!,
                    memberId = membership.id!!,
                    activityType = ActivityType.TASK_CREATED,
                    title = "Task created",
                    description = null,
                )
            )
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `createActivityRecord should reject nonexistent membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)

        assertThatThrownBy {
            activityService.createActivityRecord(
                RecordActivityCommand(
                    householdId = household.id!!,
                    memberId = UUID.randomUUID(),
                    activityType = ActivityType.TASK_CREATED,
                    title = "Task created",
                    description = null,
                )
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `createActivityRecord should reject membership from another household`() {
        val user = createLocalUserForValidToken()

        val householdA = testDataFactory.createTestHousehold(createdBy = user)
        val householdB = testDataFactory.createTestHousehold(createdBy = user)

        val memberFromHouseholdB = testDataFactory.createTestMembership(
            user = user,
            household = householdB,
        )

        assertThatThrownBy {
            activityService.createActivityRecord(
                RecordActivityCommand(
                    householdId = householdA.id!!,
                    memberId = memberFromHouseholdB.id!!,
                    activityType = ActivityType.TASK_CREATED,
                    title = "Task created",
                    description = null,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `getHouseholdActivity should return all activity sorted by createdAt desc`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )
        val otherMembership = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        val otherHouseholdMembership = testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val olderActivity = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_CREATED,
            title = "Older activity",
        )
        val newerActivity = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_COMPLETED,
            title = "Newer activity",
        )
        val middleActivity = testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.PRIVILEGE_CREATED,
            title = "Other member activity",
        )
        val otherHouseholdActivity = testDataFactory.createTestActivity(
            household = otherHousehold,
            member = otherHouseholdMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other household activity",
        )

        testDataFactory.updateActivityCreatedAt(
            activityId = olderActivity.id!!,
            createdAt = baseTime.minusDays(2),
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = middleActivity.id!!,
            createdAt = baseTime.minusDays(1),
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = newerActivity.id!!,
            createdAt = baseTime,
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = otherHouseholdActivity.id!!,
            createdAt = baseTime.plusDays(1),
        )

        authenticateAs()

        val result = activityService.getHouseholdActivity(
            householdId = household.id!!,
            activityType = null,
            actorScope = ActivityActorScope.ALL,
        )

        assertThat(result.map { it.id })
            .containsExactly(newerActivity.id, middleActivity.id, olderActivity.id)

        assertThat(result.map { it.householdId })
            .containsOnly(household.id)

        assertThat(result.map { it.userId })
            .contains(user.id, otherUser.id)

        assertThat(result.map { it.id })
            .doesNotContain(otherHouseholdActivity.id)
    }

    @Test
    fun `getHouseholdActivity should return at most 150 newest activities`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val activities = (1..151).map { index ->
            val activity = testDataFactory.createTestActivity(
                household = household,
                member = membership,
                activityType = ActivityType.TASK_CREATED,
                title = "Activity $index",
            )

            testDataFactory.updateActivityCreatedAt(
                activityId = activity.id!!,
                createdAt = baseTime.plusMinutes(index.toLong()),
            )

            activity
        }

        authenticateAs()

        val result = activityService.getHouseholdActivity(
            householdId = household.id!!,
            activityType = null,
            actorScope = ActivityActorScope.ALL,
        )

        assertThat(result).hasSize(150)
        assertThat(result.first().id).isEqualTo(activities.last().id)
        assertThat(result.last().id).isEqualTo(activities[1].id)
        assertThat(result.map { it.id }).doesNotContain(activities.first().id)
    }

    @Test
    fun `getHouseholdActivity should filter by activity type with ALL actor scope`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )
        val otherMembership = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        val otherHouseholdMembership = testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
        )

        val taskCreatedActivity = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_CREATED,
            title = "Task created",
        )
        val otherMemberTaskCreatedActivity = testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other member task created",
        )
        testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.PRIVILEGE_CREATED,
            title = "Privilege created",
        )
        testDataFactory.createTestActivity(
            household = otherHousehold,
            member = otherHouseholdMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other household task created",
        )

        authenticateAs()

        val result = activityService.getHouseholdActivity(
            householdId = household.id!!,
            activityType = ActivityType.TASK_CREATED,
            actorScope = ActivityActorScope.ALL,
        )

        assertThat(result.map { it.id })
            .containsExactlyInAnyOrder(
                taskCreatedActivity.id,
                otherMemberTaskCreatedActivity.id,
            )

        assertThat(result.map { it.activityType })
            .containsOnly(ActivityType.TASK_CREATED)
    }

    @Test
    fun `getHouseholdActivity should filter by current member when actor scope is MY`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        val currentMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        val otherMembership = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = currentUser)
        val currentUserOtherMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = otherHousehold,
        )

        val myActivity = testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "My activity",
        )
        testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other activity",
        )
        val myOtherHouseholdActivity = testDataFactory.createTestActivity(
            household = otherHousehold,
            member = currentUserOtherMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "My other household activity",
        )

        authenticateAs()

        val result = activityService.getHouseholdActivity(
            householdId = household.id!!,
            activityType = null,
            actorScope = ActivityActorScope.MY,
        )

        assertThat(result.map { it.id })
            .containsExactly(myActivity.id)

        assertThat(result.first().userId).isEqualTo(currentUser.id)
        assertThat(result.map { it.id }).doesNotContain(myOtherHouseholdActivity.id)
    }

    @Test
    fun `getHouseholdActivity should filter by activity type and current member when actor scope is MY`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        val currentMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = household,
        )
        val otherMembership = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        val myCompletedActivity = testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.TASK_COMPLETED,
            title = "My completed task",
        )
        testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "My created task",
        )
        testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.TASK_COMPLETED,
            title = "Other completed task",
        )

        authenticateAs()

        val result = activityService.getHouseholdActivity(
            householdId = household.id!!,
            activityType = ActivityType.TASK_COMPLETED,
            actorScope = ActivityActorScope.MY,
        )

        assertThat(result.map { it.id })
            .containsExactly(myCompletedActivity.id)

        assertThat(result.first().activityType).isEqualTo(ActivityType.TASK_COMPLETED)
        assertThat(result.first().userId).isEqualTo(currentUser.id)
    }

    @Test
    fun `getHouseholdActivity should return empty list when no activity exists`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        authenticateAs()

        val result = activityService.getHouseholdActivity(
            householdId = household.id!!,
            activityType = null,
            actorScope = ActivityActorScope.ALL,
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `getHouseholdActivity should reject inactive user`() {
        createLocalUserForValidToken(isActive = false)

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        authenticateAs()

        assertThatThrownBy {
            activityService.getHouseholdActivity(
                householdId = household.id!!,
                activityType = null,
                actorScope = ActivityActorScope.ALL,
            )
        }.isInstanceOf(UserNotActiveException::class.java)
    }

    @Test
    fun `getHouseholdActivity should reject nonexistent household`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            activityService.getHouseholdActivity(
                householdId = UUID.randomUUID(),
                activityType = null,
                actorScope = ActivityActorScope.ALL,
            )
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @Test
    fun `getHouseholdActivity should reject inactive household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = true,
        )

        authenticateAs()

        assertThatThrownBy {
            activityService.getHouseholdActivity(
                householdId = household.id!!,
                activityType = null,
                actorScope = ActivityActorScope.ALL,
            )
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `getHouseholdActivity should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            activityService.getHouseholdActivity(
                householdId = household.id!!,
                activityType = null,
                actorScope = ActivityActorScope.ALL,
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getHouseholdActivity should reject inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        authenticateAs()

        assertThatThrownBy {
            activityService.getHouseholdActivity(
                householdId = household.id!!,
                activityType = null,
                actorScope = ActivityActorScope.ALL,
            )
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }
}

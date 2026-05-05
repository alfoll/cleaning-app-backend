package com.cleaningapp.backend.activity

import com.cleaningapp.backend.base.BaseIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class ActivityRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `activity filter queries should return expected activities with repository sorting contract`() {
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
        val otherHouseholdMembership = testDataFactory.createTestMembership(
            user = currentUser,
            household = otherHousehold,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val olderCurrentTaskCreated = testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Older current task created",
        )
        val newerCurrentTaskCreated = testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Newer current task created",
        )
        val currentPrivilegeCreated = testDataFactory.createTestActivity(
            household = household,
            member = currentMembership,
            activityType = ActivityType.PRIVILEGE_CREATED,
            title = "Current privilege created",
        )
        val otherMemberTaskCreated = testDataFactory.createTestActivity(
            household = household,
            member = otherMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other member task created",
        )
        val otherHouseholdActivity = testDataFactory.createTestActivity(
            household = otherHousehold,
            member = otherHouseholdMembership,
            activityType = ActivityType.TASK_CREATED,
            title = "Other household activity",
        )

        testDataFactory.updateActivityCreatedAt(
            activityId = olderCurrentTaskCreated.id!!,
            createdAt = baseTime.minusDays(5),
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = newerCurrentTaskCreated.id!!,
            createdAt = baseTime.minusDays(1),
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = currentPrivilegeCreated.id!!,
            createdAt = baseTime.minusDays(2),
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = otherMemberTaskCreated.id!!,
            createdAt = baseTime.minusDays(3),
        )
        testDataFactory.updateActivityCreatedAt(
            activityId = otherHouseholdActivity.id!!,
            createdAt = baseTime.plusDays(1),
        )

        val all =
            activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        val byType =
            activityRepository.findAllByHouseholdIdAndActivityTypeOrderByCreatedAtDesc(
                household.id!!,
                ActivityType.TASK_CREATED,
            )

        val byMember =
            activityRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                currentMembership.id!!,
            )

        val byTypeAndMember =
            activityRepository.findAllByHouseholdIdAndActivityTypeAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                ActivityType.TASK_CREATED,
                currentMembership.id!!,
            )

        assertThat(all.map { it.id })
            .containsExactly(
                newerCurrentTaskCreated.id,
                currentPrivilegeCreated.id,
                otherMemberTaskCreated.id,
                olderCurrentTaskCreated.id,
            )
            .doesNotContain(otherHouseholdActivity.id)

        assertThat(byType.map { it.id })
            .containsExactly(
                newerCurrentTaskCreated.id,
                otherMemberTaskCreated.id,
                olderCurrentTaskCreated.id,
            )

        assertThat(byMember.map { it.id })
            .containsExactly(
                newerCurrentTaskCreated.id,
                currentPrivilegeCreated.id,
                olderCurrentTaskCreated.id,
            )

        assertThat(byTypeAndMember.map { it.id })
            .containsExactly(
                newerCurrentTaskCreated.id,
                olderCurrentTaskCreated.id,
            )
    }

    @Test
    fun `deleteAllByHouseholdId should delete only activities of selected household`() {
        val user = createLocalUserForValidToken()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        val otherHouseholdMembership = testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
        )

        val activityOne = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_CREATED,
        )
        val activityTwo = testDataFactory.createTestActivity(
            household = household,
            member = membership,
            activityType = ActivityType.TASK_COMPLETED,
        )
        val otherHouseholdActivity = testDataFactory.createTestActivity(
            household = otherHousehold,
            member = otherHouseholdMembership,
            activityType = ActivityType.TASK_CREATED,
        )

        val deletedCount = activityRepository.deleteAllByHouseholdId(household.id!!)

        entityManager.flush()
        entityManager.clear()

        assertThat(deletedCount).isEqualTo(2)
        assertThat(activityRepository.findById(activityOne.id!!)).isEmpty()
        assertThat(activityRepository.findById(activityTwo.id!!)).isEmpty()
        assertThat(activityRepository.findById(otherHouseholdActivity.id!!)).isPresent
    }
}
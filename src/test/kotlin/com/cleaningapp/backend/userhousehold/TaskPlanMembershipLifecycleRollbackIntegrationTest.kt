package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.activity.ActivityService
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.activity.RecordActivityCommand
import com.cleaningapp.backend.base.BaseConcurrencyIntegrationTest
import com.cleaningapp.backend.taskplan.TaskPlanRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean

class TaskPlanMembershipLifecycleRollbackIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Autowired
    private lateinit var userHouseholdService: UserHouseholdService

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var taskPlanRepository: TaskPlanRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Test
    fun `leave failure should roll back task plan and membership deactivation`() {
        val leavingUser = testDataFactory.createTestUser(firebaseUid = defaultFirebaseUid)
        val otherUser = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = leavingUser)
        val leavingMembership = testDataFactory.createTestMembership(user = leavingUser, household = household)
        testDataFactory.createTestMembership(user = otherUser, household = household)
        val plan = testDataFactory.createTestTaskPlan(household = household, createdBy = leavingUser)
        val expectedActivity = RecordActivityCommand(
            householdId = household.id!!,
            memberId = leavingMembership.id!!,
            activityType = ActivityType.USER_LEFT,
            title = "User left",
            description = "${leavingUser.name} left household \"${household.name}\"",
        )
        Mockito.doThrow(IllegalStateException("Artificial activity failure"))
            .`when`(activityService)
            .createActivityRecord(expectedActivity)
        authenticateAs()

        assertThatThrownBy {
            userHouseholdService.leaveHousehold(household.id!!)
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(userHouseholdRepository.findById(leavingMembership.id!!).orElseThrow().isUserActive).isTrue()
        assertThat(taskPlanRepository.findById(plan.id!!).orElseThrow().isActive).isTrue()
    }
}

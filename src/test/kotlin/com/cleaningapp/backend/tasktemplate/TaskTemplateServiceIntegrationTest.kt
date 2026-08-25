package com.cleaningapp.backend.tasktemplate

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDateTime

class TaskTemplateServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskTemplateService: TaskTemplateService

    @Autowired
    private lateinit var taskTemplateRepository: TaskTemplateRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `createTemplate should create template for active member and set creator on backend`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        authenticateAs()

        val result = taskTemplateService.createTemplate(
            household.id!!,
            TaskTemplateRegisterDTO(
                title = "Wash floors",
                description = "Kitchen and hallway",
                reward = 25,
            ),
        )

        entityManager.flush()
        entityManager.clear()
        val saved = taskTemplateRepository.findById(result.id).orElseThrow()

        assertThat(result.createdBy).isEqualTo(user.id)
        assertThat(result.householdId).isEqualTo(household.id)
        assertThat(saved.createdBy.id).isEqualTo(user.id)
        assertThat(saved.household.id).isEqualTo(household.id)
        assertThat(saved.isActive).isTrue()
        assertThat(activityRepository.findAll()).isEmpty()
    }

    @Test
    fun `createTemplate should reject user without household membership`() {
        createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold()
        authenticateAs()

        assertThatThrownBy {
            taskTemplateService.createTemplate(
                household.id!!,
                TaskTemplateRegisterDTO(title = "Wash floors", reward = 25),
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getHouseholdTemplates should return active current household templates newest first`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val otherHousehold = testDataFactory.createTestHousehold()
        val now = LocalDateTime.now(clock)

        val older = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = user,
            title = "Older",
        )
        val newer = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = user,
            title = "Newer",
        )
        testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = user,
            title = "Inactive",
            isActive = false,
        )
        testDataFactory.createTestTaskTemplate(
            household = otherHousehold,
            title = "Other household",
        )
        testDataFactory.updateTaskTemplateCreatedAt(older.id!!, now.minusDays(2))
        testDataFactory.updateTaskTemplateCreatedAt(newer.id!!, now.minusDays(1))
        authenticateAs()

        val result = taskTemplateService.getHouseholdTemplates(household.id!!)

        assertThat(result.map { it.id }).containsExactly(newer.id, older.id)
    }

    @Test
    fun `updateTemplate should allow creator to change all editable fields`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val template = testDataFactory.createTestTaskTemplate(household = household, createdBy = user)
        authenticateAs()

        val result = taskTemplateService.updateTemplate(
            template.id!!,
            TaskTemplateRegisterDTO(
                title = "Updated template",
                description = null,
                reward = 40,
            ),
        )

        assertThat(result.title).isEqualTo("Updated template")
        assertThat(result.description).isNull()
        assertThat(result.reward).isEqualTo(40)
        assertThat(result.createdBy).isEqualTo(user.id)
    }

    @Test
    fun `updateTemplate should reject another member of same household`() {
        val creator = testDataFactory.createTestUser()
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = user, household = household)
        val template = testDataFactory.createTestTaskTemplate(household = household, createdBy = creator)
        authenticateAs()

        assertThatThrownBy {
            taskTemplateService.updateTemplate(
                template.id!!,
                TaskTemplateRegisterDTO(title = "Updated template", reward = 40),
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `updateTemplate should reject member of another household`() {
        val creator = testDataFactory.createTestUser()
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = creator)
        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = user, household = otherHousehold)
        val template = testDataFactory.createTestTaskTemplate(household = household, createdBy = creator)
        authenticateAs()

        assertThatThrownBy {
            taskTemplateService.updateTemplate(
                template.id!!,
                TaskTemplateRegisterDTO(title = "Updated template", reward = 40),
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `updateTemplate should reject inactive template`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val template = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = user,
            isActive = false,
        )
        authenticateAs()

        assertThatThrownBy {
            taskTemplateService.updateTemplate(
                template.id!!,
                TaskTemplateRegisterDTO(title = "Updated template", reward = 40),
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `deleteTemplate should soft delete creator template and hide it from household list`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val template = testDataFactory.createTestTaskTemplate(household = household, createdBy = user)
        authenticateAs()

        taskTemplateService.deleteTemplate(template.id!!)
        entityManager.flush()
        entityManager.clear()

        val saved = taskTemplateRepository.findById(template.id!!).orElseThrow()
        val visible = taskTemplateService.getHouseholdTemplates(household.id!!)

        assertThat(saved.isActive).isFalse()
        assertThat(visible).isEmpty()
        assertThat(activityRepository.findAll()).isEmpty()
    }

    @Test
    fun `deleteTemplate should reject another household member`() {
        val creator = testDataFactory.createTestUser()
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = user, household = household)
        val template = testDataFactory.createTestTaskTemplate(household = household, createdBy = creator)
        authenticateAs()

        assertThatThrownBy {
            taskTemplateService.deleteTemplate(template.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)

        assertThat(taskTemplateRepository.findById(template.id!!).orElseThrow().isActive).isTrue()
    }
}

package com.cleaningapp.backend.tasktemplate

import com.cleaningapp.backend.base.BaseIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDateTime

class TaskTemplateRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskTemplateRepository: TaskTemplateRepository

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `find active household templates should filter and sort by creation time descending`() {
        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val otherHousehold = testDataFactory.createTestHousehold()
        val now = LocalDateTime.now(clock)

        val older = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = owner,
            title = "Older",
        )
        val newer = testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = owner,
            title = "Newer",
        )
        testDataFactory.createTestTaskTemplate(
            household = household,
            createdBy = owner,
            title = "Inactive",
            isActive = false,
        )
        testDataFactory.createTestTaskTemplate(
            household = otherHousehold,
            title = "Other household",
        )

        testDataFactory.updateTaskTemplateCreatedAt(older.id!!, now.minusDays(2))
        testDataFactory.updateTaskTemplateCreatedAt(newer.id!!, now.minusDays(1))

        val result = taskTemplateRepository
            .findAllByHouseholdIdAndIsActiveTrueOrderByCreatedAtDesc(household.id!!)

        assertThat(result.map { it.id }).containsExactly(newer.id, older.id)
    }
}

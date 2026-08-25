package com.cleaningapp.backend.task

import com.cleaningapp.backend.base.BaseIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

class TaskRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    private fun endOfDay(date: LocalDate): LocalDateTime =
        date.atTime(23, 59, 59, 999_999_000)

    @Test
    fun `task filter queries should return expected tasks with repository sorting contract`() {
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

        val olderFreeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = currentUser,
        )
        val newerFreeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = currentUser,
        )

        val olderMyAssignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = currentMembership,
        )
        val newerMyAssignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = currentMembership,
        )

        val otherAssignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = otherMembership,
        )

        val olderCompletedTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = currentUser,
            completedBy = currentMembership,
        )
        val newerCompletedTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = currentUser,
            completedBy = currentMembership,
        )

        val otherHouseholdTask = testDataFactory.createTestAssignedTask(
            household = otherHousehold,
            createdBy = currentUser,
            assignedTo = otherHouseholdMembership,
        )

        testDataFactory.updateTaskTimestamps(
            taskId = olderFreeTask.id!!,
            createdAt = baseTime.minusDays(7),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = newerFreeTask.id!!,
            createdAt = baseTime.minusDays(1),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = olderMyAssignedTask.id!!,
            createdAt = baseTime.minusDays(6),
            assignedAt = baseTime.minusDays(5),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = newerMyAssignedTask.id!!,
            createdAt = baseTime.minusDays(2),
            assignedAt = baseTime.minusDays(1),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = otherAssignedTask.id!!,
            createdAt = baseTime.minusDays(3),
            assignedAt = baseTime.minusDays(2),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = olderCompletedTask.id!!,
            createdAt = baseTime.minusDays(5),
            completedAt = baseTime.minusDays(4),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = newerCompletedTask.id!!,
            createdAt = baseTime.minusDays(4),
            completedAt = baseTime.minusDays(1),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = otherHouseholdTask.id!!,
            createdAt = baseTime.plusDays(1),
            assignedAt = baseTime.plusDays(1),
        )

        val allTasks = taskRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)
        val freeTasks =
            taskRepository.findAllByHouseholdIdAndAssignedToIsNullAndIsCompletedFalseOrderByCreatedAtDesc(
                household.id!!,
            )
        val myAssignedTasks =
            taskRepository.findAllByHouseholdIdAndAssignedToIdAndIsCompletedFalseOrderByAssignedAtDesc(
                household.id!!,
                currentMembership.id!!,
            )
        val completedTasks =
            taskRepository.findAllByHouseholdIdAndIsCompletedTrueOrderByCompletedAtDesc(
                household.id!!,
            )

        assertThat(allTasks.map { it.id })
            .containsExactly(
                newerFreeTask.id,
                newerMyAssignedTask.id,
                otherAssignedTask.id,
                newerCompletedTask.id,
                olderCompletedTask.id,
                olderMyAssignedTask.id,
                olderFreeTask.id,
            )
            .doesNotContain(otherHouseholdTask.id)

        assertThat(freeTasks.map { it.id })
            .containsExactly(
                newerFreeTask.id,
                olderFreeTask.id,
            )

        assertThat(myAssignedTasks.map { it.id })
            .containsExactly(
                newerMyAssignedTask.id,
                olderMyAssignedTask.id,
            )

        assertThat(completedTasks.map { it.id })
            .containsExactly(
                newerCompletedTask.id,
                olderCompletedTask.id,
            )
    }

    @Test
    fun `deadline queries should apply filter sorting and household contract`() {
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
        testDataFactory.createTestMembership(user = currentUser, household = otherHousehold)
        val now = LocalDateTime.now(clock)

        val oldestOverdueFree = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = currentUser,
            dueAt = now.minusDays(3),
        )
        val overdueAssigned = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = otherMembership,
            dueAt = now.minusDays(2),
        )
        val today = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = currentUser,
            dueAt = endOfDay(LocalDate.now(clock)),
        )
        val future = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = currentMembership,
            dueAt = now.plusDays(2),
        )
        val withoutDeadline = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = currentUser,
        )
        val completedPast = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = currentUser,
            completedBy = currentMembership,
            dueAt = now.minusDays(4),
        )
        val otherHouseholdOverdue = testDataFactory.createTestFreeTask(
            household = otherHousehold,
            createdBy = currentUser,
            dueAt = now.minusDays(5),
        )

        val firstPage = PageRequest.of(0, 150)
        val withDeadline =
            taskRepository.findAllByHouseholdIdAndIsCompletedFalseAndDueAtIsNotNullOrderByDueAtAsc(
                household.id!!,
                firstPage,
            )
        val overdue =
            taskRepository.findAllByHouseholdIdAndIsCompletedFalseAndDueAtIsNotNullAndDueAtBeforeOrderByDueAtAsc(
                household.id!!,
                now,
                firstPage,
            )

        assertThat(withDeadline.map { it.id })
            .containsExactly(
                oldestOverdueFree.id,
                overdueAssigned.id,
                today.id,
                future.id,
            )
            .doesNotContain(
                withoutDeadline.id,
                completedPast.id,
                otherHouseholdOverdue.id,
            )

        assertThat(overdue.map { it.id })
            .containsExactly(
                oldestOverdueFree.id,
                overdueAssigned.id,
            )
            .doesNotContain(
                today.id,
                future.id,
                withoutDeadline.id,
                completedPast.id,
                otherHouseholdOverdue.id,
            )
    }

    @Test
    fun `release query should find only unfinished tasks assigned to membership`() {
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

        val assignedToCurrent = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = currentMembership,
        )
        val assignedToOther = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = otherMembership,
        )
        val freeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = currentUser,
        )
        val completedByCurrent = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = currentUser,
            completedBy = currentMembership,
        )

        val result = taskRepository.findAllByAssignedToIdAndIsCompletedFalse(currentMembership.id!!)

        assertThat(result.map { it.id })
            .containsExactly(assignedToCurrent.id)
            .doesNotContain(
                assignedToOther.id,
                freeTask.id,
                completedByCurrent.id,
            )
    }

    @Test
    fun `deleteAllByHouseholdId should delete only tasks of selected household`() {
        val user = createLocalUserForValidToken()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        val otherMembership = testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
        )

        val taskOne = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )
        val taskTwo = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )
        val otherHouseholdTask = testDataFactory.createTestAssignedTask(
            household = otherHousehold,
            createdBy = user,
            assignedTo = otherMembership,
        )

        val deletedCount = taskRepository.deleteAllByHouseholdId(household.id!!)

        entityManager.flush()
        entityManager.clear()

        assertThat(deletedCount).isEqualTo(2)
        assertThat(taskRepository.findById(taskOne.id!!)).isEmpty()
        assertThat(taskRepository.findById(taskTwo.id!!)).isEmpty()
        assertThat(taskRepository.findById(otherHouseholdTask.id!!)).isPresent
    }
}

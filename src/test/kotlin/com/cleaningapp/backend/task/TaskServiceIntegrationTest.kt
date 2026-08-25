package com.cleaningapp.backend.task

import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.TaskNotFoundException
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.stream.Stream


class TaskServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var clock: Clock

    enum class TaskOperation {
        UPDATE,
        DELETE,
        ASSIGN,
        UNASSIGN,
        COMPLETE,
        GET_BY_ID,
    }

    companion object {
        @JvmStatic
        fun taskOperations(): Stream<TaskOperation> =
            Stream.of(
                TaskOperation.UPDATE,
                TaskOperation.DELETE,
                TaskOperation.ASSIGN,
                TaskOperation.UNASSIGN,
                TaskOperation.COMPLETE,
                TaskOperation.GET_BY_ID,
            )
    }

    private fun executeTaskOperation(
        operation: TaskOperation,
        taskId: UUID,
    ) {
        when (operation) {
            TaskOperation.UPDATE -> taskService.updateTask(
                taskId = taskId,
                newTask = TaskUpdateDTO(
                    title = "Updated task",
                    description = "Updated description",
                    reward = 30,
                )
            )

            TaskOperation.DELETE -> taskService.deleteTask(taskId)

            TaskOperation.ASSIGN -> taskService.assignTask(taskId)

            TaskOperation.UNASSIGN -> taskService.unassignTask(taskId)

            TaskOperation.COMPLETE -> taskService.completeTask(taskId)

            TaskOperation.GET_BY_ID -> taskService.getTaskById(taskId)
        }
    }

    private fun endOfDay(date: LocalDate): LocalDateTime =
        date.atTime(23, 59, 59, 999_999_000)


    @Test
    fun `createTask should create task and activity for active household member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        authenticateAs()

        val result = taskService.createTask(
            householdId = household.id!!,
            task = TaskCreateDTO(
                title = "Wash dishes",
                description = "Wash all dishes after dinner",
                reward = 20,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val savedTask = taskRepository.findById(result.id).orElseThrow()
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(result.householdId).isEqualTo(household.id)
        assertThat(result.createdBy).isEqualTo(user.id)
        assertThat(result.title).isEqualTo("Wash dishes")
        assertThat(result.description).isEqualTo("Wash all dishes after dinner")
        assertThat(result.reward).isEqualTo(20)
        assertThat(result.dueAt).isNull()
        assertThat(result.taskPlanId).isNull()
        assertThat(result.recurrenceType).isNull()
        assertThat(result.recurrenceActive).isFalse()
        assertThat(result.isAssigned).isFalse()
        assertThat(result.assignedTo).isNull()
        assertThat(result.isCompleted).isFalse()
        assertThat(result.completedBy).isNull()

        assertThat(savedTask.household.id).isEqualTo(household.id)
        assertThat(savedTask.createdBy.id).isEqualTo(user.id)
        assertThat(savedTask.title).isEqualTo("Wash dishes")
        assertThat(savedTask.reward).isEqualTo(20)
        assertThat(savedTask.dueAt).isNull()

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.TASK_CREATED)
        assertThat(activities.first { it.activityType == ActivityType.TASK_CREATED }.member.id)
            .isEqualTo(membership.id)
    }

    @Test
    fun `createTask should normalize future due date to end of day`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val dueDate = LocalDate.now(clock).plusDays(3)

        authenticateAs()

        val result = taskService.createTask(
            householdId = household.id!!,
            task = TaskCreateDTO(
                title = "Future task",
                reward = 20,
                dueAt = dueDate.atTime(12, 30),
            )
        )

        entityManager.flush()
        entityManager.clear()

        val savedTask = taskRepository.findById(result.id).orElseThrow()
        val expectedDueAt = endOfDay(dueDate)

        assertThat(result.dueAt).isEqualTo(expectedDueAt)
        assertThat(savedTask.dueAt).isEqualTo(expectedDueAt)
        assertThat(savedTask.dueAt?.nano).isEqualTo(999_999_000)
    }

    @Test
    fun `createTask should allow due date today`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val today = LocalDate.now(clock)

        authenticateAs()

        val result = taskService.createTask(
            householdId = household.id!!,
            task = TaskCreateDTO(
                title = "Today task",
                reward = 20,
                dueAt = today.atStartOfDay(),
            )
        )

        assertThat(result.dueAt).isEqualTo(endOfDay(today))
    }

    @Test
    fun `createTask should reject due date before today`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        authenticateAs()

        assertThatThrownBy {
            taskService.createTask(
                householdId = household.id!!,
                task = TaskCreateDTO(
                    title = "Past task",
                    reward = 20,
                    dueAt = LocalDate.now(clock).minusDays(1).atTime(23, 59),
                )
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Task due date cannot be in the past")
    }

    @Test
    fun `createTask should reject non member`() {
        val currentUser = createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        authenticateAs()

        assertThat(currentUser.id).isNotNull()

        assertThatThrownBy {
            taskService.createTask(
                householdId = household.id!!,
                task = TaskCreateDTO(
                    title = "Foreign task",
                    description = null,
                    reward = 20,
                )
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `createTask should reject inactive household`() {
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
            taskService.createTask(
                householdId = household.id!!,
                task = TaskCreateDTO(
                    title = "Inactive household task",
                    description = null,
                    reward = 20,
                )
            )
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `createTask should reject inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.createTask(
                householdId = household.id!!,
                task = TaskCreateDTO(
                    title = "Inactive membership task",
                    description = null,
                    reward = 20,
                )
            )
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `updateTask should update free task by creator`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )
        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            reward = 20,
        )

        authenticateAs()

        val result = taskService.updateTask(
            taskId = task.id!!,
            newTask = TaskUpdateDTO(
                title = "Updated task",
                description = "Updated description",
                reward = 30,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val updatedTask = taskRepository.findById(task.id!!).orElseThrow()

        assertThat(result.title).isEqualTo("Updated task")
        assertThat(result.description).isEqualTo("Updated description")
        assertThat(result.reward).isEqualTo(30)

        assertThat(updatedTask.title).isEqualTo("Updated task")
        assertThat(updatedTask.description).isEqualTo("Updated description")
        assertThat(updatedTask.reward).isEqualTo(30)
    }

    @Test
    fun `updateTask should set due date`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val task = testDataFactory.createTestFreeTask(household = household, createdBy = user)
        val dueDate = LocalDate.now(clock).plusDays(2)

        authenticateAs()

        val result = taskService.updateTask(
            taskId = task.id!!,
            newTask = TaskUpdateDTO(
                title = task.title,
                description = task.description,
                reward = task.reward,
                dueAt = dueDate.atTime(8, 15),
            )
        )

        entityManager.flush()
        entityManager.clear()

        assertThat(result.dueAt).isEqualTo(endOfDay(dueDate))
        assertThat(taskRepository.findById(task.id!!).orElseThrow().dueAt)
            .isEqualTo(endOfDay(dueDate))
    }

    @Test
    fun `updateTask should change due date`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val originalDueDate = LocalDate.now(clock).plusDays(1)
        val newDueDate = LocalDate.now(clock).plusDays(5)
        val task = testDataFactory.createTestTask(
            household = household,
            createdBy = user,
            dueAt = endOfDay(originalDueDate),
        )

        authenticateAs()

        val result = taskService.updateTask(
            taskId = task.id!!,
            newTask = TaskUpdateDTO(
                title = task.title,
                description = task.description,
                reward = task.reward,
                dueAt = newDueDate.atTime(16, 45),
            )
        )

        entityManager.flush()
        entityManager.clear()

        assertThat(result.dueAt).isEqualTo(endOfDay(newDueDate))
        assertThat(taskRepository.findById(task.id!!).orElseThrow().dueAt)
            .isEqualTo(endOfDay(newDueDate))
    }

    @Test
    fun `updateTask should remove due date when null`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val task = testDataFactory.createTestTask(
            household = household,
            createdBy = user,
            dueAt = endOfDay(LocalDate.now(clock).plusDays(1)),
        )

        authenticateAs()

        val result = taskService.updateTask(
            taskId = task.id!!,
            newTask = TaskUpdateDTO(
                title = task.title,
                description = task.description,
                reward = task.reward,
                dueAt = null,
            )
        )

        entityManager.flush()
        entityManager.clear()

        assertThat(result.dueAt).isNull()
        assertThat(taskRepository.findById(task.id!!).orElseThrow().dueAt).isNull()
    }

    @Test
    fun `updateTask should reject task updated by non creator`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = currentUser, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = creator,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.updateTask(
                taskId = task.id!!,
                newTask = TaskUpdateDTO(
                    title = "Illegal update",
                    description = null,
                    reward = 25,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `updateTask should reject assigned task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.updateTask(
                taskId = task.id!!,
                newTask = TaskUpdateDTO(
                    title = "Cannot update assigned",
                    description = null,
                    reward = 25,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `updateTask should reject completed task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.updateTask(
                taskId = task.id!!,
                newTask = TaskUpdateDTO(
                    title = "Cannot update completed",
                    description = null,
                    reward = 25,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `updateTask should reject nonexistent task`() {
        createLocalUserForValidToken()
        authenticateAs()

        assertThatThrownBy {
            taskService.updateTask(
                taskId = UUID.randomUUID(),
                newTask = TaskUpdateDTO(
                    title = "Missing task",
                    description = null,
                    reward = 20,
                )
            )
        }.isInstanceOf(TaskNotFoundException::class.java)
    }

    @Test
    fun `deleteTask should delete free task by creator`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        authenticateAs()

        taskService.deleteTask(task.id!!)

        entityManager.flush()
        entityManager.clear()

        assertThat(taskRepository.findById(task.id!!)).isEmpty
    }

    @Test
    fun `deleteTask should reject task deleted by non creator`() {
        val currentUser = createLocalUserForValidToken()
        val creator = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = creator)
        testDataFactory.createTestMembership(user = creator, household = household)
        testDataFactory.createTestMembership(user = currentUser, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = creator,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.deleteTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `deleteTask should reject assigned task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.deleteTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `deleteTask should reject completed task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.deleteTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `assignTask should assign free task to current member and create activity`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        authenticateAs()

        val result = taskService.assignTask(task.id!!)

        entityManager.flush()
        entityManager.clear()

        val updatedTask = taskRepository.findById(task.id!!).orElseThrow()
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(result.isAssigned).isTrue()
        assertThat(result.assignedTo).isEqualTo(user.id)
        assertThat(result.assignedAt).isNotNull()
        assertThat(result.isCompleted).isFalse()

        assertThat(updatedTask.assignedTo?.id).isEqualTo(membership.id)
        assertThat(updatedTask.assignedAt).isNotNull()

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.TASK_ASSIGNED)
    }

    @Test
    fun `assignTask should reject completed task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.assignTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `assignTask should reject already assigned task`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        testDataFactory.createTestMembership(user = currentUser, household = household)
        val otherMembership = testDataFactory.createTestMembership(user = otherUser, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = otherMembership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.assignTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `unassignTask should unassign task assigned to current member and create activity`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )

        authenticateAs()

        val result = taskService.unassignTask(task.id!!)

        entityManager.flush()
        entityManager.clear()

        val updatedTask = taskRepository.findById(task.id!!).orElseThrow()
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(result.isAssigned).isFalse()
        assertThat(result.assignedTo).isNull()
        assertThat(result.assignedAt).isNull()
        assertThat(result.isCompleted).isFalse()

        assertThat(updatedTask.assignedTo).isNull()
        assertThat(updatedTask.assignedAt).isNull()

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.TASK_UNASSIGNED)
    }

    @Test
    fun `unassignTask should reject completed task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.unassignTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `unassignTask should reject unassigned task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.unassignTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `unassignTask should reject task assigned to another member`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        testDataFactory.createTestMembership(user = currentUser, household = household)
        val otherMembership = testDataFactory.createTestMembership(user = otherUser, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = otherMembership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.unassignTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `completeTask should complete assigned task reward member create transaction and activity`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 10,
        )

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 25,
        )

        authenticateAs()

        val result = taskService.completeTask(task.id!!)

        entityManager.flush()
        entityManager.clear()

        val updatedTask = taskRepository.findById(task.id!!).orElseThrow()
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()
        val transactions = transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
            household.id!!,
            membership.id!!,
        )
        val activities = activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

        assertThat(result.isCompleted).isTrue()
        assertThat(result.completedBy).isEqualTo(user.id)
        assertThat(result.completedAt).isNotNull()
        assertThat(result.isAssigned).isFalse()
        assertThat(result.assignedTo).isNull()
        assertThat(result.assignedAt).isNull()

        assertThat(updatedTask.isCompleted).isTrue()
        assertThat(updatedTask.completedBy?.id).isEqualTo(membership.id)
        assertThat(updatedTask.completedAt).isNotNull()
        assertThat(updatedTask.assignedTo).isNull()
        assertThat(updatedTask.assignedAt).isNull()

        assertThat(updatedMembership.balance).isEqualTo(35)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.TASK_COMPLETION)
        assertThat(transactions.first().amount).isEqualTo(25)
        assertThat(transactions.first().task?.id).isEqualTo(task.id)

        assertThat(activities.map { it.activityType })
            .contains(ActivityType.TASK_COMPLETED)
    }

    @Test
    fun `completeTask should reject completed task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.completeTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `completeTask should reject unassigned task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.completeTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `completeTask should reject task assigned to another member`() {
        val currentUser = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = currentUser)
        testDataFactory.createTestMembership(user = currentUser, household = household)
        val otherMembership = testDataFactory.createTestMembership(user = otherUser, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = currentUser,
            assignedTo = otherMembership,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.completeTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `completeTask should reject duplicate completion`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 20,
        )

        authenticateAs()

        taskService.completeTask(task.id!!)

        assertThatThrownBy {
            taskService.completeTask(task.id!!)
        }.isInstanceOf(BusinessConflictException::class.java)

        entityManager.flush()
        entityManager.clear()

        val transactions = transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
            household.id!!,
            membership.id!!,
        )
        val updatedMembership = userHouseholdRepository.findById(membership.id!!).orElseThrow()

        assertThat(transactions).hasSize(1)
        assertThat(updatedMembership.balance).isEqualTo(20)
    }

    @Test
    fun `getTaskById should return task for active household member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            reward = 20,
        )

        authenticateAs()

        val result = taskService.getTaskById(task.id!!)

        assertThat(result.id).isEqualTo(task.id)
        assertThat(result.householdId).isEqualTo(household.id)
        assertThat(result.createdBy).isEqualTo(user.id)
        assertThat(result.title).isEqualTo(task.title)
    }

    @Test
    fun `getTaskById should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(user = owner, household = household)

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = owner,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.getTaskById(task.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }


    @Test
    fun `getHouseholdTasks should reject inactive household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.getHouseholdTasks(household.id!!, TaskFilterType.ALL)
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `getHouseholdTasks should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )
        testDataFactory.createTestFreeTask(
            household = household,
            createdBy = owner,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.getHouseholdTasks(household.id!!, TaskFilterType.ALL)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getHouseholdTasks should reject inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )
        testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        authenticateAs()

        assertThatThrownBy {
            taskService.getHouseholdTasks(household.id!!, TaskFilterType.ALL)
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `releaseAssignedTasks should release only unfinished assigned tasks of membership`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)
        val otherMembership = testDataFactory.createTestMembership(user = otherUser, household = household)

        val assignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
        )
        val otherAssignedTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = otherMembership,
        )
        val freeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )

        taskService.releaseAssignedTasks(membership.id!!)

        entityManager.flush()
        entityManager.clear()

        val releasedTask = taskRepository.findById(assignedTask.id!!).orElseThrow()
        val unchangedOtherTask = taskRepository.findById(otherAssignedTask.id!!).orElseThrow()
        val unchangedFreeTask = taskRepository.findById(freeTask.id!!).orElseThrow()

        assertThat(releasedTask.assignedTo).isNull()
        assertThat(releasedTask.assignedAt).isNull()
        assertThat(releasedTask.isCompleted).isFalse()

        assertThat(unchangedOtherTask.assignedTo?.id).isEqualTo(otherMembership.id)
        assertThat(unchangedOtherTask.assignedAt).isNotNull()

        assertThat(unchangedFreeTask.assignedTo).isNull()
        assertThat(unchangedFreeTask.assignedAt).isNull()
    }

    @ParameterizedTest
    @MethodSource("taskOperations")
    fun `task operation should reject non member`(operation: TaskOperation) {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        val ownerMembership = testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        val task = when (operation) {
            TaskOperation.UNASSIGN,
            TaskOperation.COMPLETE -> testDataFactory.createTestAssignedTask(
                household = household,
                createdBy = owner,
                assignedTo = ownerMembership,
            )

            else -> testDataFactory.createTestFreeTask(
                household = household,
                createdBy = owner,
            )
        }

        authenticateAs()

        assertThatThrownBy {
            executeTaskOperation(operation, task.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @ParameterizedTest
    @MethodSource("taskOperations")
    fun `task operation should reject inactive membership`(operation: TaskOperation) {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        val task = when (operation) {
            TaskOperation.UNASSIGN,
            TaskOperation.COMPLETE -> testDataFactory.createTestAssignedTask(
                household = household,
                createdBy = user,
                assignedTo = membership,
            )

            else -> testDataFactory.createTestFreeTask(
                household = household,
                createdBy = user,
            )
        }

        authenticateAs()

        assertThatThrownBy {
            executeTaskOperation(operation, task.id!!)
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @ParameterizedTest
    @MethodSource("taskOperations")
    fun `task operation should reject inactive household`(operation: TaskOperation) {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = true,
        )

        val task = when (operation) {
            TaskOperation.UNASSIGN,
            TaskOperation.COMPLETE -> testDataFactory.createTestAssignedTask(
                household = household,
                createdBy = user,
                assignedTo = membership,
            )

            else -> testDataFactory.createTestFreeTask(
                household = household,
                createdBy = user,
            )
        }

        authenticateAs()

        assertThatThrownBy {
            executeTaskOperation(operation, task.id!!)
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    // тест сортировки
    @Test
    fun `getHouseholdTasks should return tasks sorted according to filter contract`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val baseTime = LocalDateTime.now(clock)

        val olderFreeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            reward = 10,
        )
        val newerFreeTask = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            reward = 20,
        )

        val olderMyTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 30,
        )
        val newerMyTask = testDataFactory.createTestAssignedTask(
            household = household,
            createdBy = user,
            assignedTo = membership,
            reward = 40,
        )

        val olderCompletedTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            reward = 50,
        )
        val newerCompletedTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            reward = 60,
        )

        testDataFactory.updateTaskTimestamps(
            taskId = olderFreeTask.id!!,
            createdAt = baseTime.minusDays(6),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = newerFreeTask.id!!,
            createdAt = baseTime.minusDays(1),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = olderMyTask.id!!,
            createdAt = baseTime.minusDays(5),
            assignedAt = baseTime.minusDays(4),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = newerMyTask.id!!,
            createdAt = baseTime.minusDays(4),
            assignedAt = baseTime.minusDays(2),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = olderCompletedTask.id!!,
            createdAt = baseTime.minusDays(3),
            completedAt = baseTime.minusDays(3),
        )
        testDataFactory.updateTaskTimestamps(
            taskId = newerCompletedTask.id!!,
            createdAt = baseTime.minusDays(2),
            completedAt = baseTime.minusDays(1),
        )

        authenticateAs()

        val all = taskService.getHouseholdTasks(household.id!!, TaskFilterType.ALL)
        val free = taskService.getHouseholdTasks(household.id!!, TaskFilterType.FREE)
        val my = taskService.getHouseholdTasks(household.id!!, TaskFilterType.MY)
        val completed = taskService.getHouseholdTasks(household.id!!, TaskFilterType.COMPLETED)

        assertThat(all.map { it.id })
            .containsExactly(
                newerFreeTask.id,
                newerCompletedTask.id,
                olderCompletedTask.id,
                newerMyTask.id,
                olderMyTask.id,
                olderFreeTask.id,
            )

        assertThat(free.map { it.id })
            .containsExactly(
                newerFreeTask.id,
                olderFreeTask.id,
            )

        assertThat(my.map { it.id })
            .containsExactly(
                newerMyTask.id,
                olderMyTask.id,
            )

        assertThat(completed.map { it.id })
            .containsExactly(
                newerCompletedTask.id,
                olderCompletedTask.id,
            )
    }

    @Test
    fun `getHouseholdTasks FREE should return at most 150 newest tasks`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val tasks = (1..151).map { index ->
            val task = testDataFactory.createTestFreeTask(
                household = household,
                createdBy = user,
                reward = 20,
            )

            testDataFactory.updateTaskTimestamps(
                taskId = task.id!!,
                createdAt = baseTime.plusMinutes(index.toLong()),
            )

            task
        }

        authenticateAs()

        val result = taskService.getHouseholdTasks(household.id!!, TaskFilterType.FREE)

        assertThat(result).hasSize(150)
        assertThat(result.first().id).isEqualTo(tasks.last().id)
        assertThat(result.last().id).isEqualTo(tasks[1].id)
        assertThat(result.map { it.id }).doesNotContain(tasks.first().id)
    }

    @Test
    fun `task responses should calculate overdue state`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val membership = testDataFactory.createTestMembership(user = user, household = household)
        val now = LocalDateTime.now(clock)

        val withoutDeadline = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
        )
        val future = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = now.plusDays(1),
        )
        val today = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = endOfDay(LocalDate.now(clock)),
        )
        val overdue = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            dueAt = now.minusDays(1),
        )
        val completedPast = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = membership,
            dueAt = now.minusDays(2),
        )

        authenticateAs()

        val results = taskService.getHouseholdTasks(household.id!!, TaskFilterType.ALL)
            .associateBy { it.id }

        assertThat(results.getValue(withoutDeadline.id!!).isOverdue).isFalse()
        assertThat(results.getValue(future.id!!).isOverdue).isFalse()
        assertThat(results.getValue(today.id!!).isOverdue).isFalse()
        assertThat(results.getValue(overdue.id!!).isOverdue).isTrue()
        assertThat(results.getValue(completedPast.id!!).isOverdue).isFalse()
    }

    @Test
    fun `getHouseholdTasks WITH_DEADLINE should return at most 150 earliest deadlines`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val now = LocalDateTime.now(clock)

        val tasks = (1..151).map { index ->
            testDataFactory.createTestFreeTask(
                household = household,
                createdBy = user,
                dueAt = now.plusMinutes(index.toLong()),
            )
        }

        authenticateAs()

        val result = taskService.getHouseholdTasks(household.id!!, TaskFilterType.WITH_DEADLINE)

        assertThat(result).hasSize(150)
        assertThat(result.first().id).isEqualTo(tasks.first().id)
        assertThat(result.last().id).isEqualTo(tasks[149].id)
        assertThat(result.map { it.id }).doesNotContain(tasks.last().id)
    }

    @Test
    fun `getHouseholdTasks OVERDUE should return at most 150 oldest deadlines`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(user = user, household = household)
        val now = LocalDateTime.now(clock)

        val tasks = (1..151).map { index ->
            testDataFactory.createTestFreeTask(
                household = household,
                createdBy = user,
                dueAt = now.minusMinutes((152 - index).toLong()),
            )
        }

        authenticateAs()

        val result = taskService.getHouseholdTasks(household.id!!, TaskFilterType.OVERDUE)

        assertThat(result).hasSize(150)
        assertThat(result.first().id).isEqualTo(tasks.first().id)
        assertThat(result.last().id).isEqualTo(tasks[149].id)
        assertThat(result.map { it.id }).doesNotContain(tasks.last().id)
        assertThat(result).allMatch { it.isOverdue }
    }
}

package com.cleaningapp.backend.base

import com.cleaningapp.backend.activity.ActivityEntity
import com.cleaningapp.backend.activity.ActivityRepository
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.privilege.PrivilegeEntity
import com.cleaningapp.backend.privilege.PrivilegeRepository
import com.cleaningapp.backend.task.TaskEntity
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.transaction.TransactionEntity
import com.cleaningapp.backend.transaction.TransactionRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.springframework.boot.test.context.TestComponent
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// вспомогательный компонент
// создает нужные сущности для тестовой бд
// все id, имена и тд рандомные тестовые

@TestComponent
class TestDataFactory(
    private val userRepository: UserRepository,
    private val householdRepository: HouseholdRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
    private val taskRepository: TaskRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val transactionRepository: TransactionRepository,
    private val activityRepository: ActivityRepository,

    private val clock: Clock,
    private val jdbcTemplate: JdbcTemplate,
    private val entityManager: EntityManager,
) {

    // проверка принадлежности участия хозяйству
    private fun requireSameHousehold(
        household: HouseholdEntity,
        member: UserHouseholdEntity?,
    ) {
        if (member == null) return

        require(member.household.id == household.id) {
            "Member ${member.id} belongs to household ${member.household.id}, but expected ${household.id}"
        }
    }
    // проверка принадлежности задачи хозяйству
    private fun requireSameHousehold(
        household: HouseholdEntity,
        task: TaskEntity,
    ) {
        require(task.household.id == household.id) {
            "Task ${task.id} belongs to household ${task.household.id}, but expected ${household.id}"
        }
    }
    // проверка принадлежности привилегии хозяйству
    private fun requireSameHousehold(
        household: HouseholdEntity,
        privilege: PrivilegeEntity,
    ) {
        require(privilege.household.id == household.id) {
            "Privilege ${privilege.id} belongs to household ${privilege.household.id}, but expected ${household.id}"
        }
    }


    // создать и сохранить активного юзера
    fun createTestUser(
        firebaseUid: String = "firebase-uid-${UUID.randomUUID()}",
        email: String = "${UUID.randomUUID()}@test.com",
        name: String = "Test user",
        avatarUrl: String? = null,
        isActive: Boolean = true,
    ): UserEntity =
        userRepository.save(
            UserEntity(
                firebaseUid = firebaseUid,
                email = email,
                name = name,
                avatarUrl = avatarUrl,
                isActive = isActive,
            )
        )

    // создать активное хозяйство (с передачей создателя)
    // не создает участие - защита от ложноположительного теста на создание в сервисе участия
    fun createTestHousehold(
        createdBy: UserEntity = createTestUser(),
        name: String = "Test household",
        inviteCode: String = uniqueInviteCode(),
        isActive: Boolean = true
    ): HouseholdEntity {

        val household = HouseholdEntity(
            name = name,
            inviteCode = inviteCode,
            isActive = isActive,
        )

        household.createdByUser = createdBy

        return householdRepository.save(household)
    }

    private fun uniqueInviteCode(): String =
        UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(8)
            .uppercase()


    // создать участие
    fun createTestMembership(
        user: UserEntity = createTestUser(),
        household: HouseholdEntity = createTestHousehold(createdBy = user),
        balance: Int = 0,
        isUserActive: Boolean = true,
    ): UserHouseholdEntity {
        require(balance >= 0) {
            "balance must be non-negative"
        }

        val membership = UserHouseholdEntity(
            balance = balance,
            isUserActive = isUserActive,
        )

        membership.user = user
        membership.household = household

        return userHouseholdRepository.save(membership)
    }

    // создать задачу в заданном хозяйстве
    // создать задачи - создатель хозяйства
    // задача общего вида
    fun createTestTask(
        household: HouseholdEntity,
        createdBy: UserEntity = household.createdByUser,

        title: String = "Test task",
        description: String? = "Test task description",
        reward: Int = 20,
        dueAt: LocalDateTime? = null,

        assignedTo: UserHouseholdEntity? = null,
        assignedAt: LocalDateTime? = if (assignedTo != null) LocalDateTime.now(clock) else null,

        isCompleted: Boolean = false,
        completedBy: UserHouseholdEntity? = null,
        completedAt: LocalDateTime? = if (completedBy != null) LocalDateTime.now(clock) else null,
    ): TaskEntity {

        requireSameHousehold(household, assignedTo)
        requireSameHousehold(household, completedBy)

        require((assignedTo == null) == (assignedAt == null)) {
            "assignedTo and assignedAt must be both null or both not null"
        }

        require((completedBy == null) == (completedAt == null)) {
            "completedBy and completedAt must be both null or both not null"
        }

        require(!isCompleted || completedBy != null) {
            "Completed task must have completedBy"
        }

        require(!isCompleted || assignedTo == null) {
            "Completed task must not stay assigned"
        }

        require(reward in 5..100) {
            "reward must be between 5 and 100"
        }

        val task = TaskEntity(
            title = title,
            description = description,
            reward = reward,
            dueAt = dueAt,

            assignedAt = assignedAt,
            isCompleted = isCompleted,
            completedAt = completedAt,
        )

        task.household = household
        task.createdBy = createdBy
        task.assignedTo = assignedTo
        task.completedBy = completedBy

        return taskRepository.save(task)
    }

    // создать свободную садачу в заданном хозяйстве
    fun createTestFreeTask(
        household: HouseholdEntity,
        createdBy: UserEntity = household.createdByUser,
        reward: Int = 20,
        dueAt: LocalDateTime? = null,
): TaskEntity =
        createTestTask(
            household = household,
            createdBy = createdBy,
            reward = reward,
            dueAt = dueAt,
            assignedTo = null,
            assignedAt = null,
            isCompleted = false,
            completedBy = null,
            completedAt = null,
        )

    // создать забронированную задачу
    fun createTestAssignedTask(
        household: HouseholdEntity,
        createdBy: UserEntity = household.createdByUser,
        reward: Int = 20,
        assignedTo: UserHouseholdEntity,
        dueAt: LocalDateTime? = null,
    ): TaskEntity {

        requireSameHousehold(household, assignedTo)

        return createTestTask(
            household = household,
            createdBy = createdBy,
            reward = reward,
            dueAt = dueAt,
            assignedTo = assignedTo,
            assignedAt = LocalDateTime.now(clock),
            isCompleted = false,
            completedBy = null,
            completedAt = null,
        )
    }

    // создать выполненную задачу
    fun createTestCompletedTask(
        household: HouseholdEntity,
        createdBy: UserEntity = household.createdByUser,
        reward: Int = 20,
        completedBy: UserHouseholdEntity,
        dueAt: LocalDateTime? = null,
    ): TaskEntity {

        requireSameHousehold(household, completedBy)

        return createTestTask(
            household = household,
            createdBy = createdBy,
            reward = reward,
            dueAt = dueAt,
            assignedTo = null,
            assignedAt = null,
            isCompleted = true,
            completedBy = completedBy,
            completedAt = LocalDateTime.now(clock),
        )
    }


    // создать привилегию в заданном хозяйстве
    fun createTestPrivilege(
        household: HouseholdEntity,
        createdBy: UserEntity = household.createdByUser,
        title: String = "Test privilege",
        description: String? = "Test privilege description",
        cost: Int = 50,
        isAvailable: Boolean = true,
        boughtBy: UserHouseholdEntity? = null,
    ): PrivilegeEntity {

        requireSameHousehold(household, boughtBy)
        require(cost in 5..500) {
            "cost must be between 5 and 500"
        }
        require((isAvailable && boughtBy == null) || (!isAvailable && boughtBy != null)) {
            "Privilege availability and boughtBy must be consistent"
        }

        val privilege = PrivilegeEntity(
            title = title,
            description = description,
            cost = cost,
            isAvailable = isAvailable,
        )
        privilege.household = household
        privilege.createdBy = createdBy
        privilege.boughtBy = boughtBy

        return privilegeRepository.save(privilege)
    }



    // создать транзакцию выполнения задачи
    // не меняет баланс
    fun createTestTaskCompletionTransaction(
        household: HouseholdEntity,
        member: UserHouseholdEntity,
        task: TaskEntity,
        amount: Int = task.reward,
        createdAt: LocalDateTime = LocalDateTime.now(clock),
    ): TransactionEntity {

        requireSameHousehold(household, member)
        requireSameHousehold(household, task)

        val transaction = TransactionEntity(
            amount = amount,
            type = TransactionType.TASK_COMPLETION,
        )

        transaction.household = household
        transaction.member = member
        transaction.task = task
        transaction.privilege = null

        val saved = transactionRepository.saveAndFlush(transaction)

        // поле createdAt - CreationTimestamp - не сможем изначально вставить по clock
        // меняем через update jdbcTemplate
        val rowsUpdated = jdbcTemplate.update(
            """
                update "transaction"
                set created_at = ?
                where id = ?
            """.trimIndent(),
            createdAt,
            saved.id,
        )
        require(rowsUpdated == 1) {
            "Expected to update exactly one transaction row for id=${saved.id}, but updated $rowsUpdated rows"
        }
        entityManager.clear()

        return transactionRepository.findById(saved.id!!).orElseThrow()
    }

    // транзакция покупки привилегии
    // не меняет баланс
    fun createTestPrivilegePurchaseTransaction(
        household: HouseholdEntity,
        member: UserHouseholdEntity,
        privilege: PrivilegeEntity,
        amount: Int = -privilege.cost,
        createdAt: LocalDateTime = LocalDateTime.now(clock),
    ): TransactionEntity {

        requireSameHousehold(household, member)
        requireSameHousehold(household, privilege)

        val transaction = TransactionEntity(
            amount = amount,
            type = TransactionType.PRIVILEGE_BOUGHT,
        )

        transaction.household = household
        transaction.member = member
        transaction.task = null
        transaction.privilege = privilege

        val saved = transactionRepository.saveAndFlush(transaction)

        // поле createdAt - CreationTimestamp - не сможем изначально вставить по clock
        // меняем через update jdbcTemplate
        val rowsUpdated = jdbcTemplate.update(
            """
                update "transaction"
                set created_at = ?
                where id = ?
            """.trimIndent(),
            createdAt,
            saved.id,
        )
        require(rowsUpdated == 1) {
            "Expected to update exactly one transaction row for id=${saved.id}, but updated $rowsUpdated rows"
        }
        entityManager.clear()

        return transactionRepository.findById(saved.id!!).orElseThrow()
    }

    // транзакция ресета баланса
    // не меняет баланс сама
    fun createTestBalanceResetTransaction(
        household: HouseholdEntity,
        member: UserHouseholdEntity,
        amount: Int = -member.balance,
        createdAt: LocalDateTime = LocalDateTime.now(clock),
    ): TransactionEntity {

        requireSameHousehold(household, member)

        val transaction = TransactionEntity(
            amount = amount,
            type = TransactionType.BALANCE_RESET,
        )

        transaction.household = household
        transaction.member = member
        transaction.task = null
        transaction.privilege = null

        val saved = transactionRepository.saveAndFlush(transaction)

        // поле createdAt - CreationTimestamp - не сможем изначально вставить по clock
        // меняем через update jdbcTemplate
        val rowsUpdated = jdbcTemplate.update(
            """
                update "transaction"
                set created_at = ?
                where id = ?
            """.trimIndent(),
            createdAt,
            saved.id,
        )
        require(rowsUpdated == 1) {
            "Expected to update exactly one transaction row for id=${saved.id}, but updated $rowsUpdated rows"
        }
        entityManager.clear()

        return transactionRepository.findById(saved.id!!).orElseThrow()
    }

    // событие активности в заданно хозяйстве
    fun createTestActivity(
        household: HouseholdEntity,
        member: UserHouseholdEntity,
        activityType: ActivityType,
        title: String = "Test activity",
        description: String? = "Test activity description"
    ): ActivityEntity {

        requireSameHousehold(household, member)

        val activity = ActivityEntity(
            activityType = activityType,
            title = title,
            description = description,
        )

        activity.household = household
        activity.member = member

        return activityRepository.save(activity)
    }

    // изменение времени
    fun updateTaskTimestamps(
        taskId: UUID,
        createdAt: LocalDateTime? = null,
        assignedAt: LocalDateTime? = null,
        completedAt: LocalDateTime? = null,
    ): TaskEntity {

        entityManager.flush()

        createdAt?.let {
            jdbcTemplate.update(
                """
                update task
                set created_at = ?
                where id = ?
            """.trimIndent(),
                it,
                taskId,
            )
        }

        assignedAt?.let {
            jdbcTemplate.update(
                """
                update task
                set assigned_at = ?
                where id = ?
            """.trimIndent(),
                it,
                taskId,
            )
        }

        completedAt?.let {
            jdbcTemplate.update(
                """
                update task
                set completed_at = ?
                where id = ?
            """.trimIndent(),
                it,
                taskId,
            )
        }

        entityManager.clear()

        return taskRepository.findById(taskId).orElseThrow()
    }

    // для стабильной проверки сортировки по createdAt DESC
    fun updatePrivilegeCreatedAt(
        privilegeId: UUID,
        createdAt: LocalDateTime,
    ): PrivilegeEntity {

        entityManager.flush()

        val rowsUpdated = jdbcTemplate.update(
            """
            update privilege
            set created_at = ?
            where id = ?
        """.trimIndent(),
            createdAt,
            privilegeId,
        )

        require(rowsUpdated == 1) {
            "Expected to update exactly one privilege row for id=$privilegeId, but updated $rowsUpdated rows"
        }

        entityManager.clear()

        return privilegeRepository.findById(privilegeId).orElseThrow()
    }

    // для стабильной проверки сортировки activity по createdAt DESC
    fun updateActivityCreatedAt(
        activityId: UUID,
        createdAt: LocalDateTime,
    ): ActivityEntity {
        entityManager.flush()

        val rowsUpdated = jdbcTemplate.update(
            """
            update activity
            set created_at = ?
            where id = ?
        """.trimIndent(),
            createdAt,
            activityId,
        )

        require(rowsUpdated == 1) {
            "Expected to update exactly one activity row for id=$activityId, but updated $rowsUpdated rows"
        }

        entityManager.clear()

        return activityRepository.findById(activityId).orElseThrow()
    }

    // для стабильной проверки технической сортировки лидерборда по joinedAt DESC
    fun updateMembershipJoinedAt(
        membershipId: UUID,
        joinedAt: LocalDateTime,
    ): UserHouseholdEntity {
        entityManager.flush()

        val rowsUpdated = jdbcTemplate.update(
            """
            update user_household
            set joined_at = ?
            where id = ?
        """.trimIndent(),
            joinedAt,
            membershipId,
        )

        require(rowsUpdated == 1) {
            "Expected to update exactly one membership row for id=$membershipId, but updated $rowsUpdated rows"
        }

        entityManager.clear()

        return userHouseholdRepository.findById(membershipId).orElseThrow()
    }
}

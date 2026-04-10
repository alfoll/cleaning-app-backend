package com.cleaningapp.backend.transaction

import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.PrivilegeNotFoundException
import com.cleaningapp.backend.exception.TaskNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.privilege.PrivilegeEntity
import com.cleaningapp.backend.privilege.PrivilegeRepository
import com.cleaningapp.backend.task.TaskEntity
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class TransactionServiceImpl(
    private val transactionRepository: TransactionRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val taskRepository: TaskRepository,
    private val privilegeRepository: PrivilegeRepository,
): TransactionService {

    // достать юзера из контекста
    // для публичных методов чтения транзакций пользователя
    private fun getCurrentUser(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("User not authenticated")

        val firebaseUid = auth.name

        val user = userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw UserNotFoundException()

        if (!user.isActive)
            throw UserNotActiveException()
        return user
    }

    // достать активное хозяйсто
    // для публичных методов, где хозяйство приходит из апи
    private fun getActiveHousehold(householdId: UUID): HouseholdEntity {
        // существует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }

    // досать активную связь
    //для публичных методов для проверки доступа
    private fun getActiveMembership(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(userId, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return userHousehold
    }

    // достать связь без проверки активности
    private fun getMembershipEntity(memberId: UUID): UserHouseholdEntity =
        userHouseholdRepository.findByIdOrNull(memberId)
            ?: throw MembershipNotFoundException()

    // достать активную связь по member_id
    // для записей
    private fun getActiveMembershipEntity(memberId: UUID): UserHouseholdEntity {
        val membership = userHouseholdRepository.findByIdOrNull(memberId)
            ?: throw MembershipNotFoundException()

        if (!membership.isUserActive)
            throw MembershipNotActiveException()

        if (!membership.household.isActive)
            throw HouseholdNotActiveException()

        return membership
    }

    // достать сущность задачи
    private fun getTaskEntity(taskId: UUID): TaskEntity =
        taskRepository.findByIdOrNull(taskId)
            ?: throw TaskNotFoundException()

    // достать сущность привилегии
    private fun getPrivilegeEntity(privilegeId: UUID): PrivilegeEntity =
        privilegeRepository.findByIdOrNull(privilegeId)
            ?: throw PrivilegeNotFoundException()

    // создание транзакции
    private fun buildTransaction(
        amount: Int,
        type: TransactionType,
        household: HouseholdEntity,
        member: UserHouseholdEntity,
        task: TaskEntity? = null,
        privilege: PrivilegeEntity? = null): TransactionEntity {

        when (type) {

            TransactionType.TASK_COMPLETION -> {
                check(task != null) { "TASK_COMPLETION transaction must contain task" }
                check(privilege == null) { "TASK_COMPLETION transaction must not contain privilege" }
            }

            TransactionType.PRIVILEGE_BOUGHT -> {
                check(privilege != null) { "PRIVILEGE_BOUGHT transaction must contain privilege" }
                check(task == null) { "PRIVILEGE_BOUGHT transaction must not contain task" }
            }

            TransactionType.BALANCE_RESET -> {
                check(task == null) { "BALANCE_RESET transaction must not contain task" }
                check(privilege == null) { "BALANCE_RESET transaction must not contain privilege" }
            }
        }

        return TransactionEntity(
            amount = amount,
            type = type,
        ).apply {
            this.household = household
            this.member = member
            this.task = task
            this.privilege = privilege
        }
    }



    // транзакция может быть создана только на выполненную задачу, у которой еще нет транзакции
    override fun recordTaskCompletion(command: TaskCompletionTransactionCommand) {
        // достать задачу
        val task = getTaskEntity(command.taskId)

        // достать участника
        val member = getActiveMembershipEntity(command.memberId)

        // проверить что участник состоит в переданном хозяйстве (id хозяйства из команды и из участия совпадают)
        if (member.household.id != command.householdId)
            throw BusinessConflictException("Membership does not belong to the specified household")

        // проверить что задача находится в переданном хозяйстве (id хозяйства из команды и из задачи совпадают)
        if (task.household.id != command.householdId)
            throw BusinessConflictException("Task does not belong to the specified household")

        // задача действительно выполнена
        if (!task.isCompleted)
            throw BusinessConflictException("Task must be completed before creating a transaction")

        // задача выполнена тем участником на которого будет транзакция (id выполнившего = id переданного мембера)
        if (task.completedBy?.id != member.id)
            throw BusinessConflictException("Task completedBy does not match transaction member")

        // доп проверка на начисление
        if (task.reward <= 0)
            throw BusinessConflictException("Task reward must be positive")

        // нет ли уже транзакции на эту задачу (защита от двойного начисления)
        if (transactionRepository.existsByTaskId(command.taskId))
            throw BusinessConflictException("Transaction for this task already exists")

        // провести начисление и сохранить с
        val amount = task.reward
        member.balance += amount
//        userHouseholdRepository.save(member) // managed entity

        transactionRepository.save( // сохранение оставить - новая сущность
            buildTransaction(
                amount = amount,
                type = TransactionType.TASK_COMPLETION,
                household = member.household,
                member = member,
                task = task,
            )
        )
    }

    // транзакция может быть создана только на купленную привилегию, у которой еще нет транзакции
    override fun recordPrivilegePurchase(command: PrivilegePurchaseTransactionCommand) {
        // достать привилегию
        val privilege = getPrivilegeEntity(command.privilegeId)

        // достать участника
        val member = getActiveMembershipEntity(command.memberId)

        // проверить что участник состоит в переданном хозяйстве (id хозяйства из команды и из участия совпадают)
        if (member.household.id != command.householdId)
            throw BusinessConflictException("Membership does not belong to the specified household")

        // проверить что привилегия находится в переданном хозяйстве (id хозяйства из команды и из привилегии совпадают)
        if (privilege.household.id != command.householdId)
            throw BusinessConflictException("Privilege does not belong to the specified household")

        // привилегия должна быть отмечена купленной
        if (privilege.isAvailable)
            throw BusinessConflictException("Privilege must be marked unavailable before creating a transaction")

        // привилегия куплена тем участником на которого будет транзакция (id купившего = id переданного мембера)
        if (privilege.boughtBy?.id != member.id)
            throw BusinessConflictException("Privilege boughtBy does not match transaction member")

        // доп проверка на стоимость
        if (privilege.cost <= 0)
            throw BusinessConflictException("Privilege cost must be positive")

        // нет ли уже транзакции на эту задачу (защита от двойного начисления)
        if (transactionRepository.existsByPrivilegeId(command.privilegeId))
            throw BusinessConflictException("Transaction for this privilege already exists")

        // провести начисление и сохранить с
        val amount = -privilege.cost

        // на всякий члучай проверка
        if (member.balance + amount < 0)
            throw BusinessConflictException("Balance cannot become negative")

        member.balance += amount
//        userHouseholdRepository.save(member) // managed entity

        transactionRepository.save( // сохранение оставить - новая сущность
            buildTransaction(
                amount = amount,
                type = TransactionType.PRIVILEGE_BOUGHT,
                household = member.household,
                member = member,
                privilege = privilege,
            )
        )
    }

    // вычитается весь баланс, достается из связи
    // служебная операция, применяется при удалении/выходе -> активность хозяйства или участия не нужна
    override fun resetBalance(command: BalanceResetTransactionCommand) {
        // достать участника у которого обнуляется баланс (не обязательно активного)
        val member = getMembershipEntity(command.memberId)

        // проверить что участник состоит в переданном хозяйстве (id хозяйства из команды и из участия совпадают)
        if (member.household.id != command.householdId)
            throw BusinessConflictException("Membership does not belong to the specified household")

        // проверить что баланс неотрицательный - иначе нельзя списывать
        if (member.balance < 0)
            throw BusinessConflictException("Balance cannot be negative before reset")

        // если баланс уже 0 - выйти
        if (member.balance == 0)
            return

        // обнулисть баланс и сохранить
        val amount = -member.balance
        member.balance = 0
//        userHouseholdRepository.save(member) // managed entity

        transactionRepository.save( // сохранение оставить - новая сущность
            buildTransaction(
                amount = amount,
                type = TransactionType.BALANCE_RESET,
                household = member.household,
                member = member,
            )
        )
    }


    // пользователь может видеть только свои транзакции
    @Transactional(readOnly = true)
    override fun getMyTransactions(householdId: UUID): List<TransactionResponseDTO> {
        // достать юзера
        val user = getCurrentUser()

        // достать ктивное хозяйство
        val household = getActiveHousehold(householdId)

        // валидировать участие
        val membership = getActiveMembership(user.id!!, household.id!!)

        // достать историю транзакций участника
        return transactionRepository
            .findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                membership.id!!
            ).map { it.toDto() }
    }
}
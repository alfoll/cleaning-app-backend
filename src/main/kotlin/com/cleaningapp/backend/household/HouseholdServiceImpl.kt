package com.cleaningapp.backend.household

import com.cleaningapp.backend.activity.ActivityService
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.activity.RecordActivityCommand
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.transaction.BalanceResetTransactionCommand
import com.cleaningapp.backend.transaction.TransactionService
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
class HouseholdServiceImpl(
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val userHouseholdRepository: UserHouseholdRepository,

    private val taskService: TaskService,
    private val transactionService: TransactionService,
    private val activityService: ActivityService,
) : HouseholdService {

    // генерация кода из символов
    private fun generateCode(): String {
        val chars = ('A'..'Z') + ('a' .. 'z') + ('0' .. '9')
        return (1 .. 8)
            .map { chars.random() }
            .joinToString("")
    }

    // попытка сгенерировать уникальный inviteCode
    private fun generateInviteCode(): String {
        repeat(10) {
            val code = generateCode()

            if (!householdRepository.existsByInviteCode(code)) {
                return code
            }
        }
        throw BusinessConflictException("Failed to generate unique invite code")
    }

    // доостать юзера из контекста
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

    override fun createHousehold(household: HouseholdRegisterDTO): HouseholdResponseDTO {
        // достать создателя
        val user = getCurrentUser()

        // проверить лимит в 3 хозяйства у юзера
        if (userHouseholdRepository.countByUserIdAndIsUserActiveTrue(user.id!!) >= 3)
            throw BusinessConflictException("User's household limit reached")

        // сгенерировать inviteCode (он будет уникален - проверка на существование хозяйства не нужна тут)
        val code = generateInviteCode()

        // создать хозяйство и сохранить
        val householdEntity = household.toHouseholdEntity(user).apply { inviteCode = code }
        val saved = householdRepository.save(householdEntity)

        // сразу создать связь создателя и хозяйства
        val userHousehold = UserHouseholdEntity()
            .apply {
                this.user = user
                this.household = householdEntity
            }
        val savedUserHousehold = userHouseholdRepository.save(userHousehold)

        // создаем запись HOUSEHOLD_CREATED в ленте активности
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = saved.id!!,
                memberId = savedUserHousehold.id!!,
                activityType = ActivityType.HOUSEHOLD_CREATED,
                title = "Household Created",
                description = "Household \"${saved.name}\" was created"
            )
        )

        // создаем запись USER_JOINED в ленте активности - юзер сразу присоединяется к созданному хозяйству
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = saved.id!!,
                memberId = savedUserHousehold.id!!,
                activityType = ActivityType.USER_JOINED,
                title = "User joined",
                description = "${user.name} joined household \"${saved.name}\""
            )
        )

        return saved.toDto()
    }

    override fun deleteHousehold(householdId: UUID) {
        // существует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // состоит ли юзер который удаляет хозяйство в нем
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository
            .findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        // деактивировать активные связи хозяйства обнулить баланс
        // найти участников
        val members = userHouseholdRepository.findAllByHouseholdIdAndIsUserActiveTrue(household.id!!)

        for (member in members) {
            // освободить задачи
            taskService.releaseAssignedTasks(member.id!!)

            // обнулить баланс
            transactionService.resetBalance(
                BalanceResetTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                )
            )

            // деактивировать связь
            member.isUserActive = false

            // сохранить изменения - транзакционный сервис, сохранять не нужно
//            userHouseholdRepository.save(member) // managed entity
        }

        // деактивировать хозяйство
        household.isActive = false
//        householdRepository.save(household)
    }

    override fun updateHousehold(householdId: UUID, newHousehold: HouseholdRegisterDTO): HouseholdResponseDTO {
        // существует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // состоит ли юзер в этом хозяйстве
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository
            .findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        // обновить название (обновляться может только оно) и сохранить
        household.name = newHousehold.name

//        return householdRepository.save(household).toDto() // managed entity
        return household.toDto()
    }

    override fun findHouseholdByInviteCode(inviteCode: String): HouseholdResponseDTO {
        val household = householdRepository.findByInviteCode(inviteCode)
            ?: throw HouseholdNotFoundException()

        // проверить активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // проверить состояит ли пользователь в хозяйстве
        // - не нужно так как пользователь ищет хозяйство по инвайт коду при втуплении в него

        return household.toDto()
    }

    override fun findHouseholdById(id: UUID): HouseholdResponseDTO {
        val household = householdRepository.findByIdOrNull(id)
            ?: throw HouseholdNotFoundException()

        // проверить активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // проверить состояит ли пользователь в хозяйстве и активен ли в нем
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw MembershipNotFoundException()

        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return household.toDto()
    }

}
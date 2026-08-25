package com.cleaningapp.backend.household

import com.cleaningapp.backend.activity.ActivityRepository
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
import com.cleaningapp.backend.privilege.PrivilegeRepository
import com.cleaningapp.backend.task.TaskRepository
import com.cleaningapp.backend.taskplan.TaskPlanRepository
import com.cleaningapp.backend.tasktemplate.TaskTemplateRepository
import com.cleaningapp.backend.transaction.TransactionRepository
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
    private val transactionRepository: TransactionRepository,
    private val activityRepository: ActivityRepository,
    private val taskRepository: TaskRepository,
    private val taskPlanRepository: TaskPlanRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val privilegeRepository: PrivilegeRepository,

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

    // достать юзера из контекста - для read сценариев
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
    // с блокировкой для write сценариев
    private fun getCurrentUserForUpdate(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("User not authenticated")

        val firebaseUid = auth.name

        val user = userRepository.findUserByFirebaseUidForUpdate(firebaseUid)
            ?: throw UserNotFoundException()

        if (!user.isActive)
            throw UserNotActiveException()
        return user
    }

    // достать активное хозяйсто - для read сценариев
    private fun getActiveHousehold(householdId: UUID): HouseholdEntity {
        // существует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }
    // с блокировкой для write сценариев
    private fun getActiveHouseholdForUpdate(householdId: UUID): HouseholdEntity {
        // существует ли хозяйство
        val household = householdRepository.findByIdForUpdate(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }

    // досать активную связь - для read сценариев
    private fun getActiveMembership(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(userId, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return userHousehold
    }
    // с блокировкой для write сценариев
    private fun getActiveMembershipForUpdate(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdIdForUpdate(userId, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return userHousehold
    }


    // полное удаление хозяйства (жесткое удаление транзакций потом активности + задач + привилегий)
    // вызов только после блокировки household и userHousehold
    private fun deleteHouseholdInternal(household: HouseholdEntity) {
        // деактивировать хозяйство
        household.isActive = false

        // найти участников (всех, а не только активных - на всякий случай)
        val members = userHouseholdRepository.findAllByHouseholdIdForUpdate(household.id!!)

        // всех деактивировать и обнулить балансы
        members.forEach {
            it.isUserActive = false
            it.balance = 0
        }

        // удалить транзакции хозяйства
        transactionRepository.deleteAllByHouseholdId(household.id!!)
        // удалить активность хозяйства
        activityRepository.deleteAllByHouseholdId(household.id!!)
        // удалить задачи хозяйства
        taskRepository.deleteAllByHouseholdId(household.id!!)
        // удалить планы после задач, которые ссылаются на них
        taskPlanRepository.deleteAllByHouseholdId(household.id!!)
        // удалить шаблоны задач хозяйства
        taskTemplateRepository.deleteAllByHouseholdId(household.id!!)
        // удалить привилегии хозяйства
        privilegeRepository.deleteAllByHouseholdId(household.id!!)
    }

    override fun createHousehold(household: HouseholdRegisterDTO): HouseholdResponseDTO {
        // достать создателя
        val user = getCurrentUserForUpdate()

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
        // достать юзера + хозяйство + участие
        val user = getCurrentUser()
        val household = getActiveHouseholdForUpdate(householdId)
        getActiveMembershipForUpdate(user.id!!, household.id!!)

        // удаление без resetBalance и releaseAssignedTasks
        // так как все транзакции + активность + задачи + привилегии удаляются жестко
        deleteHouseholdInternal(household)
    }

    override fun deleteHouseholdFromSystem(householdId: UUID) {
        val household = householdRepository.findByIdForUpdate(householdId)
            ?: throw HouseholdNotFoundException()

        if (!household.isActive)
            return

        deleteHouseholdInternal(household)
    }

    // обновить можно только название
    override fun updateHousehold(householdId: UUID, newHousehold: HouseholdRegisterDTO): HouseholdResponseDTO {
        // достать активное хозяйство
        val household = getActiveHouseholdForUpdate(householdId)

        // достать юзера
        val user = getCurrentUser()

        // проверить участие
        getActiveMembershipForUpdate(user.id!!, household.id!!)

        // обновить название (обновляться может только оно) и сохранить
        household.name = newHousehold.name

//        return householdRepository.save(household).toDto() // managed entity
        return household.toDto()
    }

    // до вступления -> проверка участия не нужна
    override fun findHouseholdByInviteCode(inviteCode: String): HouseholdResponseDTO {
        val household = householdRepository.findByInviteCode(inviteCode)
            ?: throw HouseholdNotFoundException()

        // проверить активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household.toDto()
    }

    override fun findHouseholdById(householdId: UUID): HouseholdResponseDTO {
        // достать активное хозяйство
        val household = getActiveHousehold(householdId)

        // достать юзера
        val user = getCurrentUser()

        // проверить активное участие
        getActiveMembership(user.id!!, household.id!!)

        return household.toDto()
    }

}

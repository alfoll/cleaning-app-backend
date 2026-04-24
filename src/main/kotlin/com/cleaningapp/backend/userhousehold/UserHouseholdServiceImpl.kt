package com.cleaningapp.backend.userhousehold

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
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.household.HouseholdService
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.transaction.BalanceResetTransactionCommand
import com.cleaningapp.backend.transaction.TransactionService
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.user.UserResponseDTO
import com.cleaningapp.backend.user.toDTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class UserHouseholdServiceImpl(
    private val userHouseholdRepository: UserHouseholdRepository,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,

    private val taskService: TaskService,
    private val transactionService: TransactionService,
    private val activityService: ActivityService,
    private val householdService: HouseholdService,
) : UserHouseholdService {
    // user и household при использовании в сервисах уже есть в бд, значит id точно не null -> можно использовать !!

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
    private fun getActiveMembership(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(userId, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return userHousehold
    }


    override fun joinHousehold(inviteCode: String): UserHouseholdResponseDTO {
        // найти юзера (проверки в функции)
        val user = getCurrentUser()

        // найти хозяйство по коду (проверить есть ли такое)
        val household = householdRepository.findByInviteCode(inviteCode)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // нет ли уже юзера в этом хозяйстве
        val existing = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)

        if (existing != null && existing.isUserActive)
            throw BusinessConflictException("User is already active in this household")

        // не привышел ли лимит юзеров хозяйства
        val activeMembers = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)
        if (activeMembers >= 6) // в хозяйстве максимум 6 участников
            throw BusinessConflictException("Household's member limit reached")

        // юзер может состоять максимум в 3 хозяйствах одновременно
        val activeUserHouseholds = userHouseholdRepository.countByUserIdAndIsUserActiveTrue(user.id!!)
        if (activeUserHouseholds >= 3)
            throw BusinessConflictException("User's household limit reached")

        // если юзер уже был в этом хозяйстве (то есть есть неактивная запись) -> активировать
        if (existing != null && !existing.isUserActive) {
            existing.isUserActive = true
            existing.balance = 0

            // создаем запись USER_JOINED в ленте активности - возвращение юзера
            activityService.createActivityRecord(
                RecordActivityCommand(
                    householdId = household.id!!,
                    memberId = existing.id!!,
                    activityType = ActivityType.USER_JOINED,
                    title = "User joined",
                    description = "${user.name} rejoined household \"${household.name}\""
                )
            )

//            return userHouseholdRepository.save(existing).toDto() // managed entity - вроде сохранение не нужно
            return existing.toDto()
        }

        // создать связь и сохранить если пользователь новый
        val userHousehold = UserHouseholdEntity().apply {
            this.user = user
            this.household = household
        }
        val savedUserHousehold = userHouseholdRepository.save(userHousehold)

        // создаем запись USER_JOINED в ленте активности - новое присоединение
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = household.id!!,
                memberId = savedUserHousehold.id!!,
                activityType = ActivityType.USER_JOINED,
                title = "User joined",
                description = "${user.name} joined household \"${household.name}\""
            )
        )

        return savedUserHousehold.toDto() // сохраняется новая сущность
    }

    override fun leaveHousehold(householdId: UUID) {
        // достать юзера
        val user = getCurrentUser()

        // достать активное хозяйство
        val household = getActiveHousehold(householdId)

        // проверить активное участие
        val userHousehold = getActiveMembership(user.id!!, household.id!!)

        // если это последний участник хозяйства - после выхода будет удаление
        val activeMembers = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)

        if (activeMembers == 1) {
            householdService.deleteHousehold(household.id!!)
            return
        }

        // освободить забронированные задачи
        val releasedTaskAmount = taskService.releaseAssignedTasks(userHousehold.id!!)

        // обнулить баланс
        transactionService.resetBalance(
            BalanceResetTransactionCommand(
                householdId = household.id!!,
                memberId = userHousehold.id!!,
            )
        )


        // деактивировать участие и сохранить изменения
        userHousehold.isUserActive = false
//        userHouseholdRepository.save(userHousehold) // managed entity

        // создать запись USER_LEFT в ленте активности
        val description = if (releasedTaskAmount > 0) {
            "${user.name} left household \"${household.name}\". " +
                    "$releasedTaskAmount tasks were released"
        } else {
            "${user.name} left household \"${household.name}\""
        }
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = household.id!!,
                memberId = userHousehold.id!!,
                activityType = ActivityType.USER_LEFT,
                title = "User left",
                description = description
            )
        )
    }

    override fun removeUserFromHousehold(householdId: UUID, userToRemoveId: UUID) {
        // достать юзера КОТОРЫЙ УДАЛЯЕТ
        val user = getCurrentUser()

        // найти хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // проверить активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // проверить что удаляющий есть в хозяйстве
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw MembershipNotFoundException()

        // проверить что удаляющий активен в хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        // проверить что юзер имеет право удалять (создатель) - убрала

        // запретить удаление самого себя (для этого leaveHousehold)
        if (user.id == userToRemoveId)
            throw BusinessConflictException("You cannot remove yourself, use leaveHousehold method to leave household")

        // проверить что УДАЛЯЕМЫЙ ЮЗЕР состоит в хозяйстве
        val removedUser = userHouseholdRepository.findByUserIdAndHouseholdId(userToRemoveId, household.id!!)
            ?: throw BusinessConflictException("User to remove is not member of this household")

        // проверить что УДАЛЯЕМЫЙ ЮЗЕР активен в хозяйстве
        if (!removedUser.isUserActive)
            throw BusinessConflictException("User to remove is already not active in this household")

        // освободить забронированные задачи
        val releasedTaskAmount = taskService.releaseAssignedTasks(removedUser.id!!)

        // обнулить баланс УДАЛЯЕМОГО ЮЗЕРА в хозяйстве
        transactionService.resetBalance(
            BalanceResetTransactionCommand(
                householdId = household.id!!,
                memberId = removedUser.id!!,
            )
        )

        // деактивировать и сохранить изменения
        removedUser.isUserActive = false
//        userHouseholdRepository.save(removedUser) // managed entity

        // создать запись USER_REMOVED в ленте активности
        val description = if (releasedTaskAmount > 0) {
            "${removedUser.user.name} was removed from household \"${household.name}\" by user ${user.name}. " +
                    "$releasedTaskAmount tasks were released"
        } else {
            "${removedUser.user.name} was removed from household \"${household.name}\" by user ${user.name}. "
        }
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = household.id!!,
                memberId = userHousehold.id!!, // действие совершено УДАЛЯЮЩИМ
                activityType = ActivityType.USER_REMOVED,
                title = "User removed",
                description = description,
            )
        )

//        // МЕРТВАЯ ВЕТКА - на всякий случай проверить, остались ли еще участники
//        val activeMembers = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)
//
//        if (activeMembers == 0) {
//            household.isActive = false
////            householdRepository.save(household) // managed entity
//        }
    }

    @Transactional(readOnly = true)
    override fun getUserHouseholds(): List<UserHouseholdResponseDTO> {
        // достать юзера
        val user = getCurrentUser()

        // достать хозяйства (связи) в которых он активен
        return userHouseholdRepository
            .findAllByUserIdAndIsUserActiveTrue(user.id!!)
            .filter { it.household.isActive } // проверка на активность хозяйств?
            .map { it.toDto() }
    }

    @Transactional(readOnly = true)
    override fun getHouseholdMembers(householdId: UUID): List<UserResponseDTO> {
        // существует ли такое хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // достать юзера
        val user = getCurrentUser()

        // состоит ли пользователь в этом хозяйстве
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        // вернуть список участников, активных вхозяйстве
        return userHouseholdRepository
            .findAllByHouseholdIdAndIsUserActiveTrue(household.id!!)
            .map { it.user.toDTO() }

    }
}
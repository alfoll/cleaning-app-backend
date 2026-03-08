package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.task.TaskService
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
) : UserHouseholdService {
    // user и household при использовании в сервисах уже есть в бд, значит id точно не null -> можно использовать !!

    private fun getCurrentUser(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("User not authenticated")

        val firebaseUid = auth.name

        return userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw UserNotFoundException()
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
            return userHouseholdRepository.save(existing).toDto()
        }

        // создать связь и сохранить если пользователь новый
        val userHousehold = UserHouseholdEntity().apply {
            this.user = user
            this.household = household
        }
         return userHouseholdRepository.save(userHousehold).toDto()
    }

    override fun leaveHousehold(householdId: UUID) {
        // достать юзера
        val user = getCurrentUser()

        // найти хозяйство (проверить есть ли такое)
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        // освободить забронированные задачи
        taskService.releaseAssignedTasks(userHousehold.id!!)

        // обнулить баланс
        userHousehold.balance = 0

        // деактивировать в хозяйстве и сохранить изменения
        userHousehold.isUserActive = false
        userHouseholdRepository.save(userHousehold)

        // если это был последний участник хозяйства - деактивировать хозяйство хозяйства
        val activeMembers = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)

        // так как участников больше нет, а проверки все те же что и в deleteHousehold в HouseholdService
        // -> нет смысла вызывать меод из сервиса
        if (activeMembers == 0) {
            household.isActive = false
            householdRepository.save(household)
        }
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
        taskService.releaseAssignedTasks(removedUser.id!!)

        // обнулить баланс УДАЛЯЕМОГО ЮЗЕРА в хозяйстве
        removedUser.balance = 0

        // деактивировать и сохранить изменения
        removedUser.isUserActive = false
        userHouseholdRepository.save(removedUser)

        // на всякий случай проверить, остались ли еще участники (НЕ УВЕРЕНА НУЖНО ЛИ)
        val activeMembers = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)

        if (activeMembers == 0) {
            household.isActive = false
            householdRepository.save(household)
        }
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


    override fun increaseBalance(householdId: UUID, amount: Int): UserHouseholdResponseDTO {
        // amount должен быть больше 0
        if (amount <= 0)
            throw BusinessConflictException("Amount must be greater than 0")

        // сущтвует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // есть ли юзер в хозяйстве
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        // пополнить баланс   сохранить заново
        userHousehold.balance += amount
        return userHouseholdRepository.save(userHousehold).toDto()
    }

    override fun decreaseBalance(householdId: UUID, amount: Int): UserHouseholdResponseDTO {
        // amount должен быть больше 0
        if (amount <= 0)
            throw BusinessConflictException("Amount must be greater than 0")

        // сущтвует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        // есть ли юзер в хозяйстве
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        // баланс должен оставаться положительным
        if (userHousehold.balance < amount)
            throw BusinessConflictException("Balance must be greater (or equal) than amount")

        // обновить баланс и сохранить
        userHousehold.balance -= amount
        return userHouseholdRepository.save(userHousehold).toDto()
    }
}
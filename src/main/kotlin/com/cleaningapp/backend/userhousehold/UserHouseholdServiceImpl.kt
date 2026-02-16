package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.user.UserResponseDTO
import com.cleaningapp.backend.user.toDTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserHouseholdServiceImpl(
    private val userHouseholdRepository: UserHouseholdRepository,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
) : UserHouseholdService {
    // user и household при использовании в сервисах уже есть в бд, значит id точно не null -> можно использовать !!

    private fun getCurrentUser(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw RuntimeException("User not authenticated")

        val firebaseUid = auth.name

        return userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw RuntimeException("User not found")
    }

    override fun joinHousehold(inviteCode: String): UserHouseholdResponseDTO {
        // найти юзера (проверки в функции)
        val user = getCurrentUser()

        // найти хозяйство по коду (проверить есть ли такое)
        val household = householdRepository.findByInviteCode(inviteCode)
            ?: throw RuntimeException("Household not found")

        // активно ли хозяйство
        if (!household.isActive)
            throw RuntimeException("Household is not active")

        // нет ли уже юзера в этом хозяйстве
        val existing = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)

        if (existing != null && existing.isUserActive)
            throw RuntimeException("User is already active in this household")

        // не привышел ли лимит юзеров хозяйства
        val activeMembers = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)
        if (activeMembers >= 6) // в хозяйстве максимум 6 участников
            throw IllegalStateException("Household's member limit reached")

        // юзер может состоять максимум в 3 хозяйствах одновременно
        val activeUserHouseholds = userHouseholdRepository.countByUserIdAndIsUserActiveTrue(user.id!!)
        if (activeUserHouseholds >= 3)
            throw IllegalStateException("User's household limit reached")

        // создать связь и сохранить
        val userHousehold = UserHouseholdEntity().apply {
            this.user = user
            this.household = household
        }
         return userHouseholdRepository.save(userHousehold).toDto()
    }

    override fun leaveHousehold(householdId: UUID) {
        // достать юзера
        val user = getCurrentUser()

        // найти хозяйство (проверить есть ли такое) ИЛИ/И найти связь (проверить есть ли она)
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw RuntimeException("Household not found")

        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw RuntimeException("User is not in this household")

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw RuntimeException("User is not active in this household")

        // сделать неактивным в хозяйстве (мягкое удаление?) или удалить запись
        userHousehold.isUserActive = false
        userHouseholdRepository.save(userHousehold) // подумать над удалением
    }

    override fun getUserHouseholds(): List<UserHouseholdResponseDTO> {
        // достать юзера
        val user = getCurrentUser()

        // достать хозяйства (связи) в которых он активен
        return userHouseholdRepository
            .findAllByUserId(user.id!!)
            .filter { it.isUserActive }
            .map { it.toDto() }
    }

    override fun getHouseholdMembers(householdId: UUID): List<UserResponseDTO> {
        // существует ли такое хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw RuntimeException("Household not found")

        // достать юзера
        val user = getCurrentUser()

        // состоит ли пользователь в этом хозяйстве
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, householdId)
            ?: throw RuntimeException("User is not in this household")

        // активен ли юзер в хозяйстве
        if (!userHousehold.isUserActive)
            throw RuntimeException("User is not active in this household")

        // вернуть список участников, активных вхозяйстве
        return userHouseholdRepository
            .findAllByHouseholdIdAndIsUserActiveTrue(household.id!!)
            .map { it.user.toDTO() }

    }


    override fun increaseBalance(householdId: UUID, amount: Int): UserHouseholdResponseDTO {
        // amount должен быть больше 0
        if (amount <= 0)
            throw IllegalArgumentException("Amount must be greater than 0")

        // сущтвует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw RuntimeException("Household not found")

        // есть ли юзер в хозяйстве
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw RuntimeException("User is not in this household")

        // активен ли юзер в хозяйстве
        if (!userHousehold.isUserActive)
            throw RuntimeException("User is not active in this household")

        // пополнить баланс   сохранить заново
        userHousehold.balance += amount
        return userHouseholdRepository.save(userHousehold).toDto()
    }

    override fun decreaseBalance(householdId: UUID, amount: Int): UserHouseholdResponseDTO {
        // amount должен быть больше 0
        if (amount <= 0)
            throw IllegalArgumentException("Amount must be greater than 0")

        // сущтвует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw RuntimeException("Household not found")

        // есть ли юзер в хозяйстве
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw RuntimeException("User is not in this household")

        // активен ли юзер в хозяйстве
        if (!userHousehold.isUserActive)
            throw RuntimeException("User is not active in this household")

        // баланс должен оставаться положительным
        if (userHousehold.balance < amount)
            throw IllegalStateException("Balance must be greater (or equal) than amount")

        // обновить баланс и сохранить
        userHousehold.balance -= amount
        return userHouseholdRepository.save(userHousehold).toDto()
    }
}
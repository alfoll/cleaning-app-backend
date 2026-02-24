package com.cleaningapp.backend.household

import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.data.repository.findByIdOrNull
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
        throw IllegalStateException("Failed to generate unique invite code")
    }

    // доостать юзера из контекста
    private fun getCurrentUser(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw RuntimeException("User not authenticated")

        val firebaseUid = auth.name

        return userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw RuntimeException("User not found")
    }

    override fun createHousehold(household: HouseholdRegisterDTO): HouseholdResponseDTO {
        // достать создателя
        val user = getCurrentUser()

        // проверить лимит в 3 хозяйства у юзера
        if (userHouseholdRepository.countByUserIdAndIsUserActiveTrue(user.id!!) >= 3)
            throw IllegalStateException("User's household limit reached")

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
        userHouseholdRepository.save(userHousehold)

        return saved.toDto()
    }

    override fun deleteHousehold(householdId: UUID) {
        // существует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw RuntimeException("Household not found")

        // активно ли хозяйство
        if (!household.isActive)
            throw RuntimeException("Household is not active")

        // состоит ли юзер который удаляет хозяйство в нем
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository
            .findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw RuntimeException("User is not in this household")

        // активен ли юзер в хозяйстве
        if (!userHousehold.isUserActive)
            throw RuntimeException("User is not active in this household")

        // как то реализовать удаление с освобождением всех связей - мягкое?
        return householdRepository.deleteById(household.id)
    }

    override fun updateHousehold(householdId: UUID, newHousehold: HouseholdRegisterDTO): HouseholdResponseDTO {
        // существует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw RuntimeException("Household not found")

        // активно ли хозяйство
        if (!household.isActive)
            throw RuntimeException("Household is not active")

        // состоит ли юзер в этом хозяйстве
        val user = getCurrentUser()

        val userHousehold = userHouseholdRepository
            .findByUserIdAndHouseholdId(user.id!!, household.id!!)
            ?: throw RuntimeException("User is not in this household")

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw RuntimeException("User is not active in this household")

        // обновить название (обновляться может только оно) и сохранить
        household.name = newHousehold.name
        return householdRepository.save(household).toDto()
    }

    override fun findHouseholdByInviteCode(inviteCode: String): HouseholdResponseDTO {
        val household = householdRepository.findByInviteCode(inviteCode)
            ?: throw RuntimeException("Household not found")

        // проверить активно ли хозяйство
        if (!household.isActive)
            throw RuntimeException("Household is not active")

        // проверить состояит ли пользователь в хозяйстве
        // - не нужно так как пользователь ищет хозяйство по инвайт коду при втуплении в него

        return household.toDto()
    }

    override fun findHouseholdById(id: UUID): HouseholdResponseDTO {
        val household = householdRepository.findByIdOrNull(id)
            ?: throw RuntimeException("Household not found")

        // проверить активно ли хозяйство - нужно ли?
        if (!household.isActive)
            throw RuntimeException("Household is not active")

        // проверить состояит ли пользователь в хозяйстве - зачем?
        return household.toDto()
    }

}
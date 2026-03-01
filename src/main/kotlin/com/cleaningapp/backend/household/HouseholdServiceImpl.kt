package com.cleaningapp.backend.household

import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.UserNotFoundException
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

        return userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw UserNotFoundException()
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
        userHouseholdRepository.save(userHousehold)

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
            // обнулить баланс
            member.balance = 0

            // деактивировать связь
            member.isUserActive = false

            // сохранить изменения - аналогично вроде не нужно
//            userHouseholdRepository.save(member)
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
        return householdRepository.save(household).toDto()
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
package com.cleaningapp.backend.privilege

import com.cleaningapp.backend.activity.ActivityService
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.activity.RecordActivityCommand
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.PrivilegeNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.transaction.PrivilegePurchaseTransactionCommand
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

enum class PrivilegeFilterType{
    ALL, // все
    AVAILABLE, // все не купленные
    MY, // мои купленные
}

@Service
@Transactional
class PrivilegeServiceImpl(
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val householdRepository: HouseholdRepository,
    private val userHouseholdRepository: UserHouseholdRepository,

    private val transactionService: TransactionService,
    private val activityService: ActivityService,
    ): PrivilegeService {

    // достать юзера из контекста
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
    // блокировка для write сценариев
    private fun getActiveHouseholdForUpdate(householdId: UUID): HouseholdEntity {
        // существует ли хозяйство
        val household = householdRepository.findByIdForUpdate(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }

    // досать активную связь - для read сценаривеев
    private fun getActiveMembership(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(userId, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return userHousehold
    }
    // блокировка для write сценариев
    private fun getActiveMembershipForUpdate(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdIdForUpdate(userId, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return userHousehold
    }

    // достать сущность привилегии - для read сценариев
    private fun getPrivilegeEntity(privilegeId: UUID): PrivilegeEntity =
        privilegeRepository.findByIdOrNull(privilegeId)
            ?: throw PrivilegeNotFoundException()
    // блокировка для write сценариев
    private fun getPrivilegeEntityForUpdate(privilegeId: UUID): PrivilegeEntity =
        privilegeRepository.findByIdForUpdate(privilegeId)
            ?: throw PrivilegeNotFoundException()

    // валидировать привилегию
    // (проверка что пользователь состоит в хозяйстве в котором хочет купить привилегию)
    // возвращает активное участие
    // для read сценариев только
    private fun validatePrivilegeAccess(privilege: PrivilegeEntity, currentUser: UserEntity): UserHouseholdEntity {
        val household = privilege.household

        if (!household.isActive)
            throw HouseholdNotActiveException()

        return getActiveMembership(currentUser.id!!, household.id!!)
    }


    // создать привилегию может любой активный участник хозяйства
    override fun createPrivilege(householdId: UUID, privilege: PrivilegeRegisterDTO): PrivilegeResponseDTO {
        // достать юзера + хозяйство + участие
        val user = getCurrentUser()

        val household = getActiveHouseholdForUpdate(householdId)
        val membership = getActiveMembershipForUpdate(user.id!!, household.id!!)

        // сохраняем привилегию
        val savedPrivilege = privilegeRepository.save(privilege.toPrivilegeEntity(household, user))

        // создаем запись PRIVILEGE_CREATED в ленте активности
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = household.id!!,
                memberId = membership.id!!,
                activityType = ActivityType.PRIVILEGE_CREATED,
                title = "Privilege created",
                description = "${user.name} created privilege \"${savedPrivilege.title}\""
            )
        )

        return savedPrivilege.toDto()
    }

    // можно менять только не купленные привилегии
    // менять может только создатель
    override fun updatePrivilege(privilegeId: UUID, newPrivilege: PrivilegeRegisterDTO): PrivilegeResponseDTO {
        // юзер + хозяйство и участие из привилегии
        val user = getCurrentUser()

        val householdId = privilegeRepository.findHouseholdIdByPrivilegeId(privilegeId)
            ?: throw PrivilegeNotFoundException()

        val household = getActiveHouseholdForUpdate(householdId)
        getActiveMembershipForUpdate(user.id!!, household.id!!)

        // достать привилегию
        val privilege = getPrivilegeEntityForUpdate(privilegeId)

        if (privilege.household.id != household.id)
            throw BusinessConflictException("Privilege does not belong to this household")

        // если задача куплена - нельзя менять
        if (!privilege.isAvailable || privilege.boughtBy != null)
            throw BusinessConflictException("Bought privilege cannot be updated")

        // если не создатель - нельзя менять
        if (privilege.createdBy.id != user.id)
            throw BusinessConflictException("Only creator can update privilege")

        // обновляем поля (название/описание/стоимость)
        privilege.title = newPrivilege.title
        privilege.description = newPrivilege.description
        privilege.cost = newPrivilege.cost

//        return privilegeRepository.save(privilege).toDto() // managed entity
        return privilege.toDto()

    }

    // удалить может только создатель
    override fun deletePrivilege(privilegeId: UUID) {
        // юзер + хозяйство и участие из привилегии
        val user = getCurrentUser()

        val householdId = privilegeRepository.findHouseholdIdByPrivilegeId(privilegeId)
            ?: throw PrivilegeNotFoundException()

        val household = getActiveHouseholdForUpdate(householdId)
        getActiveMembershipForUpdate(user.id!!, household.id!!)

        // достать привилегию
        val privilege = getPrivilegeEntityForUpdate(privilegeId)

        if (privilege.household.id != household.id)
            throw BusinessConflictException("Privilege does not belong to this household")


        // если привилегия куплена - ее нельзя удалить
        if (!privilege.isAvailable || privilege.boughtBy != null)
            throw BusinessConflictException("Bought privilege cannot be deleted")

        // если не создатель - нельзя удалить
        if (privilege.createdBy.id != user.id)
            throw BusinessConflictException("Only creator can delete privilege")

        // жесткое удаление, так как массовая сущность
        privilegeRepository.delete(privilege)
    }

    override fun buyPrivilege(privilegeId: UUID): PrivilegeResponseDTO {
        // юзер + хозяйство и участие из привилегии
        val user = getCurrentUser()

        val householdId = privilegeRepository.findHouseholdIdByPrivilegeId(privilegeId)
            ?: throw PrivilegeNotFoundException()

        val household = getActiveHouseholdForUpdate(householdId)
        val membership = getActiveMembershipForUpdate(user.id!!, household.id!!)

        // достать привилегию
        val privilege = getPrivilegeEntityForUpdate(privilegeId)

        if (privilege.household.id != household.id)
            throw BusinessConflictException("Privilege does not belong to this household")

        // если привилегия куплена - ее нельзя купить
        if (!privilege.isAvailable || privilege.boughtBy != null)
            throw BusinessConflictException("Privilege is already bought")

        // проверить что на балансе достаточно средств
        if (membership.balance < privilege.cost)
            throw BusinessConflictException("You dont have enough coins to buy this privilege")

        // покупаем привилегию
        privilege.isAvailable = false
        privilege.boughtBy = membership

        // списание средств с баланса
//        val savedPrivilege = privilegeRepository.save(privilege) // managed entity

        transactionService.recordPrivilegePurchase(
            PrivilegePurchaseTransactionCommand(
                householdId = privilege.household.id!!,
                memberId = membership.id!!,
                privilegeId = privilege.id!!,
            )
        )

        // создаем запись PRIVILEGE_BOUGHT в ленте активности
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = privilege.household.id!!,
                memberId = membership.id!!,
                activityType = ActivityType.PRIVILEGE_BOUGHT,
                title = "Privilege bought",
                description = "${user.name} bought privilege \"${privilege.title}\""
            )
        )

        return privilege.toDto()
    }

    @Transactional(readOnly = true)
    override fun getPrivilegeById(privilegeId: UUID): PrivilegeResponseDTO {
        // достать юзера
        val user = getCurrentUser()

        // достать привилегию
        val privilege = getPrivilegeEntity(privilegeId)

        // валидировать привилегию (проверка наличия активного хозяйства + активного участия)
        validatePrivilegeAccess(privilege, user)

        return privilege.toDto()
    }

    // привилегии достаются не разными методами а с помощью фильтра
    // можно посмотреть все/свободные/мои купленные привилегии хозяйства
    @Transactional(readOnly = true)
    override fun getHouseholdPrivileges(householdId: UUID, filter: PrivilegeFilterType): List<PrivilegeResponseDTO> {
        // достать юзера
        val user = getCurrentUser()

        // достать активное хозяйство
        val household = getActiveHousehold(householdId)

        // достать активное участие
        val membership = getActiveMembership(user.id!!, household.id!!)

        // сформиовать список
        val privileges = when(filter) {
            PrivilegeFilterType.ALL ->
                privilegeRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

            PrivilegeFilterType.AVAILABLE ->
                privilegeRepository.findAllByHouseholdIdAndIsAvailableTrueAndBoughtByIsNullOrderByCreatedAtDesc(household.id!!)

            PrivilegeFilterType.MY ->
                privilegeRepository.findAllByHouseholdIdAndBoughtByIdOrderByCreatedAtDesc(household.id!!, membership.id!!)
        }
        return privileges.map { it.toDto() }
    }
}
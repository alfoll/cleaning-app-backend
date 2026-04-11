package com.cleaningapp.backend.activity

import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
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

enum class ActivityActorScope{
    ALL, // вся активность хозяйства
    MY, // моя активность в хозяйстве
}

@Service
@Transactional
class ActivityServiceImpl(
    private val activityRepository: ActivityRepository,
    private val userRepository: UserRepository,
    private val householdRepository: HouseholdRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
): ActivityService {

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

    // достать связь без проверки активности
    // событие может ссылаться на неактивное участие (выход/удаление) - метод нужен
    private fun getMembershipEntity(memberId: UUID): UserHouseholdEntity =
        userHouseholdRepository.findByIdOrNull(memberId)
            ?: throw MembershipNotFoundException()

    // собрать запись активности
    private fun buildActivity(
        activityType: ActivityType,
        title: String,
        description: String?,
        household: HouseholdEntity,
        member: UserHouseholdEntity,
    ): ActivityEntity = ActivityEntity(
        activityType = activityType,
        title = title,
        description = description,
    ).apply {
        this.household = household
        this.member = member
    }


    // факт активности может принадлежать любому активному учаснику хозяйства
    // метод внутренний
    // активность участия не проверяется - история может ссылаться на неактивное учакстие
    // принадлежность участия к хозяйству проверяется
    override fun createActivityRecord(command: RecordActivityCommand) {
        // достать активное хозяйство
        val household = getActiveHousehold(command.householdId)

        // достать участие (не обязательно активное)
        val member = getMembershipEntity(command.memberId)

        // проверить принадлежность участия к хозяйству (что command.householdId это хозяйство участника command.memberId)
        if (household.id != member.household.id)
            throw BusinessConflictException("Membership does not belong to the specified household")

        // создать и сохранить запись активности
        activityRepository.save( // новая сущность - сохранение нужно
            buildActivity(
                activityType = command.activityType,
                title = command.title,
                description = command.description,
                household = household,
                member = member,
            )
        )
    }

    // activityType = null -> без фильтра по типу (вся лента, иными словами ALL)
    // actorScope = ALL/MY - фильтр по участинику (все/мои)
    // смотреть историю может только АКТИВНЫЙ участник
    @Transactional(readOnly = true)
    override fun getHouseholdActivity(
        householdId: UUID,
        activityType: ActivityType?,
        actorScope: ActivityActorScope
    ): List<ActivityResponseDTO> {
        // достать активного участника
        val user = getCurrentUser()

        // достать активное хозяйство
        val household = getActiveHousehold(householdId)

        // достать АКТИВНОЕ участие
        val membership = getActiveMembership(user.id!!, household.id!!)

        // получить ленту активности с фильтрацией
        val activities = when {
            // вся лента (нет фильтра по типу активности + ALL на фильтре по участнику)
            activityType == null && actorScope == ActivityActorScope.ALL ->
                activityRepository.findAllByHouseholdIdOrderByCreatedAtDesc(
                    household.id!!
                )

            // лента по типам активности (фильтр по типу акти вности + ALL на фильтре по участнику)
            activityType != null && actorScope == ActivityActorScope.ALL ->
                activityRepository.findAllByHouseholdIdAndActivityTypeOrderByCreatedAtDesc(
                    household.id!!,
                    activityType
                )

            // вся лента участника (нет фильтра по типу активности + MY на фильтре по участнику)
            activityType == null && actorScope == ActivityActorScope.MY ->
                activityRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                    household.id!!,
                    membership.id!! // фильтрует по id у UserHousehold а не у User
                )

            // моя активность + фильтрация по типу (двойной фильтр)
            else ->
                activityRepository.findAllByHouseholdIdAndActivityTypeAndMemberIdOrderByCreatedAtDesc(
                    household.id!!,
                    activityType!!,
                    membership.id!! // фильтрует по id у UserHousehold а не у User
                )
        }

        return activities.map { it.toDto() }
    }
}
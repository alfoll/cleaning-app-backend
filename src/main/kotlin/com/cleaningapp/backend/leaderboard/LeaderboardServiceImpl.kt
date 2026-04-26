package com.cleaningapp.backend.leaderboard

import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private const val LEADERBOARD_PERIOD_DAYS = 7

@Service
@Transactional(readOnly = true)
class LeaderboardServiceImpl(
    private val leaderboardRepository: LeaderboardRepository,
    private val userRepository: UserRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
    private val householdRepository: HouseholdRepository,
    private val clock: Clock,
): LeaderboardService {

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

    // перевод проекции в дто ответа - финальное оформление данных
    // ранг считается на основе  -- см. LeaderboardRepository
    private fun List<LeaderboardRowProjection>.toResponseDto(
        currentUserId: UUID
    ): List<LeaderboardItemResponseDTO> =
        mapIndexed { index, row ->
            LeaderboardItemResponseDTO(
                place = index + 1,
                userId = row.userId,
                displayName = row.name,
                avatarUrl = row.avatarUrl,
                earnedCoins = row.earnedCoins,
                earnedCoinsDelta = row.earnedCoinsDelta,
                completedTasksCount = row.completedTaskCount,
                completedTasksDelta = row.completedTasksDelta,
                isCurrentUser = row.userId == currentUserId,
            )
        }



    // лидерборд по хозяйству
    // места уникальны
    override fun getLeaderboard(householdId: UUID): LeaderboardResponseDTO {
        // достать пользователя
        val user = getCurrentUser()

        // достать активное хозяйство
        val household = getActiveHousehold(householdId)

        // проверить активное участие
        getActiveMembership(user.id!!, household.id!!)

        // период за который считается прирост активности - неделя
        val periodStart = LocalDateTime.now(clock).minusDays(LEADERBOARD_PERIOD_DAYS.toLong())

        // достать лидерборд по репозиторию (рейтинг по транзакциям за начисление считается)
        val rows = leaderboardRepository.findHouseholdLeaderboard(
            householdId = household.id!!,
            earningType = TransactionType.TASK_COMPLETION,
            periodStart = periodStart,
        )

        // вернуть ответ
        return LeaderboardResponseDTO(
            periodDays = LEADERBOARD_PERIOD_DAYS,
            items = rows.toResponseDto(user.id!!)
        )
    }
}
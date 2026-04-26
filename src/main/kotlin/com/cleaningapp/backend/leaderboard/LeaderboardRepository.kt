package com.cleaningapp.backend.leaderboard

import com.cleaningapp.backend.transaction.TransactionType
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface LeaderboardRepository: JpaRepository<UserHouseholdEntity, UUID> {
    // возвращает активных участников со статистикой для лидерборда
    // (отсортированный список строк-проекций лидерборда)

    // join участия и юзера + left join на транзакции (чтобы с 0 заработанных монет тоже попали в лидерборд)
    // считаются TASK_COMPLETION транзакции

    /* сорт:
        заработанные монеты - earnedCoins
        прирост по монетам - earnedCoinsDelta
        прирост по задачам - completedTasksDelta
        общее кол-во задач - completedTasksCount
        время первого ступления (более позднее выше) - технический сорт
        id - технический сорт
    */

    @Query(
        """
            select new com.cleaningapp.backend.leaderboard.LeaderboardRowProjection (
                u.id,
                u.name,
                u.avatarUrl,
                
                coalesce(sum(t.amount), 0),
                coalesce(sum(
                        case 
                            when t.createdAt >= :periodStart then t.amount
                            else 0 
                        end
                    ), 0),
                count(t.id),
                count(case
                        when t.createdAt >= :periodStart then t.id
                        else null
                    end))
                    
            from UserHouseholdEntity m join m.user u 
            left join TransactionEntity t 
                on t.member = m 
                and t.household.id = :householdId 
                and t.type = :earningType
            where m.household.id = :householdId 
                and m.isUserActive = true 
                and u.isActive = true
            group by m.id, m.joinedAt, u.id, u.name, u.avatarUrl
            order by coalesce(sum(t.amount), 0) desc, 
                    coalesce(sum(
                            case 
                                when t.createdAt >= :periodStart then t.amount
                                else 0 
                            end
                        ), 0) desc,
                    count(case
                            when t.createdAt >= :periodStart then t.id
                            else null
                        end) desc,
                    count(t.id) desc, 
                    
                    m.joinedAt desc, 
                    u.id asc
        """
    )
    fun findHouseholdLeaderboard(
        @Param("householdId") householdId: UUID,
        @Param("earningType") earningType: TransactionType,
        @Param("periodStart") periodStart: LocalDateTime,
    ): List<LeaderboardRowProjection>
}
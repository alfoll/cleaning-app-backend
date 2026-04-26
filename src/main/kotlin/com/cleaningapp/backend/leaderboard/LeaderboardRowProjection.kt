package com.cleaningapp.backend.leaderboard

import java.util.UUID

// внутренняя проекция результата запроса по транзакциям
// на основании этих данных считается рейтинг

data class LeaderboardRowProjection(
    val userId: UUID,
    val name: String,
    val avatarUrl: String?,

    val earnedCoins: Long,
    val earnedCoinsDelta: Long,

    val completedTaskCount: Long,
    val completedTasksDelta: Long,
)
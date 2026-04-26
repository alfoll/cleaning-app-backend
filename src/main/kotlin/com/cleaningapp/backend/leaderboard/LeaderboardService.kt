package com.cleaningapp.backend.leaderboard

import java.util.UUID

interface LeaderboardService {
    // получение лидерборда
    fun getLeaderboard(householdId: UUID): LeaderboardResponseDTO
}
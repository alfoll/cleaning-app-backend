package com.cleaningapp.backend.leaderboard

// лидерборд с указанием периода, за который считаются приросты
data class LeaderboardResponseDTO(
    val periodDays: Int,
    val items: List<LeaderboardItemResponseDTO>,
)

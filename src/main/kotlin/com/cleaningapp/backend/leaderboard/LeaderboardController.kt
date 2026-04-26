package com.cleaningapp.backend.leaderboard

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class LeaderboardController(
    private val leaderboardService: LeaderboardService,
) {

    // показать лидерборд хрзяйства - GET /api/households/{householdId}/leaderboard
    @GetMapping("/households/{householdId}/leaderboard")
    fun getLeaderboard(@PathVariable householdId: UUID): LeaderboardResponseDTO =
        leaderboardService.getLeaderboard(householdId)

}
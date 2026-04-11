package com.cleaningapp.backend.activity

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID


@RestController
@RequestMapping("/api")
class ActivityController(
    private val activityService: ActivityService,
) {

    // просмотр ленты активности с фильтрацией по типу активности + по участнику (все/мои)
    // GET /api/households/{householdId}/activity
    @GetMapping("/households/{householdId}/activity")
    fun getHouseholdActivity(
        @PathVariable householdId: UUID,
        @RequestParam(required = false) activityType: ActivityType?, // nullable поле
        @RequestParam(defaultValue = "ALL") actorScope: ActivityActorScope,
    ): List<ActivityResponseDTO> =
        activityService.getHouseholdActivity(householdId, activityType, actorScope)
}
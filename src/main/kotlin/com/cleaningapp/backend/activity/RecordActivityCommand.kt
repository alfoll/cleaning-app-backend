package com.cleaningapp.backend.activity

import java.util.UUID

// activity - создается внутри, поэтому DTO для создания извне не нужно - только командное DTO
data class RecordActivityCommand(
    val householdId: UUID,
    val memberId: UUID,

    val activityType: ActivityType,

    val title: String,
    val description: String? = null,
)

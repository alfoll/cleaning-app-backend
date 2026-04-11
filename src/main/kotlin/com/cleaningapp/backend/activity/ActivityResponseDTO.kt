package com.cleaningapp.backend.activity

import java.time.LocalDateTime
import java.util.UUID

data class ActivityResponseDTO(
    val id: UUID, // когда используется дто, id уже гарантированно не null (null только до сохранения в бд)
    val householdId: UUID, // точно не null, так как хозяйство уже известно
    val userId: UUID, // точно не null, так как юзер уже известен + в реальности UserHousehold.id, но отдается User.id

    val activityType: ActivityType,

    val createdAt: LocalDateTime,
    val title: String,
    val description: String? = null,
)

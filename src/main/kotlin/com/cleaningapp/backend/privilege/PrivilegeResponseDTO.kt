package com.cleaningapp.backend.privilege

import java.time.LocalDateTime
import java.util.UUID

data class PrivilegeResponseDTO(
    val id: UUID, // когда используется дто, id уже гарантированно не null (null только до сохранения в бд)
    val householdId: UUID, // точно не null, так как хозяйство уже известно
    val createdBy: UUID, // точно не null, так как создатель уже известен
    val createdAt: LocalDateTime,

    val title: String,
    val description: String? = null,
    val cost: Int,

    val isAvailable: Boolean = true, // может убрать из сущности и проверять как в Task через наличие брони?
    val boughtBy: UUID? = null, // в реальности UserHousehold.id, но тут отдается User.id
)

package com.cleaningapp.backend.userhousehold

import java.time.LocalDateTime
import java.util.UUID

data class UserHouseholdResponseDTO(
    val id: UUID, // когда используется дто, id уже гарантированно не null (null только до сохранения в бд)
    val householdId: UUID, // точно не null, так как хозяйство уже известно
    val balance: Int,
    val joinedAt: LocalDateTime,
    val isUserActive: Boolean,
)
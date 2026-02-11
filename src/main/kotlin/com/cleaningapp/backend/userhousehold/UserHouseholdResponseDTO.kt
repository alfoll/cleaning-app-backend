package com.cleaningapp.backend.userhousehold

import java.time.LocalDateTime
import java.util.UUID

data class UserHouseholdResponseDTO(
    val id: UUID,
    val householdId: UUID,
    val balance: Int,
    val joinedAt: LocalDateTime,
    val isUserActive: Boolean,
)
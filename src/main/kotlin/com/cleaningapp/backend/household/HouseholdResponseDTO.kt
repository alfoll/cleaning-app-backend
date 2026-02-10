package com.cleaningapp.backend.household

import java.time.LocalDateTime
import java.util.UUID

data class HouseholdResponseDTO(
    val id: UUID,
    val name: String,
    val inviteCode: String,
//    val maxMembers: Int = 6,
    val createdAt: LocalDateTime,
    val createdByUser: UUID,
    val isActive: Boolean = true,
)

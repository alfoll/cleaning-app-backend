package com.cleaningapp.backend.user

import java.time.LocalDateTime
import java.util.UUID

data class UserResponseDTO(
    val id: UUID, // почему то ьыло nullable
    val firebaseUid: String,
    val name: String,
    val email: String,
    val createdAt: LocalDateTime,
    val avatarUrl: String? = null,
)

package com.cleaningapp.backend.user

import java.time.LocalDateTime

data class UserResponseDTO(
    val id: Long,
    val name: String,
    val email: String,
    val createdAt: LocalDateTime,
    val avatarUrl: String? = null,
)

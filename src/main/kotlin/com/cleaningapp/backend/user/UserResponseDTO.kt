package com.cleaningapp.backend.user

import java.time.LocalDateTime
import java.util.UUID

data class UserResponseDTO(
    val id: UUID, // когда используется дто, id уже гарантированно не null (null только до сохранения в бд)
    val firebaseUid: String, // точно не null, так как риходит еще до регистрации
    val name: String,
    val email: String,
    val createdAt: LocalDateTime,
    val avatarUrl: String? = null,
)

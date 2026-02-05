package com.cleaningapp.backend.user

typealias SpringUser = org.springframework.security.core.userdetails.User

fun UserEntity.toDTO() = UserResponseDTO(
    id = id,
    name = name,
    email = email,
    createdAt = createdAt,
    avatarUrl = avatarUrl,
)

fun UserRegisterDTO.toUserEntity(encodedPass: String) = UserEntity(
    email = email,
    passwordHash = encodedPass,
    name = name,
    avatarUrl = avatarUrl,
)
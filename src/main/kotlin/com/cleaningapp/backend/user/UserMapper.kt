package com.cleaningapp.backend.user

import org.springframework.security.core.userdetails.UserDetails

typealias SpringUser = org.springframework.security.core.userdetails.User

fun UserEntity.toDTO() = UserResponseDTO(
    id = id,
    firebaseUid = firebaseUid,
    name = name,
    email = email,
    createdAt = createdAt,
    avatarUrl = avatarUrl,
)

fun UserRegisterDTO.toUserEntity(firebaseUid: String) = UserEntity(
    firebaseUid = firebaseUid,
    email = email,
    name = name,
    avatarUrl = avatarUrl,
)

fun UserEntity.toUserDetails(): UserDetails =
    SpringUser.builder()
        .username(email)
        .password("") // ? null мб
        .roles("USER")
        .build()
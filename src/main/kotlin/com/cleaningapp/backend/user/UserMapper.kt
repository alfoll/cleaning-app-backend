package com.cleaningapp.backend.user

import org.springframework.security.core.userdetails.UserDetails

typealias SpringUser = org.springframework.security.core.userdetails.User

fun UserEntity.toDTO() = UserResponseDTO(
    id = id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
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
        .username(firebaseUid)
        .password(null)
        .roles("USER")
        .build()
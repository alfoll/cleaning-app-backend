package com.cleaningapp.backend.user

data class UserRegisterDTO(
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
)

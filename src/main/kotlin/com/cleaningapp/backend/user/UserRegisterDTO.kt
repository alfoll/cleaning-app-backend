package com.cleaningapp.backend.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UserRegisterDTO(
    @field:NotBlank
    @field:Size(min = 2, max = 30, message = "Name must be between 2 and 30")
    val name: String,

    @field:Email
    @field:NotBlank
    val email: String,

    val avatarUrl: String? = null,
)

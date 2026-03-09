package com.cleaningapp.backend.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UserRegisterDTO(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 30, message = "Name must be between 2 and 30")
    val name: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email format is invalid")
    @field:Size(max = 254, message = "Email must be at most 254 characters")
    val email: String,

    @field:Size(max = 500, message = "Avatar URL must be at most 500 characters")
    val avatarUrl: String? = null,
)

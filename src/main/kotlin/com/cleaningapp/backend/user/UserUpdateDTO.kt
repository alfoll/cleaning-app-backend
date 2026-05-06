package com.cleaningapp.backend.user

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


data class UserUpdateDTO(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 30, message = "Name must be between 2 and 30")
    val name: String,

    @field:Size(max = 255, message = "Avatar URL must be at most 255 characters")
    val avatarUrl: String? = null,
)

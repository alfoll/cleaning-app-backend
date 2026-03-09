package com.cleaningapp.backend.household

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


data class HouseholdRegisterDTO(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
    val name: String,
)

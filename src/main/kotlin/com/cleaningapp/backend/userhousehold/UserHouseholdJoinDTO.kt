package com.cleaningapp.backend.userhousehold

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UserHouseholdJoinDTO(
    @field:NotBlank(message = "Invite code is required")
    @field:Size(min = 8, max = 8, message = "Invite code must be exactly 8 characters")
    @field:Pattern(
        regexp = "^[A-Za-z0-9]{8}$",
        message = "Invite code must contain only Latin letters and digits"
    )
    val inviteCode: String,
)
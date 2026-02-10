package com.cleaningapp.backend.household

import java.util.UUID

data class HouseholdRegisterDTO(
    val name: String,
    val createdByUser: UUID,
)

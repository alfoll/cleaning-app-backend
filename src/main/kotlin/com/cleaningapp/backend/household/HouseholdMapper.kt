package com.cleaningapp.backend.household

fun HouseholdEntity.toDto() = HouseholdResponseDTO(
    id = id,
    name = name,
    inviteCode = inviteCode,
    createdAt = createdAt,
    createdByUser = createdByUser,
    isActive = isActive,
)

fun HouseholdRegisterDTO.toHouseholdEntity() = HouseholdEntity(
    name = name,
    createdByUser = createdByUser,
)
package com.cleaningapp.backend.household

fun HouseholdEntity.toDto() = HouseholdResponseDTO(
    id = id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
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
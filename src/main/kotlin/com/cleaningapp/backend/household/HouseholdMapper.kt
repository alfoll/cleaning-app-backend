package com.cleaningapp.backend.household

import com.cleaningapp.backend.user.UserEntity

fun HouseholdEntity.toDto() = HouseholdResponseDTO(
    id = id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
    name = name,
    inviteCode = inviteCode,
    createdAt = createdAt,
    createdByUser = createdByUser.id!!,
    isActive = isActive,
)

fun HouseholdRegisterDTO.toHouseholdEntity(user: UserEntity) : HouseholdEntity =
    HouseholdEntity(
    name = name,
        )
        .apply { createdByUser = user }
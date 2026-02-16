package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity

fun UserHouseholdEntity.toDto() : UserHouseholdResponseDTO =
    UserHouseholdResponseDTO(
        id = id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
        householdId = household.id!!,
        balance = balance,
        joinedAt = joinedAt,
        isUserActive = isUserActive,
    )

fun UserHouseholdJoinDTO.toUserHouseholdEntity(
    user: UserEntity,
    household: HouseholdEntity
) : UserHouseholdEntity = UserHouseholdEntity()
    .apply {
        this.user = user
        this.household = household
    }
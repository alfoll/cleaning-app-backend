package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity

fun UserHouseholdEntity.toDto() : UserHouseholdResponseDTO =
    UserHouseholdResponseDTO(
        id = id,
        householdId = household.id,
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
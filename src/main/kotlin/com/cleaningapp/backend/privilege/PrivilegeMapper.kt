package com.cleaningapp.backend.privilege

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity

fun PrivilegeEntity.toDto(): PrivilegeResponseDTO = PrivilegeResponseDTO(
    id = id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)

    householdId = household.id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
    createdBy = createdBy.id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
    createdAt = createdAt,

    title = title,
    description = description,
    cost = cost,

    isAvailable = isAvailable,
    boughtBy = boughtBy?.user?.id, // может быть null (никем не куплена), на фронт передаю именно ЮЗЕРА
)

fun PrivilegeRegisterDTO.toPrivilegeEntity(household: HouseholdEntity,
                                           creator: UserEntity): PrivilegeEntity =
    PrivilegeEntity(
        title = title,
        description = description,
        cost = cost,
    ).apply {
        this.household = household
        this.createdBy = creator
    }
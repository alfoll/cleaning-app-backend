package com.cleaningapp.backend.activity

fun ActivityEntity.toDto(): ActivityResponseDTO = ActivityResponseDTO(
    id = id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)

    householdId = household.id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
    userId = member.user.id!!, // наружу передаю User.id а не id участия

    activityType = activityType,
    createdAt = createdAt,
    title = title,
    description = description,
)
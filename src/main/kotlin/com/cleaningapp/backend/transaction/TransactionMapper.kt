package com.cleaningapp.backend.transaction

fun TransactionEntity.toDto(): TransactionResponseDTO = TransactionResponseDTO(
    id = id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)

    householdId = household.id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
    userId = member.user.id!!, // наружу передаю User.id а не id участия

    amount = amount,
    type = type,
    createdAt = createdAt,

    // что то одно будет заполнено, второе null
    taskId = task?.id,
    privilegeId = privilege?.id,
)
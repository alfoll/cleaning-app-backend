package com.cleaningapp.backend.transaction

import java.util.UUID

data class BalanceResetTransactionCommand(
    val householdId: UUID, // в каком хозяйства
    val memberId: UUID, // кто совершил

    // привилегии/задачи нет
    // баланс будет списан полностью
)
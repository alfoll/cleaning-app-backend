package com.cleaningapp.backend.transaction

import java.util.UUID

data class TaskCompletionTransactionCommand(
    val householdId: UUID, // в каком хозяйства
    val memberId: UUID, // кто совершил
    val taskId: UUID, // id задачи с которой связана транзакция
)

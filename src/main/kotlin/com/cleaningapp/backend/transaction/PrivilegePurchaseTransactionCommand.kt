package com.cleaningapp.backend.transaction

import java.util.UUID

data class PrivilegePurchaseTransactionCommand(
    val householdId: UUID, // в каком хозяйства
    val memberId: UUID, // кто совершил
    val privilegeId: UUID, // id привилегии с которой связана транзакция
    )

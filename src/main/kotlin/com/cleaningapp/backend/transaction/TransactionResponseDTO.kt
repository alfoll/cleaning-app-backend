package com.cleaningapp.backend.transaction

import java.time.LocalDateTime
import java.util.UUID

data class TransactionResponseDTO(
    val id: UUID, // когда используется дто, id уже гарантированно не null (null только до сохранения в бд)
    val householdId: UUID, // точно не null, так как хозяйство уже известно
    val userId: UUID, // точно не null, наружу отдаю User.id а не UserHousehold.id

    val amount: Int,
    val type: TransactionType,
    val createdAt: LocalDateTime,

    // что то одно из этого -> nullable
    val taskId: UUID? = null,
    val privilegeId: UUID? = null,

)

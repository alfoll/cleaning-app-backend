package com.cleaningapp.backend.task

import java.time.LocalDateTime
import java.util.UUID

data class TaskResponseDTO(
    val id: UUID, // когда используется дто, id уже гарантированно не null (null только до сохранения в бд)
    val householdId: UUID, // точно не null, так как хозяйство уже известно
    val createdBy: UUID, // точно не null, так как юзер уже известен
    val createdAt: LocalDateTime,

    val title: String,
    val description: String? = null,
    val reward: Int,

    val isAssigned: Boolean = false, // в сущности нет, только в дто для облегчения
    val assignedTo: UUID? = null,
    val assignedAt: LocalDateTime? = null,

    val isCompleted: Boolean = false,
    val completedBy: UUID? = null,
    val completedAt: LocalDateTime? = null,
)

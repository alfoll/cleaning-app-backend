package com.cleaningapp.backend.tasktemplate

import java.time.LocalDateTime
import java.util.UUID

data class TaskTemplateResponseDTO(
    val id: UUID,
    val title: String,
    val description: String?,
    val reward: Int,
    val createdAt: LocalDateTime,
    val createdBy: UUID,
    val householdId: UUID,
)

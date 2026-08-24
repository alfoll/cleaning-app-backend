package com.cleaningapp.backend.task

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class TaskRegisterDTO(
    @field:NotBlank(message = "Title is required")
    @field:Size(min = 2, max = 120, message = "Title must be between 2 and 120 characters")
    val title: String,

    @field:Size(max = 2000, message = "Description must be at most 2000 characters")
    val description: String? = null,

    @field:Min(5, message = "Reward must be at least 5")
    @field:Max(100, message = "Reward must be at most 100")
    val reward: Int, // пока что решено что можно менять до брони/завершения но в дто это не нужно

    val dueAt: LocalDateTime? = null,
)

package com.cleaningapp.backend.privilege

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


data class PrivilegeRegisterDTO(
    @field:NotBlank(message = "Title is required")
    @field:Size(min = 2, max = 120, message = "Title must be between 2 and 120 characters")
    val title: String,

    @field:Size(max = 2000, message = "Description must be at most 2000 characters")
    val description: String? = null,

    // разобраться с границами cost
    @field:Min(5, message = "Cost must be at least 5")
    @field:Max(500, message = "Cost must be at most 500")
    val cost: Int // можно менять до покупки но в дто это не нужно - стоит ли?
)

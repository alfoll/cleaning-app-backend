package com.cleaningapp.backend.exception

import java.time.LocalDateTime

data class ExcResponse(
    val error: String,
    val message: String? = null,
    val time: LocalDateTime = LocalDateTime.now(),
)

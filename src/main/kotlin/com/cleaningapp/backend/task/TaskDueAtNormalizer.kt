package com.cleaningapp.backend.task

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

object TaskDueDatePolicy {
    fun endOfDay(date: LocalDate): LocalDateTime =
        date.atTime(23, 59, 59, 999_999_000)
}

@Component
class TaskDueAtNormalizer(
    private val clock: Clock,
) {
    fun normalize(dueAt: LocalDateTime?): LocalDateTime? {
        if (dueAt == null) return null

        val dueDate = dueAt.toLocalDate()
        if (dueDate.isBefore(LocalDate.now(clock)))
            throw IllegalArgumentException("Task due date cannot be in the past")

        return TaskDueDatePolicy.endOfDay(dueDate)
    }
}

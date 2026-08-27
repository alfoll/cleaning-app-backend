package com.cleaningapp.backend.task

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TaskDueAtNormalizerTest {

    private val clock = Clock.fixed(
        Instant.parse("2026-08-27T12:00:00Z"),
        ZoneOffset.UTC,
    )
    private val normalizer = TaskDueAtNormalizer(clock)

    @Test
    fun `normalizer should allow today and normalize to end of day`() {
        val today = LocalDate.of(2026, 8, 27)

        assertThat(normalizer.normalize(today.atStartOfDay()))
            .isEqualTo(TaskDueDatePolicy.endOfDay(today))
    }

    @Test
    fun `normalizer should allow future date and normalize to end of day`() {
        val future = LocalDate.of(2026, 9, 10)

        assertThat(normalizer.normalize(future.atTime(8, 30)))
            .isEqualTo(TaskDueDatePolicy.endOfDay(future))
    }

    @Test
    fun `normalizer should reject past date`() {
        assertThatThrownBy {
            normalizer.normalize(LocalDate.of(2026, 8, 20).atStartOfDay())
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Task due date cannot be in the past")
    }
}

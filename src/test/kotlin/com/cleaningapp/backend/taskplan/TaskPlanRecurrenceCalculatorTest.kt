package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.task.TaskDueDatePolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TaskPlanRecurrenceCalculatorTest {

    private val calculator = TaskPlanRecurrenceCalculator()

    @Test
    fun `daily should calculate next calendar day at end of day`() {
        val currentDueAt = endOfDay(LocalDate.of(2026, 9, 10))

        val nextDueAt = calculator.calculateNextDueAt(currentDueAt, RecurrenceType.DAILY)

        assertThat(nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 9, 11)))
    }

    @Test
    fun `weekly should calculate seven calendar days later at end of day`() {
        val currentDueAt = endOfDay(LocalDate.of(2026, 9, 10))

        val nextDueAt = calculator.calculateNextDueAt(currentDueAt, RecurrenceType.WEEKLY)

        assertThat(nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 9, 17)))
    }

    @Test
    fun `monthly normal date should preserve anchor day`() {
        val firstDueAt = endOfDay(LocalDate.of(2026, 1, 15))

        val schedule = calculator.createSchedule(firstDueAt, RecurrenceType.MONTHLY)

        assertThat(schedule.monthlyAnchorDay).isEqualTo(15)
        assertThat(schedule.monthlyLastDay).isFalse()
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 2, 15)))
    }

    @Test
    fun `monthly last day should follow month ends in non leap year`() {
        val schedule = calculator.createSchedule(
            endOfDay(LocalDate.of(2026, 1, 31)),
            RecurrenceType.MONTHLY,
        )

        val february = schedule.nextDueAt
        val march = calculator.calculateNextDueAt(
            february,
            RecurrenceType.MONTHLY,
            schedule.monthlyAnchorDay,
            schedule.monthlyLastDay,
        )
        val april = calculator.calculateNextDueAt(
            march,
            RecurrenceType.MONTHLY,
            schedule.monthlyAnchorDay,
            schedule.monthlyLastDay,
        )

        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isTrue()
        assertThat(february).isEqualTo(endOfDay(LocalDate.of(2026, 2, 28)))
        assertThat(march).isEqualTo(endOfDay(LocalDate.of(2026, 3, 31)))
        assertThat(april).isEqualTo(endOfDay(LocalDate.of(2026, 4, 30)))
    }

    @Test
    fun `monthly last day should use leap day then restore month end`() {
        val schedule = calculator.createSchedule(
            endOfDay(LocalDate.of(2024, 1, 31)),
            RecurrenceType.MONTHLY,
        )

        val march = calculator.calculateNextDueAt(
            schedule.nextDueAt,
            RecurrenceType.MONTHLY,
            schedule.monthlyAnchorDay,
            schedule.monthlyLastDay,
        )

        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2024, 2, 29)))
        assertThat(march).isEqualTo(endOfDay(LocalDate.of(2024, 3, 31)))
    }

    @Test
    fun `monthly anchor 30 should recover after short month`() {
        val schedule = calculator.createSchedule(
            endOfDay(LocalDate.of(2026, 1, 30)),
            RecurrenceType.MONTHLY,
        )

        val march = calculator.calculateNextDueAt(
            schedule.nextDueAt,
            RecurrenceType.MONTHLY,
            schedule.monthlyAnchorDay,
            schedule.monthlyLastDay,
        )

        assertThat(schedule.monthlyAnchorDay).isEqualTo(30)
        assertThat(schedule.monthlyLastDay).isFalse()
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 2, 28)))
        assertThat(march).isEqualTo(endOfDay(LocalDate.of(2026, 3, 30)))
    }

    @Test
    fun `monthly anchor 29 should recover after non leap February`() {
        val schedule = calculator.createSchedule(
            endOfDay(LocalDate.of(2026, 1, 29)),
            RecurrenceType.MONTHLY,
        )

        val march = calculator.calculateNextDueAt(
            schedule.nextDueAt,
            RecurrenceType.MONTHLY,
            schedule.monthlyAnchorDay,
            schedule.monthlyLastDay,
        )

        assertThat(schedule.monthlyAnchorDay).isEqualTo(29)
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 2, 28)))
        assertThat(march).isEqualTo(endOfDay(LocalDate.of(2026, 3, 29)))
    }

    private fun endOfDay(date: LocalDate) = TaskDueDatePolicy.endOfDay(date)
}

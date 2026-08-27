package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.task.TaskDueDatePolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TaskPlanRecurrenceCalculatorTest {

    private val calculator = TaskPlanRecurrenceCalculator()

    @Test
    fun `daily schedule from start date should create first and next deadlines`() {
        val schedule = calculator.createScheduleFromStartDate(
            startDate = LocalDate.of(2026, 8, 1),
            recurrenceType = RecurrenceType.DAILY,
        )

        assertThat(schedule.firstDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 2)))
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 3)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isFalse()
    }

    @Test
    fun `weekly schedule from start date should create first and next deadlines`() {
        val schedule = calculator.createScheduleFromStartDate(
            startDate = LocalDate.of(2026, 8, 1),
            recurrenceType = RecurrenceType.WEEKLY,
        )

        assertThat(schedule.firstDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 8)))
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 8, 15)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isFalse()
    }

    @Test
    fun `monthly schedule from normal start date should preserve start day anchor`() {
        val schedule = calculator.createScheduleFromStartDate(
            startDate = LocalDate.of(2026, 8, 10),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.firstDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 9, 10)))
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 10, 10)))
        assertThat(schedule.monthlyAnchorDay).isEqualTo(10)
        assertThat(schedule.monthlyLastDay).isFalse()
    }

    @Test
    fun `monthly schedule from January 30 should recover anchor after non leap February`() {
        val schedule = calculator.createScheduleFromStartDate(
            startDate = LocalDate.of(2027, 1, 30),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.firstDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 2, 28)))
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 3, 30)))
        assertThat(schedule.monthlyAnchorDay).isEqualTo(30)
        assertThat(schedule.monthlyLastDay).isFalse()
    }

    @Test
    fun `monthly schedule from January 30 should recover anchor after leap February`() {
        val schedule = calculator.createScheduleFromStartDate(
            startDate = LocalDate.of(2028, 1, 30),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.firstDueAt).isEqualTo(endOfDay(LocalDate.of(2028, 2, 29)))
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2028, 3, 30)))
        assertThat(schedule.monthlyAnchorDay).isEqualTo(30)
        assertThat(schedule.monthlyLastDay).isFalse()
    }

    @Test
    fun `monthly schedule from January last day should keep last day semantics`() {
        val schedule = calculator.createScheduleFromStartDate(
            startDate = LocalDate.of(2027, 1, 31),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.firstDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 2, 28)))
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 3, 31)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isTrue()
    }

    @Test
    fun `monthly schedule from non leap February last day should keep last day semantics`() {
        val schedule = calculator.createScheduleFromStartDate(
            startDate = LocalDate.of(2027, 2, 28),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.firstDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 3, 31)))
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 4, 30)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isTrue()
    }

    @Test
    fun `monthly schedule from leap day should keep last day semantics`() {
        val schedule = calculator.createScheduleFromStartDate(
            startDate = LocalDate.of(2028, 2, 29),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.firstDueAt).isEqualTo(endOfDay(LocalDate.of(2028, 3, 31)))
        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2028, 4, 30)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isTrue()
    }

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

    @Test
    fun `monthly metadata should be derived without advancing reference due date`() {
        val metadata = calculator.createRecurrenceMetadata(
            referenceAt = endOfDay(LocalDate.of(2026, 1, 31)),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(metadata.monthlyAnchorDay).isNull()
        assertThat(metadata.monthlyLastDay).isTrue()
    }

    @Test
    fun `non monthly metadata should clear monthly state`() {
        listOf(RecurrenceType.DAILY, RecurrenceType.WEEKLY).forEach { recurrenceType ->
            val metadata = calculator.createRecurrenceMetadata(
                referenceAt = endOfDay(LocalDate.of(2026, 1, 30)),
                recurrenceType = recurrenceType,
            )

            assertThat(metadata.monthlyAnchorDay).isNull()
            assertThat(metadata.monthlyLastDay).isFalse()
        }
    }

    @Test
    fun `overdue daily completion should restart schedule from completion date`() {
        val schedule = calculator.recalculateAfterOverdueCompletion(
            completedAt = LocalDate.of(2026, 9, 13).atTime(10, 30),
            recurrenceType = RecurrenceType.DAILY,
        )

        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 9, 14)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isFalse()
    }

    @Test
    fun `overdue weekly completion should restart schedule seven calendar days after completion`() {
        val schedule = calculator.recalculateAfterOverdueCompletion(
            completedAt = LocalDate.of(2026, 9, 20).atTime(22, 15),
            recurrenceType = RecurrenceType.WEEKLY,
        )

        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2026, 9, 27)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isFalse()
    }

    @Test
    fun `overdue monthly completion on normal day should replace old month end anchor`() {
        val schedule = calculator.recalculateAfterOverdueCompletion(
            completedAt = LocalDate.of(2027, 2, 5).atTime(9, 0),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 3, 5)))
        assertThat(schedule.monthlyAnchorDay).isEqualTo(5)
        assertThat(schedule.monthlyLastDay).isFalse()
    }

    @Test
    fun `overdue monthly completion on last day should establish month end anchor`() {
        val schedule = calculator.recalculateAfterOverdueCompletion(
            completedAt = LocalDate.of(2027, 2, 28).atTime(12, 0),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2027, 3, 31)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isTrue()
    }

    @Test
    fun `overdue monthly completion on leap day should establish month end anchor`() {
        val schedule = calculator.recalculateAfterOverdueCompletion(
            completedAt = LocalDate.of(2028, 2, 29).atTime(12, 0),
            recurrenceType = RecurrenceType.MONTHLY,
        )

        assertThat(schedule.nextDueAt).isEqualTo(endOfDay(LocalDate.of(2028, 3, 31)))
        assertThat(schedule.monthlyAnchorDay).isNull()
        assertThat(schedule.monthlyLastDay).isTrue()
    }

    private fun endOfDay(date: LocalDate) = TaskDueDatePolicy.endOfDay(date)
}

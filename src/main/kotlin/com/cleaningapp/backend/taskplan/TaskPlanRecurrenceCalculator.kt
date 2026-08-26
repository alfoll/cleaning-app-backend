package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.task.TaskDueDatePolicy
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class TaskPlanSchedule(
    val nextDueAt: LocalDateTime,
    val monthlyAnchorDay: Int?,
    val monthlyLastDay: Boolean,
)

data class TaskPlanRecurrenceMetadata(
    val monthlyAnchorDay: Int?,
    val monthlyLastDay: Boolean,
)

@Component
class TaskPlanRecurrenceCalculator {

    fun createSchedule(
        firstDueAt: LocalDateTime,
        recurrenceType: RecurrenceType,
    ): TaskPlanSchedule {
        val metadata = createRecurrenceMetadata(firstDueAt, recurrenceType)

        return TaskPlanSchedule(
            nextDueAt = calculateNextDueAt(
                currentDueAt = firstDueAt,
                recurrenceType = recurrenceType,
                monthlyAnchorDay = metadata.monthlyAnchorDay,
                monthlyLastDay = metadata.monthlyLastDay,
            ),
            monthlyAnchorDay = metadata.monthlyAnchorDay,
            monthlyLastDay = metadata.monthlyLastDay,
        )
    }

    fun createRecurrenceMetadata(
        referenceAt: LocalDateTime,
        recurrenceType: RecurrenceType,
    ): TaskPlanRecurrenceMetadata {
        if (recurrenceType != RecurrenceType.MONTHLY) {
            return TaskPlanRecurrenceMetadata(
                monthlyAnchorDay = null,
                monthlyLastDay = false,
            )
        }

        val referenceDate = referenceAt.toLocalDate()
        val monthlyLastDay = referenceDate.isLastDayOfMonth()

        return TaskPlanRecurrenceMetadata(
            monthlyAnchorDay = referenceDate.dayOfMonth.takeUnless { monthlyLastDay },
            monthlyLastDay = monthlyLastDay,
        )
    }

    fun calculateNextDueAt(
        currentDueAt: LocalDateTime,
        recurrenceType: RecurrenceType,
        monthlyAnchorDay: Int? = null,
        monthlyLastDay: Boolean = false,
    ): LocalDateTime {
        val currentDate = currentDueAt.toLocalDate()
        val nextDate = when (recurrenceType) {
            RecurrenceType.DAILY -> currentDate.plusDays(1)
            RecurrenceType.WEEKLY -> currentDate.plusDays(7)
            RecurrenceType.MONTHLY -> calculateNextMonthlyDate(
                currentDate = currentDate,
                monthlyAnchorDay = monthlyAnchorDay,
                monthlyLastDay = monthlyLastDay,
            )
        }

        return TaskDueDatePolicy.endOfDay(nextDate)
    }

    fun recalculateAfterOverdueCompletion(
        completedAt: LocalDateTime,
        recurrenceType: RecurrenceType,
    ): TaskPlanSchedule = createSchedule(
        firstDueAt = completedAt,
        recurrenceType = recurrenceType,
    )

    private fun calculateNextMonthlyDate(
        currentDate: LocalDate,
        monthlyAnchorDay: Int?,
        monthlyLastDay: Boolean,
    ): LocalDate {
        val nextMonth = YearMonth.from(currentDate).plusMonths(1)

        if (monthlyLastDay)
            return nextMonth.atEndOfMonth()

        val anchorDay = requireNotNull(monthlyAnchorDay) {
            "Monthly recurrence requires an anchor day or last-day rule"
        }
        require(anchorDay in 1..31) {
            "Monthly anchor day must be between 1 and 31"
        }

        return nextMonth.atDay(minOf(anchorDay, nextMonth.lengthOfMonth()))
    }

    private fun LocalDate.isLastDayOfMonth(): Boolean =
        dayOfMonth == lengthOfMonth()
}

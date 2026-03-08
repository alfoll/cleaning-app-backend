package com.cleaningapp.backend.task

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TaskRepository: JpaRepository<TaskEntity, UUID> {
    // задачи хозяйства
    fun findAllByHouseholdId(householdId: UUID): List<TaskEntity>

    // выполненные и не выполненные задачи хозяйства
    fun findAllByHouseholdIdAndIsCompletedFalse(householdId: UUID): List<TaskEntity>
    fun findAllByHouseholdIdAndIsCompletedTrue(householdId: UUID): List<TaskEntity>

    // чтобы освобождать забронированные невыполненные задачи
    // МОИ забронированные и не выполненные - зачем если есть следующий метод? - для освобождения по всем хозяйствам
    fun findAllByAssignedToIdAndIsCompletedFalse(assignedToId: UUID): List<TaskEntity>

    // МОИ забронированные задачи в хозяйстве
    fun findAllByHouseholdIdAndAssignedToIdAndIsCompletedFalse(
        householdId: UUID,
        assignedToId: UUID
    ): List<TaskEntity>

    // не забронированные и не выполненные - свободные задачи
    fun findAllByHouseholdIdAndAssignedToIsNullAndIsCompletedFalse(
        householdId: UUID
    ): List<TaskEntity>
}
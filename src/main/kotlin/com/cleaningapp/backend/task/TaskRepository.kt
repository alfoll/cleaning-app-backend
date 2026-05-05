package com.cleaningapp.backend.task

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock

interface TaskRepository: JpaRepository<TaskEntity, UUID> {
    // задачи хозяйства (сорт по созданию сначалп последние)
    fun findAllByHouseholdIdOrderByCreatedAtDesc(householdId: UUID): List<TaskEntity>

    // выполненные и не выполненные задачи хозяйства (сорт по выполнению - сначала последние)
    fun findAllByHouseholdIdAndIsCompletedTrueOrderByCompletedAtDesc(householdId: UUID): List<TaskEntity>

    // чтобы освобождать забронированные невыполненные задачи (сорт не нужен - внуренний метод)
    // МОИ забронированные и не выполненные - зачем если есть следующий метод? - для освобождения по всем хозяйствам
    fun findAllByAssignedToIdAndIsCompletedFalse(assignedToId: UUID): List<TaskEntity>

    // МОИ забронированные задачи в хозяйстве (сорт по брони - сначала последние)
    fun findAllByHouseholdIdAndAssignedToIdAndIsCompletedFalseOrderByAssignedAtDesc(
        householdId: UUID,
        assignedToId: UUID
    ): List<TaskEntity>

    // не забронированные и не выполненные - свободные задачи (сорт по созданию - сначала последние)
    fun findAllByHouseholdIdAndAssignedToIsNullAndIsCompletedFalseOrderByCreatedAtDesc(
        householdId: UUID
    ): List<TaskEntity>

    // bulk delete
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from TaskEntity t where t.household.id = :householdId")
    fun deleteAllByHouseholdId(@Param("householdId") householdId: UUID): Int

    // блокирующий запрос на задачу
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskEntity t where t.id = :taskId")
    fun findByIdForUpdate(@Param("taskId") taskId: UUID): TaskEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select t
        from TaskEntity t
        where t.assignedTo.id = :assignedToId
          and t.isCompleted = false
        order by t.id
        """
    )
    fun findAllByAssignedToIdAndIsCompletedFalseForUpdate(
        @Param("assignedToId") assignedToId: UUID,
    ): List<TaskEntity>

    // возвращает id хозяйства без блокировки - нужно для порядка блокировки в сервисах
    @Query("select t.household.id from TaskEntity t where t.id = :taskId")
    fun findHouseholdIdByTaskId(@Param("taskId") taskId: UUID): UUID?
}
package com.cleaningapp.backend.transaction

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TransactionRepository: JpaRepository<TransactionEntity, UUID> {

    // транзакции пользователя
    fun findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(householdId: UUID, memberId: UUID): List<TransactionEntity>

    // была ли уже транзакция на задачу
    fun existsByTaskId(taskId: UUID): Boolean

    // была ли уже транзакция на привилегию
    fun existsByPrivilegeId(privilegeId: UUID): Boolean

    // bulk delete
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from TransactionEntity t where t.household.id = :householdId")
    fun deleteAllByHouseholdId(@Param ("householdId") householdId: UUID): Int
}
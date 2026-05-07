package com.cleaningapp.backend.transaction

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph

interface TransactionRepository: JpaRepository<TransactionEntity, UUID> {

    // транзакции пользователя
    fun findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
        householdId: UUID,
        memberId: UUID
    ): List<TransactionEntity>
    // Pageable
    @EntityGraph(attributePaths = ["member", "member.user"])
    fun findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
        householdId: UUID,
        memberId: UUID,
        pageable: Pageable,
    ): List<TransactionEntity>


    // была ли уже транзакция на задачу
    fun existsByTaskId(taskId: UUID): Boolean

    // была ли уже транзакция на привилегию
    fun existsByPrivilegeId(privilegeId: UUID): Boolean

    // bulk delete
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from TransactionEntity t where t.household.id = :householdId")
    fun deleteAllByHouseholdId(@Param ("householdId") householdId: UUID): Int
}
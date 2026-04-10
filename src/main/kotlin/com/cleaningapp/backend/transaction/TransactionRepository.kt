package com.cleaningapp.backend.transaction

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TransactionRepository: JpaRepository<TransactionEntity, UUID> {

    // транзакции пользователя
    fun findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(householdId: UUID, memberId: UUID): List<TransactionEntity>

    // была ли уже транзакция на задачу
    fun existsByTaskId(taskId: UUID): Boolean

    // была ли уже транзакция на привилегию
    fun existsByPrivilegeId(privilegeId: UUID): Boolean
}
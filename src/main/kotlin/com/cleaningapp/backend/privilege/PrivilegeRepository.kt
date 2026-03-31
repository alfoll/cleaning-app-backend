package com.cleaningapp.backend.privilege

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PrivilegeRepository: JpaRepository<PrivilegeEntity, UUID> {
    // все привилегии хозяйства (сорт по созданию сначала последние)
    fun findAllByHouseholdIdOrderByCreatedAtDesc(householdId: UUID): List<PrivilegeEntity>

    // свободные привилегии хозяйства (сорт по созданию сначала последние)
    fun findAllByHouseholdIdAndIsAvailableTrueAndBoughtByIsNullOrderByCreatedAtDesc(
        householdId: UUID,
    ): List<PrivilegeEntity>

    // купленные привилегии (мои) - поиск по bought by id - (сорт по созданию сначала последние)
    fun findAllByHouseholdIdAndBoughtByIdOrderByCreatedAtDesc(
        householdId: UUID,
        boughtBy: UUID,
    ): List<PrivilegeEntity>
}
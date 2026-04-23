package com.cleaningapp.backend.privilege

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    // bulk delete
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from PrivilegeEntity p where p.household.id = :householdId")
    fun deleteAllByHouseholdId(@Param("householdId") householdId: UUID): Int
}
package com.cleaningapp.backend.privilege

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock


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

    // блокирующие запросы на привилегии
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrivilegeEntity p where p.id = :privilegeId")
    fun findByIdForUpdate(@Param("privilegeId") privilegeId: UUID): PrivilegeEntity?

    // возвращает id хозяйства без блокировки - нужно для порядка блокировки в сервисах
    @Query("select p.household.id from PrivilegeEntity p where p.id = :id")
    fun findHouseholdIdByPrivilegeId(@Param("id") id: UUID): UUID?
}
package com.cleaningapp.backend.household

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param


interface HouseholdRepository : JpaRepository<HouseholdEntity, UUID> {
    fun findByInviteCode(inviteCode: String): HouseholdEntity?
    fun existsByInviteCode(inviteCode: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HouseholdEntity h where h.id = :householdId")
    fun findByIdForUpdate(@Param("householdId") householdId: UUID): HouseholdEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HouseholdEntity h where h.inviteCode = :inviteCode")
    fun findByInviteCodeForUpdate(@Param("inviteCode") inviteCode: String): HouseholdEntity?
}
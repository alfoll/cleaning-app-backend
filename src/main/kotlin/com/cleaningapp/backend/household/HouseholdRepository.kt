package com.cleaningapp.backend.household

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HouseholdRepository : JpaRepository<HouseholdEntity, UUID> {
    fun findByInviteCode(inviteCode: String): HouseholdEntity?
    fun existsByInviteCode(inviteCode: String): Boolean
}
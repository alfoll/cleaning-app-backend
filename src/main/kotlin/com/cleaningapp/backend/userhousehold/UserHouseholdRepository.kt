package com.cleaningapp.backend.userhousehold

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserHouseholdRepository : JpaRepository<UserHouseholdEntity, UUID> {
    fun findByUserIdAndHouseholdId(userId: UUID, householdId: UUID): UserHouseholdEntity?
}
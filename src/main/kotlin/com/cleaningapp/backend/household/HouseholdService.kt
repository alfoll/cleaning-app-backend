package com.cleaningapp.backend.household

import java.util.UUID

interface HouseholdService {
    fun createHousehold(creatorId: UUID, household: HouseholdRegisterDTO): HouseholdResponseDTO
    fun deleteHousehold(householdId: UUID)

    fun updateHousehold(householdId: UUID, newHousehold: HouseholdRegisterDTO): HouseholdResponseDTO

    fun findHouseholdById(id: UUID): HouseholdResponseDTO
    fun findHouseholdByInviteCode(inviteCode: String): HouseholdResponseDTO

}
package com.cleaningapp.backend.household

import java.util.UUID

interface HouseholdService {
    fun createHousehold(household: HouseholdRegisterDTO): HouseholdResponseDTO
    fun deleteHousehold(householdId: UUID)

    fun updateHousehold(householdId: UUID, newHousehold: HouseholdRegisterDTO): HouseholdResponseDTO

    fun findHouseholdById(householdId: UUID): HouseholdResponseDTO
    fun findHouseholdByInviteCode(inviteCode: String): HouseholdResponseDTO

    // удаление хозяйства
    fun deleteHouseholdFromSystem(householdId: UUID)
}
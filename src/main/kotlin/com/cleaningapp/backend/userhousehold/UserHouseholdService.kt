package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.user.UserResponseDTO
import java.util.UUID

interface UserHouseholdService {
    fun joinHousehold(inviteCode: String): UserHouseholdResponseDTO
    fun leaveHousehold(householdId: UUID)

    fun getUserHouseholds(): List<UserHouseholdResponseDTO>
    fun getHouseholdMembers(householdId: UUID): List<UserResponseDTO>

    fun updateUserHousehold(householdId: UUID, updateDTO: UserHouseholdUpdateDTO): UserHouseholdResponseDTO
}
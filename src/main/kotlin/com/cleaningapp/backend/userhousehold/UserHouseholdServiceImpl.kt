package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.household.HouseholdService
import com.cleaningapp.backend.user.UserResponseDTO
import com.cleaningapp.backend.user.UserService
import java.util.UUID

class UserHouseholdServiceImpl(
    private val userHouseholdRepository: UserHouseholdRepository,
    private val householdService: HouseholdService,
    private val userService: UserService,
) : UserHouseholdService {

    override fun joinHousehold(inviteCode: String): UserHouseholdResponseDTO {
        TODO("Not yet implemented")
    }

    override fun leaveHousehold(householdId: UUID) {
        TODO("Not yet implemented")
    }

    override fun getUserHouseholds(): List<UserHouseholdResponseDTO> {
        TODO("Not yet implemented")
    }

    override fun getHouseholdMembers(householdId: UUID): List<UserResponseDTO> {
        TODO("Not yet implemented")
    }

    override fun updateUserHousehold(householdId: UUID, updateDTO: UserHouseholdUpdateDTO): UserHouseholdResponseDTO {
        TODO("Not yet implemented")
    }
}
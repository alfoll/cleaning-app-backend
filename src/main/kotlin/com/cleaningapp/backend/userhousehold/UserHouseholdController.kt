package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.user.UserResponseDTO
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/households")
class UserHouseholdController(
    private val userHouseholdService: UserHouseholdService,
) {
    // вступить в хозяйство
    @PostMapping("/join")
    fun joinHousehold(@RequestBody join: UserHouseholdJoinDTO,): UserHouseholdResponseDTO =
        userHouseholdService.joinHousehold(join.inviteCode)

    // выйти из хозяйства
    @DeleteMapping("/{householdId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveHousehold(@PathVariable householdId: UUID,) =
        userHouseholdService.leaveHousehold(householdId)

    // получить список хозяйств пользователя (мб в users/me?)
    @GetMapping("/myHouseholds")
    fun getUserHouseholds(): List<UserHouseholdResponseDTO> =
        userHouseholdService.getUserHouseholds()

    // получить участников хозяйства
    @GetMapping("/{householdId}/ members")
    fun getHouseholdMembers(@PathVariable householdId: UUID,): List<UserResponseDTO> =
        userHouseholdService.getHouseholdMembers(householdId)


    // удалить юзера из хозяйства
    @DeleteMapping("/{householdId}/members/{userToRemoveId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeUserFromHousehold(@PathVariable householdId: UUID,
                                @PathVariable userToRemoveId: UUID,) =
        userHouseholdService.removeUserFromHousehold(householdId, userToRemoveId)

}
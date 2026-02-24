package com.cleaningapp.backend.household

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/household")
class HouseholdController(
    private val householdService: HouseholdService,
) {
    // нужно ли @AuthenticationPrincipal userDetails: UserDetails?

    // создать хозяйство
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createHousehold(@RequestBody household: HouseholdRegisterDTO): HouseholdResponseDTO =
        householdService.createHousehold(household)

    // обновить хозяйство (имя)
    @PutMapping("/{householdId}")
    fun updateHousehold(@PathVariable householdId: UUID,
                        @RequestBody newHousehold: HouseholdRegisterDTO): HouseholdResponseDTO =
        householdService.updateHousehold(householdId, newHousehold)

    // найти хозяйство по id
    @GetMapping("/{householdId}")
    fun getHousehold(@PathVariable householdId: UUID): HouseholdResponseDTO =
        householdService.findHouseholdById(householdId)

//    // найти хозяйство по инвайт коду - нужно ли, если это делается только при вступлении?
//    // мб для экрана выведения хозяйства в которое вступил/вступает юзер
//    @GetMapping("/invite")
//    fun getInvitedHouseholds(@AuthenticationPrincipal userDetails: UserDetails,
//                             @RequestBody invite: UserHouseholdJoinDTO) : HouseholdResponseDTO =
//        householdService.findHouseholdByInviteCode(invite.inviteCode)

    // удалить
    @DeleteMapping("/{householdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteHousehold(@PathVariable householdId: UUID) =
        householdService.deleteHousehold(householdId)
}
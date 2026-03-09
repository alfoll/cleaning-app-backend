package com.cleaningapp.backend.household

import jakarta.validation.Valid
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
@RequestMapping("/api/households")
class HouseholdController(
    private val householdService: HouseholdService,
) {
    // нужно ли @AuthenticationPrincipal userDetails: UserDetails?
    // - нет так как юзер березтся в сервисе из контеста

    // создать хозяйство
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createHousehold(@Valid @RequestBody household: HouseholdRegisterDTO): HouseholdResponseDTO =
        householdService.createHousehold(household)

    // обновить хозяйство (имя)
    @PutMapping("/{householdId}")
    fun updateHousehold(@PathVariable householdId: UUID,
                        @Valid @RequestBody newHousehold: HouseholdRegisterDTO): HouseholdResponseDTO =
        householdService.updateHousehold(householdId, newHousehold)

    // найти хозяйство по id
    @GetMapping("/{householdId}")
    fun getHousehold(@PathVariable householdId: UUID): HouseholdResponseDTO =
        householdService.findHouseholdById(householdId)


    // удалить
    @DeleteMapping("/{householdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteHousehold(@PathVariable householdId: UUID) =
        householdService.deleteHousehold(householdId)
}
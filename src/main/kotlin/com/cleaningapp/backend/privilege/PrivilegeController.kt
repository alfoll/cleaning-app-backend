package com.cleaningapp.backend.privilege

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class PrivilegeController(
    private val privilegeService: PrivilegeService,
) {
    // создать привилегию в хозяйстве - POST /api/households/{householdId}/privileges
    @PostMapping("/households/{householdId}/privileges")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPrivilege(@PathVariable householdId: UUID,
                        @Valid @RequestBody privilege: PrivilegeRegisterDTO): PrivilegeResponseDTO =
        privilegeService.createPrivilege(householdId, privilege)

    // показать список привилегий с фильтрацией - GET /api/households/{householdId}/privileges
    @GetMapping("/households/{householdId}/privileges")
    fun getHouseholdPrivileges(@PathVariable householdId: UUID,
                               @RequestParam(defaultValue = "ALL") filter: PrivilegeFilterType): List<PrivilegeResponseDTO> =
        privilegeService.getHouseholdPrivileges(householdId, filter)




    // получить привилегию по id - GET /api/privileges/{privilegeId}
    @GetMapping("/privileges/{privilegeId}")
    fun getPrivilegeById(@PathVariable privilegeId: UUID): PrivilegeResponseDTO =
        privilegeService.getPrivilegeById(privilegeId)

    // обновить свободную привилегию - PUT /api/privileges/{privilegeId}
    @PutMapping("/privileges/{privilegeId}")
    fun updatePrivilege(@PathVariable privilegeId: UUID,
                        @Valid @RequestBody newPrivilege: PrivilegeRegisterDTO): PrivilegeResponseDTO =
        privilegeService.updatePrivilege(privilegeId, newPrivilege)

    // удалить свободную привилегию - DELETE /api/privileges/{privilegeId}
    @DeleteMapping("/privileges/{privilegeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePrivilege(@PathVariable privilegeId: UUID) =
        privilegeService.deletePrivilege(privilegeId)

    // купить свободную привилегию - POST /api/privileges/{privilegeId}/buy
    @PostMapping("/privileges/{privilegeId}/buy")
    fun boughtPrivilege(@PathVariable privilegeId: UUID): PrivilegeResponseDTO =
        privilegeService.buyPrivilege(privilegeId)
}
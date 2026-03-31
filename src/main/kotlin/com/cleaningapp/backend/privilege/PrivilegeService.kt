package com.cleaningapp.backend.privilege

import java.util.UUID

interface PrivilegeService {

    // создать привилегию
    fun createPrivilege(householdId: UUID, privilege: PrivilegeRegisterDTO): PrivilegeResponseDTO

    // обновить привилегию
    fun updatePrivilege(privilegeId: UUID, newPrivilege: PrivilegeRegisterDTO): PrivilegeResponseDTO

    // удалить привилегию
    fun deletePrivilege(privilegeId: UUID)

    // купить привилегию
    fun buyPrivilege(privilegeId: UUID): PrivilegeResponseDTO

    // получить привилегию по id
    fun getPrivilegeById(privilegeId: UUID): PrivilegeResponseDTO

    // получить все привилегии по фильтру
    fun getHouseholdPrivileges(householdId: UUID, filter: PrivilegeFilterType): List<PrivilegeResponseDTO>
}
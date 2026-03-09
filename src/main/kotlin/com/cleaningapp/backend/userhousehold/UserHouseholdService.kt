package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.user.UserResponseDTO
import java.util.UUID

interface UserHouseholdService {

    // активность юзера меняется только здесь (отдельных методов нет)
    fun joinHousehold(inviteCode: String): UserHouseholdResponseDTO
    fun leaveHousehold(householdId: UUID)

    //удалить участника может НЕ только создатель
    fun removeUserFromHousehold(householdId: UUID, userToRemoveId: UUID)

    fun getUserHouseholds(): List<UserHouseholdResponseDTO>
    fun getHouseholdMembers(householdId: UUID): List<UserResponseDTO>

    // вызывается из TaskService/TransactionService (не публичный API)
    fun increaseBalance(householdId: UUID, amount: Int): UserHouseholdResponseDTO
    fun decreaseBalance(householdId: UUID, amount: Int): UserHouseholdResponseDTO

    // может кто то другой вызывать увеличение баланса (например при проверке выполнения)
    // тогда нужно передавать id того кому начислять
//    fun increaseBalance(userId: UUID, householdId: UUID, amount: Int): UserHouseholdResponseDTO
//    fun decreaseBalance(userId: UUID, householdId: UUID, amount: Int): UserHouseholdResponseDTO
}
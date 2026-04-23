package com.cleaningapp.backend.userhousehold

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserHouseholdRepository : JpaRepository<UserHouseholdEntity, UUID> {
    // найти связь по id юзера и хозяйства
    fun findByUserIdAndHouseholdId(userId: UUID, householdId: UUID): UserHouseholdEntity?
    fun findAllByHouseholdId(householdId: UUID): List<UserHouseholdEntity>

    // найти/посчитать активных юзеров хозяйства (активные связи)
    fun countByHouseholdIdAndIsUserActiveTrue(householdId: UUID): Int
    fun findAllByHouseholdIdAndIsUserActiveTrue(householdId: UUID): List<UserHouseholdEntity>

    // найти хозяйства в которых юзер активен (добавиь фильтрацию активности?)
    fun findAllByUserIdAndIsUserActiveTrue(userId: UUID): List<UserHouseholdEntity>

    // посчитать хозяйства в которых активен юзер (может быть максимум в трех хозяйствах)
    fun countByUserIdAndIsUserActiveTrue(userId: UUID): Int
}
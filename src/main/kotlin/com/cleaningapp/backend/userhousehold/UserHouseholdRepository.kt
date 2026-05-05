package com.cleaningapp.backend.userhousehold

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uh from UserHouseholdEntity uh where uh.id = :userHouseholdId")
    fun findByIdForUpdate(@Param("userHouseholdId") userHouseholdId: UUID): UserHouseholdEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select uh
    from UserHouseholdEntity uh
    where uh.user.id = :userId
      and uh.household.id = :householdId
""")
    fun findByUserIdAndHouseholdIdForUpdate(
        @Param("userId") userId: UUID,
        @Param("householdId") householdId: UUID,
    ): UserHouseholdEntity?

    // блокирует всех участников - для полного удаления
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
    select uh
    from UserHouseholdEntity uh
    where uh.household.id = :householdId
    order by uh.id
    """
    )    fun findAllByHouseholdIdForUpdate(
        @Param("householdId") householdId: UUID,
    ): List<UserHouseholdEntity>
}
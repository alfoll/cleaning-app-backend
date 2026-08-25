package com.cleaningapp.backend.taskplan

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TaskPlanRepository : JpaRepository<TaskPlanEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TaskPlanEntity p where p.id = :taskPlanId")
    fun findByIdForUpdate(@Param("taskPlanId") taskPlanId: UUID): TaskPlanEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from TaskPlanEntity p where p.household.id = :householdId")
    fun deleteAllByHouseholdId(@Param("householdId") householdId: UUID): Int
}

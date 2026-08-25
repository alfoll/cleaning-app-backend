package com.cleaningapp.backend.taskplan

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface TaskPlanRepository : JpaRepository<TaskPlanEntity, UUID> {
    @Query(
        """
        select p.id
        from TaskPlanEntity p
        where p.isActive = true
          and p.nextDueAt < :startOfTomorrow
          and not exists (
              select t.id
              from TaskEntity t
              where t.taskPlan = p
                and t.isCompleted = false
          )
        order by p.nextDueAt, p.id
        """
    )
    fun findReadyPlanIdsWithoutUnfinishedTask(
        @Param("startOfTomorrow") startOfTomorrow: LocalDateTime,
        pageable: Pageable,
    ): List<UUID>

    @Query("select p.household.id from TaskPlanEntity p where p.id = :taskPlanId")
    fun findHouseholdIdByTaskPlanId(@Param("taskPlanId") taskPlanId: UUID): UUID?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TaskPlanEntity p where p.id = :taskPlanId")
    fun findByIdForUpdate(@Param("taskPlanId") taskPlanId: UUID): TaskPlanEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from TaskPlanEntity p where p.household.id = :householdId")
    fun deleteAllByHouseholdId(@Param("householdId") householdId: UUID): Int
}

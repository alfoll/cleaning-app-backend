package com.cleaningapp.backend.tasktemplate

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TaskTemplateRepository : JpaRepository<TaskTemplateEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(
        """
        update TaskTemplateEntity t
        set t.isActive = false,
            t.version = t.version + 1
        where t.household.id = :householdId
          and t.createdBy.id = :createdById
          and t.isActive = true
        """
    )
    fun deactivateActiveTemplatesByHouseholdIdAndCreatedById(
        @Param("householdId") householdId: UUID,
        @Param("createdById") createdById: UUID,
    ): Int

    @Query(
        """
        select distinct t.household.id
        from TaskTemplateEntity t
        where t.createdBy.id = :createdById
          and t.isActive = true
        """
    )
    fun findActiveTemplateHouseholdIdsByCreatedById(
        @Param("createdById") createdById: UUID,
    ): List<UUID>

    @EntityGraph(attributePaths = ["household", "createdBy"])
    fun findAllByHouseholdIdAndIsActiveTrueOrderByCreatedAtDesc(
        householdId: UUID,
    ): List<TaskTemplateEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskTemplateEntity t where t.id = :templateId")
    fun findByIdForUpdate(@Param("templateId") templateId: UUID): TaskTemplateEntity?

    @Query("select t.household.id from TaskTemplateEntity t where t.id = :templateId")
    fun findHouseholdIdByTemplateId(@Param("templateId") templateId: UUID): UUID?

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from TaskTemplateEntity t where t.household.id = :householdId")
    fun deleteAllByHouseholdId(@Param("householdId") householdId: UUID): Int
}

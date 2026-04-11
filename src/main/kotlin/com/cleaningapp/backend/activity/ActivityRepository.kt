package com.cleaningapp.backend.activity

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ActivityRepository: JpaRepository<ActivityEntity, UUID> {

    // вся активность
    fun findAllByHouseholdIdOrderByCreatedAtDesc(householdId: UUID): List<ActivityEntity>

    // активность с фильтром по типу
    fun findAllByHouseholdIdAndActivityTypeOrderByCreatedAtDesc(householdId: UUID, activityType: ActivityType): List<ActivityEntity>

    // активность с фильтром по участнику
    fun findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(householdId: UUID, memberId: UUID): List<ActivityEntity>

    // активность с фильтром тип + участник - подумать, нужно ли вообще
    fun findAllByHouseholdIdAndActivityTypeAndMemberIdOrderByCreatedAtDesc(
        householdId: UUID,
        activityType: ActivityType,
        memberId: UUID,
    ): List<ActivityEntity>
}
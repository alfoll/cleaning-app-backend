package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "task_plan")
class TaskPlanEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    val id: UUID? = null,

    @Column(name = "title", nullable = false, length = 120)
    var title: String,

    @Column(name = "description", columnDefinition = "text", length = 2000)
    var description: String? = null,

    @Column(name = "reward", nullable = false)
    var reward: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false, length = 20)
    var recurrenceType: RecurrenceType,

    @Column(name = "next_due_at", nullable = false)
    var nextDueAt: LocalDateTime,

    @Column(name = "monthly_anchor_day")
    var monthlyAnchorDay: Int? = null,

    @Column(name = "monthly_last_day", nullable = false)
    var monthlyLastDay: Boolean = false,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    lateinit var household: HouseholdEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    lateinit var createdBy: UserEntity
}

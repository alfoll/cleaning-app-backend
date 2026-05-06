package com.cleaningapp.backend.task

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID
import jakarta.persistence.Version


@Entity
@Table(name = "task")
class TaskEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    val id: UUID? = null, // JPA сам генерит id при сохранении в бд

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "title", nullable = false, length = 120)
    var title: String,

    @Column(name = "description", columnDefinition = "text", length = 2000)
    var description: String? = null,

    // проверяю лимиты в дто
    @Column(name = "reward", nullable = false)
    var reward: Int,

    @Column(name = "assigned_at")
    var assignedAt: LocalDateTime? = null,

    @Column(name = "is_completed", nullable = false)
    var isCompleted: Boolean = false,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    var assignedTo: UserHouseholdEntity? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    var completedBy: UserHouseholdEntity? = null
}
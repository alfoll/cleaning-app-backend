package com.cleaningapp.backend.privilege

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "privilege",
    indexes = [
        Index(name = "idx_privilege_household_created_at", columnList = "household_id, created_at"),
        Index(name = "idx_privilege_household_available", columnList = "household_id, is_available"),
        Index(name = "idx_privilege_bought_by", columnList = "bought_by")
    ]
)
class PrivilegeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", unique = true, nullable = false, updatable = false)
    val id: UUID? = null, // JPA сам генерит id при сохранении в бд

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "title", nullable = false, length = 120)
    var title: String,

    @Column(name = "description", columnDefinition = "text", length = 2000)
    var description: String? = null,

    @Column(name = "cost", nullable = false)
    var cost: Int,

    @Column(name = "is_available", nullable = false)
    var isAvailable: Boolean = true, // может убрать из сущности и проверять как в Task через boughtBy == null?

    // добавить поле boughtAt?
) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    lateinit var household: HouseholdEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    lateinit var createdBy: UserEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bought_by", nullable = true)
    var boughtBy: UserHouseholdEntity? = null
}
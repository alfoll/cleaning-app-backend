package com.cleaningapp.backend.userhousehold

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "user_household",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "household_id"])])
class UserHouseholdEntity(
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    val id: UUID? = null, // JPA сам генерит id при сохранении в бд

    @Column(name = "balance", nullable = false)
    var balance: Int = 0,

    @Column(name = "joined_at", nullable = false, updatable = false)
    @CreationTimestamp
    val joinedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_user_active", nullable = false)
    var isUserActive: Boolean = true,
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: UserEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    lateinit var household: HouseholdEntity
}


package com.cleaningapp.backend.activity

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
    name = "activity",
    indexes = [
        Index(
            name = "idx_activity_household_created_at",
            columnList = "household_id, created_at"
        ),
        Index(
            name = "idx_activity_household_type_created_at",
            columnList = "household_id, activity_type, created_at"
        ),
        Index(
            name = "idx_activity_household_member_created_at",
            columnList = "household_id, member_id, created_at"
        ),
    ]
)
class ActivityEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    var id: UUID? = null, // JPA сам генерит id при сохранении в бд

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, updatable = false, length = 40)
    var activityType: ActivityType,

    @Column(name = "title", nullable = false, updatable = false, length = 180)
    var title: String,

    @Column(name = "description", columnDefinition = "text", updatable = false, length = 1000)
    var description: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    var createdAt: LocalDateTime = LocalDateTime.now()

) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false, updatable = false)
    lateinit var household: HouseholdEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    lateinit var member: UserHouseholdEntity

}
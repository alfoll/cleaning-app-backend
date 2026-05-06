package com.cleaningapp.backend.household

import com.cleaningapp.backend.user.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
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
@Table(name = "household")
class HouseholdEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    val id: UUID? = null, // JPA сам генерит id при сохранении в бд

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "invite_code", nullable = false, updatable = false)
    var inviteCode: String = "",

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,


    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,
) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    lateinit var createdByUser: UserEntity
}
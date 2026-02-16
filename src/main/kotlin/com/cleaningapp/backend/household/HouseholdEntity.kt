package com.cleaningapp.backend.household

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "household")
class HouseholdEntity(
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    val id: UUID? = null, // JPA сам генерит id при сохранении в бд

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "invite_code", nullable = false, unique = true, updatable = false)
    var inviteCode: String = "",

//    @Column(name = "max_members", nullable = false)
//    val maxMembers: Int = 6, // нахуй тут столбец где все 6? убрать в сервис

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by_user", nullable = false, updatable = false) // нужно ли?
    val createdByUser: UUID,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true, // false?
) {
    @PrePersist
    fun generateInviteCode() {
        val chars = ('A'..'Z') + ('0'..'9') + ('a' .. 'z')
        inviteCode = (1 .. 8) .map { chars.random() }.joinToString("")
    }
}
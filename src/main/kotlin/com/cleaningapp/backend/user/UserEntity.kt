package com.cleaningapp.backend.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "`user`",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_firebase_uid",
            columnNames = ["firebase_uid"]
        ),
        UniqueConstraint(
            name = "uk_user_email",
            columnNames = ["email"]
        )
    ]
)
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    var id: UUID? = null, // JPA сам генерит id при сохранении в бд

    @Column(name = "firebase_uid", nullable = false)
    val firebaseUid: String,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,


    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,
)
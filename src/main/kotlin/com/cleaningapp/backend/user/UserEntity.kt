package com.cleaningapp.backend.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "`user`")
class UserEntity(
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "firebase_uid", nullable = false, unique = true)
    val firebaseUid: String,

    @Column(name = "email", nullable = false, unique = true)
    var email: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "avatarUrl")
    var avatarUrl: String? = null,

    @Column(name = "createdAt", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
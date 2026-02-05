package com.cleaningapp.backend.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "`user`")
class UserEntity(
    @Column(name = "id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "email", nullable = false, unique = true)
    var email: String,

    @Column(name = "passwordHash", nullable = false)
    var passwordHash: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "avatarUrl")
    var avatarUrl: String? = null,

    @Column(name = "createdAt", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {
}
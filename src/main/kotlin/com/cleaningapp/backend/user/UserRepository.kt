package com.cleaningapp.backend.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository: JpaRepository<UserEntity, UUID> {
    fun findUserByFirebaseUid(firebaseUid: String): UserEntity?
    fun findUserByEmail(email: String): UserEntity?
}
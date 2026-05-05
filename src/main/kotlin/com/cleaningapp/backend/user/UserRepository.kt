package com.cleaningapp.backend.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository: JpaRepository<UserEntity, UUID> {
    fun findUserByFirebaseUid(firebaseUid: String): UserEntity?
    fun findUserByEmail(email: String): UserEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :userId")
    fun findByIdForUpdate(@Param("userId") userId: UUID): UserEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.firebaseUid = :firebaseUid")
    fun findUserByFirebaseUidForUpdate(@Param("firebaseUid") firebaseUid: String): UserEntity?
}
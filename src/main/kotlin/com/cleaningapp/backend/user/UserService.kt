package com.cleaningapp.backend.user

import java.util.UUID

interface UserService {
    fun createUser(firebaseUid: String, user: UserRegisterDTO): UserResponseDTO
    fun deleteUser(firebaseUid: String)

    fun updateProfile(firebaseUid: String, userNew: UserRegisterDTO): UserResponseDTO

    // в AuthService
    fun changePassword(firebaseUid: String, newPassword: String): UserResponseDTO

    fun findUserById(id: UUID): UserResponseDTO
    fun findUserByEmail(email: String): UserResponseDTO
    fun findUserByFirebaseUid(firebaseUid: String): UserResponseDTO
}
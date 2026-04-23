package com.cleaningapp.backend.user

import java.util.UUID

interface UserService {
    fun createUser(firebaseUid: String, user: UserRegisterDTO): UserResponseDTO
    fun deleteUser(firebaseUid: String)

    // обновление не включает смену почты
    fun updateProfile(firebaseUid: String, userNew: UserUpdateDTO): UserResponseDTO

    // мена почты - отдельный сценарий, происходит через fb
    // синхронизация email из firebase с локальной бд (после смены email на fb)
    fun syncEmailFromFirebase(firebaseUid: String): UserResponseDTO

    fun findUserById(id: UUID): UserResponseDTO
    fun findUserByEmail(email: String): UserResponseDTO
    fun findUserByFirebaseUid(firebaseUid: String): UserResponseDTO
}
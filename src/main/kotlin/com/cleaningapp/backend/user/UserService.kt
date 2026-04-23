package com.cleaningapp.backend.user


interface UserService {
    fun createUser(firebaseUid: String, user: UserRegisterDTO): UserResponseDTO
    fun deleteUser()

    // обновление не включает смену почты
    fun updateProfile(userNew: UserUpdateDTO): UserResponseDTO
    fun getProfile(): UserResponseDTO

    // мена почты - отдельный сценарий, происходит через fb
    // синхронизация email из firebase с локальной бд (после смены email на fb)
    fun syncEmailFromFirebase(): UserResponseDTO

}
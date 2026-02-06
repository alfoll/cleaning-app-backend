package com.cleaningapp.backend.user

import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService {

    override fun createUser(firebaseUid: String, user: UserRegisterDTO): UserResponseDTO {

        if (userRepository.findUserByFirebaseUid(firebaseUid) != null) {
            throw RuntimeException("Account already exists")
        }

        if (userRepository.findUserByEmail(user.email) != null) {
            throw RuntimeException("User with this email already exists")
        }

        val userEntity = user.toUserEntity(firebaseUid)
        return userRepository.save(userEntity).toDTO()
    }
    // ОПАСНО, подумать
    override fun deleteUser(firebaseUid: String) {
        val user = userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw RuntimeException("User does not exist")

        return userRepository.deleteById(user.id)
    }

    override fun updateProfile(firebaseUid: String, userNew: UserRegisterDTO): UserResponseDTO {
        val existingUser = userRepository.findUserByFirebaseUid(firebaseUid)
        // есть ли вообще юзер
        if (existingUser != null) {
            // меняется ли почта
            if (existingUser.email != userNew.email) {
                val userWithEmail = userRepository.findUserByEmail(userNew.email)
                // если такая почта уже существует - ее нельзя поставить
                if (userWithEmail != null && userWithEmail.id != existingUser.id) {
                    throw RuntimeException("This email already exists")
                }
            }
            // меняем остальное
            existingUser.name = userNew.name
            existingUser.email = userNew.email
            existingUser.avatarUrl = userNew.avatarUrl
        } else throw RuntimeException("User does not exist")

        return userRepository.save(existingUser).toDTO()
    }

    // в AuthService
    override fun changePassword(firebaseUid: String, newPassword: String): UserResponseDTO {
        TODO("Not yet implemented") // делается чезе firebase
    }

    override fun findUserById(id: UUID): UserResponseDTO =
        userRepository.findByIdOrNull(id)?.toDTO()
            ?: throw RuntimeException("User not found")

    override fun findUserByEmail(email: String): UserResponseDTO =
        userRepository.findUserByEmail(email)?.toDTO()
            ?: throw RuntimeException("User not found")

    override fun findUserByFirebaseUid(firebaseUid: String): UserResponseDTO =
        userRepository.findUserByFirebaseUid(firebaseUid)?.toDTO()
            ?: throw RuntimeException("User not found")
}
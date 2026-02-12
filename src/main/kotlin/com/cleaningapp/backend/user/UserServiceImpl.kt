package com.cleaningapp.backend.user

import com.cleaningapp.backend.exception.EmailAlreadyUserException
import com.cleaningapp.backend.exception.UserAlreadyExistsException
import com.cleaningapp.backend.exception.UserNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService {

    override fun createUser(firebaseUid: String, user: UserRegisterDTO): UserResponseDTO {

        // проверка что такого аккаунта не существует
        if (userRepository.findUserByFirebaseUid(firebaseUid) != null) {
            throw UserAlreadyExistsException("Account already exists")
        }

        // проверка что почта не занята
        if (userRepository.findUserByEmail(user.email) != null) {
            throw EmailAlreadyUserException("This email is already in use")
        }

        // сохранение юзера в бд
        val userEntity = user.toUserEntity(firebaseUid)
        return userRepository.save(userEntity).toDTO()
    }

    // ОПАСНО, подумать
    override fun deleteUser(firebaseUid: String) {
        // проверка что юзер существует
        val user = userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw UserNotFoundException("User does not exist")

        // провера id на null на всякий случай
        val userId = user.id
            ?: throw IllegalStateException("User ID is null. Entity is not persisted.")

        return userRepository.deleteById(userId)
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
                    throw EmailAlreadyUserException("This email is already in use")
                }
            }
            // меняем остальное
            existingUser.name = userNew.name
            existingUser.email = userNew.email
            existingUser.avatarUrl = userNew.avatarUrl
        } else
            throw UserNotFoundException("User does not exist")

        return userRepository.save(existingUser).toDTO()
    }

    // в AuthService
    override fun changePassword(firebaseUid: String, newPassword: String): UserResponseDTO {
        TODO("Not yet implemented") // делается чезе firebase
    }

    override fun findUserById(id: UUID): UserResponseDTO =
        userRepository.findByIdOrNull(id)?.toDTO()
            ?: throw UserNotFoundException("User not found")

    override fun findUserByEmail(email: String): UserResponseDTO =
        userRepository.findUserByEmail(email)?.toDTO()
            ?: throw UserNotFoundException("User with this email not found")

    override fun findUserByFirebaseUid(firebaseUid: String): UserResponseDTO =
        userRepository.findUserByFirebaseUid(firebaseUid)?.toDTO()
            ?: throw UserNotFoundException("User with this uid not found")
}
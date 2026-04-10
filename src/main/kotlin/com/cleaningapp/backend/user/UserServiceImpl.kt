package com.cleaningapp.backend.user

import com.cleaningapp.backend.exception.EmailAlreadyUsedException
import com.cleaningapp.backend.exception.UserAlreadyExistsException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.transaction.BalanceResetTransactionCommand
import com.cleaningapp.backend.transaction.TransactionService
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val userHouseholdRepository: UserHouseholdRepository,

    private val taskService: TaskService,
    private val transactionService: TransactionService,
) : UserService {

    override fun createUser(firebaseUid: String, user: UserRegisterDTO): UserResponseDTO {

        // проверка что такого аккаунта не существует
        if (userRepository.findUserByFirebaseUid(firebaseUid) != null) {
            throw UserAlreadyExistsException()
        }

        // проверка что почта не занята
        if (userRepository.findUserByEmail(user.email) != null) {
            throw EmailAlreadyUsedException()
        }

        // сохранение юзера в бд
        val userEntity = user.toUserEntity(firebaseUid)
        return userRepository.save(userEntity).toDTO()
    }

    // еще подумать, но вроде норм (пока без задач/привилегий/транзакций/активности)
    override fun deleteUser(firebaseUid: String) {
        // проверка что юзер существует
        val user = userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw UserNotFoundException()

        // провера id на null на всякий случай
        val userId = user.id
            ?: throw IllegalStateException("User ID is null. Entity is not persisted.")

        // проверка активен ли юзер вообще
        if (!user.isActive)
            throw UserNotActiveException()

        // найти все связи с хозяйствами -> везде деактивировать и обнулить баланс
        val userHouseholds = userHouseholdRepository.findAllByUserIdAndIsUserActiveTrue(userId)

        for (userHousehold in userHouseholds) {
            //  юзер активен в каком то хозяйстве (на уровне репозитория метод) (проверка отсюда убрана)

            // освободить забронированные задачи
            taskService.releaseAssignedTasks(userHousehold.id!!)

            // обнуляем баланс
            transactionService.resetBalance(
                BalanceResetTransactionCommand(
                    householdId = userHousehold.household.id!!,
                    memberId = userHousehold.id!!,
                )
            )

            // деактивируем пользователя
            userHousehold.isUserActive = false

            // сохраняем изменения - транзакционный сервис, сохранять не обязательно
//            userHouseholdRepository.save(userHousehold) // managed entity

            // проверяем остались ли еще активные участники в хозяйстве
            val household = userHousehold.household

            val activeMembers = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)

            // если нет больше активных участников то нужно деактивировать хозяйство
            if (activeMembers == 0) {
                household.isActive = false
//                householdRepository.save(household) // managed entity
            }

        }

        // деактивировать самого юзера и сохранить изменения
        user.isActive = false
//        userRepository.save(user) // managed entity
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
                    throw EmailAlreadyUsedException()
                }
            }
            // меняем остальное
            existingUser.name = userNew.name
            existingUser.email = userNew.email
            existingUser.avatarUrl = userNew.avatarUrl
        } else
            throw UserNotFoundException()

//        return userRepository.save(existingUser).toDTO() // managed entity
        return existingUser.toDTO()
    }

    override fun findUserById(id: UUID): UserResponseDTO =
        userRepository.findByIdOrNull(id)?.toDTO()
            ?: throw UserNotFoundException()

    override fun findUserByEmail(email: String): UserResponseDTO =
        userRepository.findUserByEmail(email)?.toDTO()
            ?: throw UserNotFoundException()

    override fun findUserByFirebaseUid(firebaseUid: String): UserResponseDTO =
        userRepository.findUserByFirebaseUid(firebaseUid)?.toDTO()
            ?: throw UserNotFoundException()
}
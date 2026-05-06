package com.cleaningapp.backend.user

import com.cleaningapp.backend.activity.ActivityService
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.activity.RecordActivityCommand
import com.cleaningapp.backend.exception.EmailAlreadyUsedException
import com.cleaningapp.backend.exception.UserAlreadyExistsException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.household.HouseholdService
import com.cleaningapp.backend.security.FirebaseAuthService
import com.cleaningapp.backend.task.TaskService
import com.cleaningapp.backend.transaction.BalanceResetTransactionCommand
import com.cleaningapp.backend.transaction.TransactionService
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
    private val householdRepository: HouseholdRepository,

    private val householdService: HouseholdService,
    private val taskService: TaskService,
    private val transactionService: TransactionService,
    private val activityService: ActivityService,
    private val firebaseAuthService: FirebaseAuthService,
) : UserService {

    // достать юзера из контекста - read сценарий
    private fun getCurrentUser(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("User not authenticated")

        val firebaseUid = auth.name

        val user = userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw UserNotFoundException()

        if (!user.isActive)
            throw UserNotActiveException()
        return user
    }
    // блокировка для write сценариев
    private fun getCurrentUserForUpdate(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("User not authenticated")

        val firebaseUid = auth.name

        val user = userRepository.findUserByFirebaseUidForUpdate(firebaseUid)
            ?: throw UserNotFoundException()

        if (!user.isActive)
            throw UserNotActiveException()
        return user
    }


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

    // не является глобальным mutex для всех действий пользователя
    override fun deleteUser() {
        // достать активного юзера - с блокировкой
        val user = getCurrentUserForUpdate()

        // найти все хозяйства -> везде деактивировать и обнулить баланс
        // отсортированный порядок
        val householdIds = userHouseholdRepository
            .findAllByUserIdAndIsUserActiveTrue(user.id!!)
            .map { it.household.id!! }
            .distinct()
            .sorted()

        for (householdId in householdIds) {
            // если это последний пользователь КАКОГО-ЛИБО ИЗ СВОИХ ХОЗЯЙСТВ - удаляем хозяйство
            // хозяйства + участия с блокировкой
            val household = householdRepository.findByIdForUpdate(householdId)
                ?: continue

            val lockedMembership = userHouseholdRepository.findByUserIdAndHouseholdIdForUpdate(user.id!!, householdId)
                ?: continue
            if (!lockedMembership.isUserActive)
                continue

            if (!household.isActive) {
                lockedMembership.isUserActive = false
                lockedMembership.balance = 0
                continue
            }

            val activeMembers = userHouseholdRepository.countByHouseholdIdAndIsUserActiveTrue(household.id!!)

            if (activeMembers == 1) {
                householdService.deleteHouseholdFromSystem(household.id!!)
                continue
            }

            // если это не последний пользователь - обычный сценарий
            // освободить забронированные задачи
            val releasedTaskAmount = taskService.releaseAssignedTasks(lockedMembership.id!!)

            // обнуляем баланс
            transactionService.resetBalance(
                BalanceResetTransactionCommand(
                    householdId = householdId,
                    memberId = lockedMembership.id!!,
                )
            )

            // для каждого хозяйства запись в ленте активности USER_LEFT
            val description = if (releasedTaskAmount > 0) {
                "${user.name} left household \"${household.name}\". " +
                        "$releasedTaskAmount tasks were released"
            } else {
                "${user.name} left household \"${household.name}\""
            }
            activityService.createActivityRecord(
                RecordActivityCommand(
                    householdId = householdId,
                    memberId = lockedMembership.id!!,
                    activityType = ActivityType.USER_LEFT,
                    title = "User left",
                    description = description
                )
            )

            // деактивируем пользователя
            lockedMembership.isUserActive = false

            // сохраняем изменения - транзакционный сервис, сохранять не обязательно
//            userHouseholdRepository.save(userHousehold) // managed entity
        }

        // деактивировать самого юзера и сохранить изменения
        user.isActive = false
//        userRepository.save(user) // managed entity
    }

    override fun getProfile(): UserResponseDTO {
        val user = getCurrentUser()
        return user.toDTO()
    }

    // update профиля не включает смену почты - она через fb отдельным сценарием
    override fun updateProfile(userNew: UserUpdateDTO): UserResponseDTO {
        // найти юзера
        val existingUser = getCurrentUser()

        // меняем поля (имя + аватар - без почты)
        existingUser.name = userNew.name
        existingUser.avatarUrl = userNew.avatarUrl

//        return userRepository.save(existingUser).toDTO() // managed entity
        return existingUser.toDTO()
    }

    // отдельный сценарий смены почты через fb подразумевает синхронизацию с локальной бд
    override fun syncEmailFromFirebase(): UserResponseDTO {
        // найти существующего юзера (firebaseUid из уже валидированного токена)
        val existingUser = getCurrentUser()

        // достать пользователя из FB по uid
        val firebaseUser = firebaseAuthService.getUserByUid(existingUser.firebaseUid)

        // взять почту (FB)
        val firebaseEmail = firebaseUser.email
            ?: throw IllegalStateException("Firebase user has no email")

        // если почта действительно изменена (не совпадает со старой) - если совпадает то просто возвращаем того же юзера
        if (existingUser.email != firebaseEmail) {
            // проверить не занята ли уже новая почта кем то ДРУГИМ
            val sameEmail = userRepository.findUserByEmail(firebaseEmail)
            if (sameEmail != null && sameEmail.id != existingUser.id)
                throw EmailAlreadyUsedException()

            // сменить почту в локальной бд
            existingUser.email = firebaseEmail
        }

        return existingUser.toDTO() // managed entity
    }
}
package com.cleaningapp.backend.task

import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.TaskNotFoundException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

enum class TaskFilterType {
    ALL, // все
    FREE, // вске не выполненные и не забронированные
    MY, // все не выполненные и забронированные за юзером
    COMPLETED, // все выполненные
}

@Service
@Transactional
class TaskServiceImpl(
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val householdRepository: HouseholdRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
): TaskService {

    // достать юзера из контекста
    private fun getCurrentUser(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("User not authenticated")

        val firebaseUid = auth.name

        return userRepository.findUserByFirebaseUid(firebaseUid)
            ?: throw UserNotFoundException()
    }

    // достать активное хозяйсто
    private fun getActiveHousehold(householdId: UUID): HouseholdEntity {
        // существует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }

    // досать активную связь
    private fun getActiveMembership(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(userId, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return userHousehold
    }

    // достать сущность задачи
    private fun getTaskEntity(taskId: UUID): TaskEntity =
        taskRepository.findByIdOrNull(taskId)
            ?: throw TaskNotFoundException()

    // проверить что пользователь состоит в хозяйстве в котором хочет взять задачу
    private fun validateTaskAccess(task: TaskEntity, currentUser: UserEntity): UserHouseholdEntity {
        val household = task.household

        if (!household.isActive)
            throw HouseholdNotActiveException()

        return getActiveMembership(currentUser.id!!, household.id!!)
    }

    // создать задачу может любой активный участник хозяйсвта
    override fun createTask(householdId: UUID, task: TaskRegisterDTO): TaskResponseDTO {
        // достать юзера и активное хозяйства
        val user = getCurrentUser()
        val household = getActiveHousehold(householdId)

        // проверить состоит ли юзер в хозяйстве и активен ли в нем
        getActiveMembership(user.id!!, household.id!!)

        // если такая задача уже есть - похуй может быть и вторая - сохраняем
        return taskRepository.save(task.toTaskEntity(user, household)).toDto()
    }

    // нельзя менять условия задачи если она взята в работу (забронена) или выполнена
    override fun updateTask(taskId: UUID, newTask: TaskRegisterDTO): TaskResponseDTO {
        // достать юзера - поему не достаем активное хозяйство?
        // потому что в валидации доступа берется уже существующее хозяйтство задачи проверяется его активность
        // и проверяется активная связь - все вместе
        val user = getCurrentUser()

        // достать задачу
        val task = getTaskEntity(taskId)

        // как раз проверка наличия активного хозяйства + активного участия
        validateTaskAccess(task, user)

        // если задача выполнена - нельзя менять
        if (task.isCompleted)
            throw BusinessConflictException("Completed task cannot be updated")

        // если задача забронирована - нельзя менять
        if (task.assignedTo != null)
            throw BusinessConflictException("Assigned task cannot be updated")

        // обновляем (название/описание/награду) и сохранем
        task.title = newTask.title
        task.description = newTask.description
        task.reward = newTask.reward

        return taskRepository.save(task).toDto()
    }

    // массовая сущность - если свободна то можно жестко удалить
    override fun deleteTask(taskId: UUID) {
        // достать юзера
        val user = getCurrentUser()

        // достать задачу
        val task = getTaskEntity(taskId)

        // валидировать - хозяйство задачи активно + юзер в нем активен
        validateTaskAccess(task, user)

        // нельзя удалять выполненную задачу
        if (task.isCompleted)
            throw BusinessConflictException("Completed task cannot be deleted")

        // нельзя удалять забронированную задачу
        if (task.assignedTo != null)
            throw BusinessConflictException("Assigned task cannot be deleted")

        taskRepository.delete(task) // как реализовать удаление - жестко или как с юзерами и хозяйствами (мягко)?
    }

    // бронирует задачу текущему пользователю - мне
    override fun assignTask(taskId: UUID): TaskResponseDTO {
        // достать юзера
        val user = getCurrentUser()

        // достать задачу
        val task = getTaskEntity(taskId)

        // валидировать задачу (активность хозяйства задачи + участия юзера в хозяйстве) + достать связь
        val membership = validateTaskAccess(task, user)

        // нельзя забронировать выполненную задачу
        if (task.isCompleted)
            throw BusinessConflictException("Completed task cannot be assigned")

        // нельзя забронировать уже забронированную задачу
        if (task.assignedTo != null)
            throw BusinessConflictException("Assigned task cannot be assigned")

        // бронируемза собой
        task.assignedTo = membership
        task.assignedAt = LocalDateTime.now()

        return taskRepository.save(task).toDto()
    }

    // снять бронь с задачи может только тот кто ее забронировал
    override fun unassignTask(taskId: UUID): TaskResponseDTO {
        // достать юзера
        val user = getCurrentUser()

        // достать задачу
        val task = getTaskEntity(taskId)

        // валидировать (хозяйство задачи активно + юзер в нем состоит и активен) - возвращается связь
        val membership = validateTaskAccess(task, user)

        // нельзя освободить выполненную задачу
        if (task.isCompleted)
            throw BusinessConflictException("Completed task cannot be unassigned")

        // нельзя освободить незабронированную задачу
        if (task.assignedTo == null)
            throw BusinessConflictException("Unassigned task cannot be unassigned")

        // нельзя освободить НЕ СВОЮ задачу (задача прикреплена к участию а не к юзеру)
        if (task.assignedTo?.id != membership.id)
            throw BusinessConflictException("Only assigned user can unassign this task")

        // освобождаем + сохраняем
        task.assignedTo = null
        task.assignedAt = null

        return taskRepository.save(task).toDto()
    }

    // задача должна быть забронирована чтобы ее завершить
    // после завершения начисляется награда
    // НАЧИСЛЕНИЕ ВЫНЕСТИ В СЕРВИС ТРАНЗАКЦИЙ
    override fun completeTask(taskId: UUID): TaskResponseDTO {
        // достать юзера
        val user = getCurrentUser()

        // достать задачу
        val task = getTaskEntity(taskId)

        // валидировать (хозяйство задачи активно + юзер в нем состоит и активен) + достать связь
        val membership = validateTaskAccess(task, user)

        // нельзя завершить уже завершенную задачу
        if (task.isCompleted)
            throw BusinessConflictException("Completed task cannot be completed")

        // нельзя завершить НЕзабронированную задачу
        if (task.assignedTo == null)
            throw BusinessConflictException("Unassigned task cannot be completed")

        // нельзя завешить задачу если ее бронировал кто то другой
        if (task.assignedTo?.id != membership.id)
            throw BusinessConflictException("Only assigned user can complete this task")

        // завершаем задачу
        task.isCompleted = true
        task.completedBy = membership
        task.completedAt = LocalDateTime.now()

        // бронь сбрасываем, она больше не нужна, в истории сохранится
        task.assignedTo = null
        task.assignedAt = null

        // начисление капусты на баланс - через репозиторий
        // (далее в сервисе UserHouseholdService не будет методов изменения баланса) - задача транзакций
        membership.balance += task.reward
        userHouseholdRepository.save(membership)

        return taskRepository.save(task).toDto()
    }

    @Transactional(readOnly = true)
    override fun getTaskById(taskId: UUID): TaskResponseDTO {
        // достать юзера
        val user = getCurrentUser()

        // достать задачу
        val task = getTaskEntity(taskId)

        // валидировать задачу - хозяйство задачи активно + участие активно
        validateTaskAccess(task, user)

        return task.toDto()
    }

    // через фильтр а не отдельными методами
    // все/выполненные/забронированные другими/свободные/мои/мои-забронированные/мои-выполненные - мб сделать такой набор
    // на данном этапе - все/свободные/выполненные/мои (забронированные мной)
    @Transactional(readOnly = true)
    override fun getHouseholdTasks(householdId: UUID, filter: TaskFilterType): List<TaskResponseDTO> {
        // достать юзера
        val user = getCurrentUser()

        // достать активное хозяйство
        val household = getActiveHousehold(householdId)

        // достать активное участие
        val membership = getActiveMembership(user.id!!, household.id!!)

        // сформировать список задач по запросу фильтра
        val tasks = when (filter) {
            TaskFilterType.ALL -> // все
                taskRepository.findAllByHouseholdIdOrderByCreatedAtDesc(household.id!!)

            TaskFilterType.FREE -> // все не заброненные и не выполненные
                taskRepository.findAllByHouseholdIdAndAssignedToIsNullAndIsCompletedFalseOrderByCreatedAtDesc(household.id!!)

            TaskFilterType.MY -> // мои заброненные - через участие а не через юзера
                taskRepository.findAllByHouseholdIdAndAssignedToIdAndIsCompletedFalseOrderByAssignedAtDesc(
                    household.id!!,
                    membership.id!!
                )

            TaskFilterType.COMPLETED -> // все выполненные
                taskRepository.findAllByHouseholdIdAndIsCompletedTrueOrderByCompletedAtDesc(household.id!!)
        }

        return tasks.map { it.toDto() }
    }

    // для UserHouseholdService - при удалении/выходе пользователя - через участие
    // для UserService - приудалении пользователя из системы - через участие
    override fun releaseAssignedTasks(userHouseholdId: UUID) {
        // достали все незавершенные задачи участника
        val tasks = taskRepository.findAllByAssignedToIdAndIsCompletedFalse(userHouseholdId)

        // освободить
        tasks.forEach { task ->
            task.assignedTo = null
            task.assignedAt = null
        }

        // не возвращаем, просто сохраняем
        taskRepository.saveAll(tasks)
    }
}
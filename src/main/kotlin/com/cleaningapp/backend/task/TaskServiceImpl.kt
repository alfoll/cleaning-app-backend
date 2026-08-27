package com.cleaningapp.backend.task

import com.cleaningapp.backend.activity.ActivityService
import com.cleaningapp.backend.activity.ActivityType
import com.cleaningapp.backend.activity.RecordActivityCommand
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.TaskNotFoundException
import com.cleaningapp.backend.exception.TaskPlanNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.taskplan.TaskPlanEntity
import com.cleaningapp.backend.taskplan.TaskPlanInstanceService
import com.cleaningapp.backend.taskplan.TaskPlanRecurrenceCalculator
import com.cleaningapp.backend.taskplan.TaskPlanRepository
import com.cleaningapp.backend.transaction.TaskCompletionTransactionCommand
import com.cleaningapp.backend.transaction.TransactionService
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

enum class TaskFilterType {
    ALL, // все
    FREE, // вске не выполненные и не забронированные
    MY, // все не выполненные и забронированные за юзером
    COMPLETED, // все выполненные
    WITH_DEADLINE, // все невыполненные со сроком
    OVERDUE, // все невыполненные с истекшим сроком
}

@Service
@Transactional
class TaskServiceImpl(
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val householdRepository: HouseholdRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
    private val taskPlanRepository: TaskPlanRepository,

    private val transactionService: TransactionService,
    private val activityService: ActivityService,
    private val taskPlanInstanceService: TaskPlanInstanceService,
    private val dueAtNormalizer: TaskDueAtNormalizer,
    private val recurrenceCalculator: TaskPlanRecurrenceCalculator,
    private val clock: Clock,
): TaskService {

    // лимиты на выдачу фронту (ALL без лимита)
    private companion object {
        const val COMPLETED_TASK_LIMIT = 150
        const val MY_TASK_LIMIT = 150
        const val FREE_TASK_LIMIT = 150
        const val WITH_DEADLINE_TASK_LIMIT = 150
        const val OVERDUE_TASK_LIMIT = 150
    }

    // достать юзера из контекста
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

    // достать активное хозяйсто - для read сценариев
    private fun getActiveHousehold(householdId: UUID): HouseholdEntity {
        // существует ли хозяйство
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }
    // блокировки для write сценариев
    private fun getActiveHouseholdForUpdate(householdId: UUID): HouseholdEntity {
        // существует ли хозяйство
        val household = householdRepository.findByIdForUpdate(householdId)
            ?: throw HouseholdNotFoundException()

        // активно ли хозяйство
        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }

    // досать активную связь - для read сценариев
    private fun getActiveMembership(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdId(userId, householdId)
            ?: throw MembershipNotFoundException()

        // активен ли юзер в этом хозяйстве
        if (!userHousehold.isUserActive)
            throw MembershipNotActiveException()

        return userHousehold
    }
    // блокировки для write сценариев
    private fun getActiveMembershipForUpdate(userId: UUID, householdId: UUID): UserHouseholdEntity {
        // найти связь (проверить есть ли она)
        val userHousehold = userHouseholdRepository.findByUserIdAndHouseholdIdForUpdate(userId, householdId)
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
    // с блкировкой на write сценарии
    private fun getTaskEntityForUpdate(taskId: UUID): TaskEntity =
        taskRepository.findByIdForUpdate(taskId)
            ?: throw TaskNotFoundException()

    // проверить что пользователь состоит в хозяйстве в котором хочет взять задачу
    // только для read сценариев - для поддержания порядка блокировки
    private fun validateTaskAccess(task: TaskEntity, currentUser: UserEntity): UserHouseholdEntity {
        val household = task.household

        if (!household.isActive)
            throw HouseholdNotActiveException()

        return getActiveMembership(currentUser.id!!, household.id!!)
    }


    // создать задачу может любой активный участник хозяйсвта
    override fun createTask(householdId: UUID, task: TaskCreateDTO): TaskResponseDTO {
        // достать юзера + активное хозяйство + участие
        val user = getCurrentUser()

        val household = getActiveHouseholdForUpdate(householdId)
        val membership = getActiveMembershipForUpdate(user.id!!, household.id!!)

        val normalizedDueAt = dueAtNormalizer.normalize(task.dueAt)
        if (task.recurrenceType != null && normalizedDueAt == null)
            throw BusinessConflictException("Recurring task requires a due date")

        val taskPlan = task.recurrenceType?.let { recurrenceType ->
            val schedule = recurrenceCalculator.createSchedule(normalizedDueAt!!, recurrenceType)
            taskPlanRepository.saveAndFlush(
                TaskPlanEntity(
                    title = task.title,
                    description = task.description,
                    reward = task.reward,
                    recurrenceType = recurrenceType,
                    nextDueAt = schedule.nextDueAt,
                    monthlyAnchorDay = schedule.monthlyAnchorDay,
                    monthlyLastDay = schedule.monthlyLastDay,
                ).apply {
                    this.household = household
                    this.createdBy = user
                }
            )
        }

        val savedTask = if (taskPlan == null) {
            taskRepository.save(task.copy(dueAt = normalizedDueAt).toTaskEntity(user, household))
        } else {
            taskPlanInstanceService.createTaskInstance(taskPlan.id!!, normalizedDueAt!!)
        }

        // запись TASK_CREATED в ленту активности
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = household.id!!,
                memberId = membership.id!!,
                activityType = ActivityType.TASK_CREATED,
                title = "Task created",
                description = "${user.name} created task \"${savedTask.title}\""
            )
        )

        return savedTask.toDto(LocalDateTime.now(clock)) // сохранение оставить - новая сущность
    }

    // нельзя менять условия задачи если она взята в работу (забронена) или выполнена
    // только создатель может менять задачу
    override fun updateTask(taskId: UUID, newTask: TaskUpdateDTO): TaskResponseDTO {
        // юзер + хозяйство и участие из задачи
        val user = getCurrentUser()

        val householdId = taskRepository.findHouseholdIdByTaskId(taskId)
            ?: throw TaskNotFoundException()

        val household = getActiveHouseholdForUpdate(householdId)
        getActiveMembershipForUpdate(user.id!!, householdId)

        // достать задачу
        val task = getTaskEntityForUpdate(taskId)

        if (task.household.id != household.id)
            throw BusinessConflictException("Task does not belong to this household")

        // если задача выполнена - нельзя менять
        if (task.isCompleted)
            throw BusinessConflictException("Completed task cannot be updated")

        // если задача забронирована - нельзя менять
        if (task.assignedTo != null)
            throw BusinessConflictException("Assigned task cannot be updated")

        // если не создатель - нельзя менять
        if (task.createdBy.id != user.id)
            throw BusinessConflictException("Only creator can update task")

        val updatedDueAt = resolveUpdatedDueAt(task, newTask.dueAt)

        // Изменения экземпляра повторяющейся задачи не изменяют параметры плана.
        task.title = newTask.title
        task.description = newTask.description
        task.reward = newTask.reward
        if (task.taskPlan == null)
            task.dueAt = updatedDueAt

//        return taskRepository.save(task).toDto() // managed entity
        return task.toDto(LocalDateTime.now(clock))
    }

    private fun resolveUpdatedDueAt(
        task: TaskEntity,
        requestedDueAt: LocalDateTime?,
    ): LocalDateTime? {
        val existingDueAt = task.dueAt
        val isSameCalendarDate = requestedDueAt != null &&
            requestedDueAt.toLocalDate() == existingDueAt?.toLocalDate()

        if (task.taskPlan != null) {
            if (requestedDueAt == null || isSameCalendarDate)
                return existingDueAt

            throw BusinessConflictException("Recurring task due date cannot be changed")
        }

        if (requestedDueAt == null)
            return null
        if (isSameCalendarDate)
            return existingDueAt

        return dueAtNormalizer.normalize(requestedDueAt)
    }

    // массовая сущность - если свободна то можно жестко удалить
    // только создатель может удалять задачу
    override fun deleteTask(taskId: UUID) {
        // юзер + хозяйство и участие из задачи
        val user = getCurrentUser()

        val householdId = taskRepository.findHouseholdIdByTaskId(taskId)
            ?: throw TaskNotFoundException()
        val taskPlanId = taskRepository.findTaskPlanIdByTaskId(taskId)

        val household = getActiveHouseholdForUpdate(householdId)
        getActiveMembershipForUpdate(user.id!!, household.id!!)

        // Как и в B5/B6, TaskPlan блокируется до Task.
        val taskPlan = taskPlanId?.let { id ->
            taskPlanRepository.findByIdForUpdate(id)
                ?: throw TaskPlanNotFoundException()
        }

        // достать задачу
        val task = getTaskEntityForUpdate(taskId)

        if (task.household.id != household.id)
            throw BusinessConflictException("Task does not belong to this household")

        // нельзя удалять выполненную задачу
        if (task.isCompleted)
            throw BusinessConflictException("Completed task cannot be deleted")

        // нельзя удалять забронированную задачу
        if (task.assignedTo != null)
            throw BusinessConflictException("Assigned task cannot be deleted")

        // если не создатель - нельзя удалить
        if (task.createdBy.id != user.id)
            throw BusinessConflictException("Only creator can delete task")

        if (taskPlan?.isActive == true)
            taskPlan.isActive = false

        taskRepository.delete(task)
    }

    // бронирует задачу текущему пользователю - мне
    override fun assignTask(taskId: UUID): TaskResponseDTO {
        // юзер + хозяйство и участие из задачи
        val user = getCurrentUser()

        val householdId = taskRepository.findHouseholdIdByTaskId(taskId)
            ?: throw TaskNotFoundException()

        val household = getActiveHouseholdForUpdate(householdId)
        val membership = getActiveMembershipForUpdate(user.id!!, household.id!!)

        // достать задачу
        val task = getTaskEntityForUpdate(taskId)

        if (task.household.id != household.id)
            throw BusinessConflictException("Task does not belong to this household")

        // нельзя забронировать выполненную задачу
        if (task.isCompleted)
            throw BusinessConflictException("Completed task cannot be assigned")

        // нельзя забронировать уже забронированную задачу
        if (task.assignedTo != null)
            throw BusinessConflictException("Assigned task cannot be assigned")

        // бронируем за собой
        task.assignedTo = membership
        task.assignedAt = LocalDateTime.now(clock)

        // создаем запись TASK_ASSIGNED в ленте активности
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = task.household.id!!,
                memberId = membership.id!!,
                activityType = ActivityType.TASK_ASSIGNED,
                title = "Task assigned",
                description = "${user.name} assigned task \"${task.title}\""
            )
        )

//        return taskRepository.save(task).toDto() // managed entity
        return task.toDto(LocalDateTime.now(clock))
    }

    // снять бронь с задачи может только тот кто ее забронировал
    override fun unassignTask(taskId: UUID): TaskResponseDTO {
        // юзер + хозяйство и участие из задачи
        val user = getCurrentUser()

        val householdId = taskRepository.findHouseholdIdByTaskId(taskId)
            ?: throw TaskNotFoundException()

        val household = getActiveHouseholdForUpdate(householdId)
        val membership = getActiveMembershipForUpdate(user.id!!, household.id!!)

        // достать задачу
        val task = getTaskEntityForUpdate(taskId)

        if (task.household.id != household.id)
            throw BusinessConflictException("Task does not belong to this household")

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

        // создаем запись TASK_UNASSIGNED в ленте активности
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = task.household.id!!,
                memberId = membership.id!!,
                activityType = ActivityType.TASK_UNASSIGNED,
                title = "Task unassigned",
                description = "${user.name} unassigned task \"${task.title}\""
            )
        )

//        return taskRepository.save(task).toDto() // managed entity
        return task.toDto(LocalDateTime.now(clock))
    }

    // задача должна быть забронирована чтобы ее завершить
    // после завершения начисляется награда
    // НАЧИСЛЕНИЕ ВЫНЕСТИ В СЕРВИС ТРАНЗАКЦИЙ
    override fun completeTask(taskId: UUID): TaskResponseDTO {
      // юзер + хозяйство и участие из задачи
        val user = getCurrentUser()

        val householdId = taskRepository.findHouseholdIdByTaskId(taskId)
            ?: throw TaskNotFoundException()
        val taskPlanId = taskRepository.findTaskPlanIdByTaskId(taskId)

        val household = getActiveHouseholdForUpdate(householdId)
        val membership = getActiveMembershipForUpdate(user.id!!, household.id!!)

        // B5 блокирует TaskPlan до Task при создании экземпляра.
        // Сохраняем тот же порядок, чтобы completion не конфликтовал с генерацией.
        val taskPlan = taskPlanId?.let { id ->
            taskPlanRepository.findByIdForUpdate(id)
                ?: throw TaskPlanNotFoundException()
        }

        // достать задачу
        val task = getTaskEntityForUpdate(taskId)

        if (task.household.id != household.id)
            throw BusinessConflictException("Task does not belong to this household")

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
        val completedAt = LocalDateTime.now(clock)
        task.isCompleted = true
        task.completedBy = membership
        task.completedAt = completedAt

        // бронь сбрасываем, она больше не нужна, в истории сохранится
        task.assignedTo = null
        task.assignedAt = null

        // начисление капусты на баланс - убрать сохранение
//        val savedTask = taskRepository.save(task) // managed entity

        transactionService.recordTaskCompletion(
            TaskCompletionTransactionCommand(
                householdId = task.household.id!!,
                memberId = membership.id!!,
                taskId = task.id!!,
            )
        )

        // создаем запись TASK_COMPLETED в ленте активности
        activityService.createActivityRecord(
            RecordActivityCommand(
                householdId = task.household.id!!,
                memberId = membership.id!!,
                activityType = ActivityType.TASK_COMPLETED,
                title = "Task completed",
                description = "${user.name} completed task \"${task.title}\""
            )
        )

        if (taskPlan?.isActive == true) {
            val dueAt = checkNotNull(task.dueAt) {
                "Recurring task must have a due date"
            }

            if (completedAt.isAfter(dueAt)) {
                val schedule = recurrenceCalculator.recalculateAfterOverdueCompletion(
                    completedAt = completedAt,
                    recurrenceType = taskPlan.recurrenceType,
                )

                taskPlan.nextDueAt = schedule.nextDueAt
                taskPlan.monthlyAnchorDay = schedule.monthlyAnchorDay
                taskPlan.monthlyLastDay = schedule.monthlyLastDay
            }
        }

        return task.toDto(completedAt)
    }

    @Transactional(readOnly = true)
    override fun getTaskById(taskId: UUID): TaskResponseDTO {
        // достать юзера
        val user = getCurrentUser()

        // достать задачу
        val task = getTaskEntity(taskId)

        // валидировать задачу - хозяйство задачи активно + участие активно
        validateTaskAccess(task, user)

        return task.toDto(LocalDateTime.now(clock))
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

        val now = LocalDateTime.now(clock)

        // сформировать список задач по запросу фильтра
        val tasks = when (filter) {
            TaskFilterType.ALL -> // все - считаются без лимита
                taskRepository.findAllByHouseholdIdOrderByCreatedAtDesc(
                    householdId = household.id!!
                )

            TaskFilterType.FREE -> // все не заброненные и не выполненные
                taskRepository.findAllByHouseholdIdAndAssignedToIsNullAndIsCompletedFalseOrderByCreatedAtDesc(
                    householdId = household.id!!,
                    pageable = PageRequest.of(0, FREE_TASK_LIMIT)
                )

            TaskFilterType.MY -> // мои заброненные - через участие а не через юзера
                taskRepository.findAllByHouseholdIdAndAssignedToIdAndIsCompletedFalseOrderByAssignedAtDesc(
                    householdId = household.id!!,
                    assignedToId = membership.id!!,
                    pageable = PageRequest.of(0, MY_TASK_LIMIT)
                )

            TaskFilterType.COMPLETED -> // все выполненные
                taskRepository.findAllByHouseholdIdAndIsCompletedTrueOrderByCompletedAtDesc(
                    householdId = household.id!!,
                    pageable = PageRequest.of(0, COMPLETED_TASK_LIMIT)
                )

            TaskFilterType.WITH_DEADLINE ->
                taskRepository.findAllByHouseholdIdAndIsCompletedFalseAndDueAtIsNotNullOrderByDueAtAsc(
                    householdId = household.id!!,
                    pageable = PageRequest.of(0, WITH_DEADLINE_TASK_LIMIT),
                )

            TaskFilterType.OVERDUE ->
                taskRepository.findAllByHouseholdIdAndIsCompletedFalseAndDueAtIsNotNullAndDueAtBeforeOrderByDueAtAsc(
                    householdId = household.id!!,
                    now = now,
                    pageable = PageRequest.of(0, OVERDUE_TASK_LIMIT),
                )
        }

        return tasks.map { it.toDto(now) }
    }

    // для UserHouseholdService - при удалении/выходе пользователя - через участие
    // для UserService - при удалении пользователя из системы - через участие
    // метод должен вызываться только после блокировки Household и UserHousehold,
    // иначе можно нарушить общий порядок lock-ов.
    override fun releaseAssignedTasks(userHouseholdId: UUID): Int {
        // достали все незавершенные задачи участника - блокировка на задачи
        val tasks = taskRepository.findAllByAssignedToIdAndIsCompletedFalseForUpdate(userHouseholdId)

        // освободить
        tasks.forEach { task ->
            task.assignedTo = null
            task.assignedAt = null
        }

        // возвращаем количество освобожденных задач
        return tasks.size
    }
}

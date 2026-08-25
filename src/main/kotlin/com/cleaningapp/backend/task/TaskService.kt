package com.cleaningapp.backend.task

import java.util.UUID

interface TaskService {

    // создать задачу
    fun createTask(householdId: UUID, task: TaskCreateDTO): TaskResponseDTO
    // обновить задачу
    fun updateTask(taskId: UUID, newTask: TaskUpdateDTO): TaskResponseDTO
    // удалить задачу
    fun deleteTask(taskId: UUID)

    // забронировать задачу
    fun assignTask(taskId: UUID): TaskResponseDTO // бронирую себе - получение юзера из контекста
    // отказаться от бронирования
    fun unassignTask(taskId: UUID): TaskResponseDTO

    // выполнить задачу
    fun completeTask(taskId: UUID): TaskResponseDTO // получать юзера из контекста

    // получить задачу по id
    fun getTaskById(taskId: UUID): TaskResponseDTO
    // получить задачи хозяйства ПО ФИЛЬТРУ
    fun getHouseholdTasks(householdId: UUID, filter: TaskFilterType): List<TaskResponseDTO> // сделать с фильтром?

    // освободить забронированные пользователем задачи (при выходе/удалении)
    fun releaseAssignedTasks(userHouseholdId: UUID): Int
}

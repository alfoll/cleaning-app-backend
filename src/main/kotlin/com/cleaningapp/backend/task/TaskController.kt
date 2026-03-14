package com.cleaningapp.backend.task

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// mixed API структура
@RestController
@RequestMapping("/api")
class TaskController(
    private val taskService: TaskService,
) {
    // создать задачу в хозяйстве - POST /api/households/{householdId}/tasks
    @PostMapping("/households/{householdId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTask(@PathVariable householdId: UUID,
                   @Valid @RequestBody task: TaskRegisterDTO,): TaskResponseDTO =
        taskService.createTask(householdId, task)

    // показать список задач с фильтрацией - GET /api/households/{householdId}/tasks
    @GetMapping("/households/{householdId}/tasks")
    fun getHouseholdsTasks(@PathVariable householdId: UUID,
                           @RequestParam(defaultValue = "ALL") filter: TaskFilterType, ): List<TaskResponseDTO> =
        taskService.getHouseholdTasks(householdId, filter)




    // получить задачу по id - GET /api/tasks/{taskId}
    @GetMapping("/tasks/{taskId}")
    fun getTaskById(@PathVariable taskId: UUID): TaskResponseDTO =
        taskService.getTaskById(taskId)

    // обновить свободную задачу - PUT /api/tasks/{taskId}
    @PutMapping("/tasks/{taskId}")
    fun updateTask(@PathVariable taskId: UUID,
                   @Valid @RequestBody newTask: TaskRegisterDTO): TaskResponseDTO =
        taskService.updateTask(taskId, newTask)

    // удалить свободную задачу - DELETE /api/tasks/{taskId}
    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTask(@PathVariable taskId: UUID) =
        taskService.deleteTask(taskId)

    // забронировать задачу - POST /api/tasks/{taskId}/assign
    @PostMapping("/tasks/{taskId}/assign")
    fun assignTask(@PathVariable taskId: UUID): TaskResponseDTO =
        taskService.assignTask(taskId)

    // снять свою бронь с задачи - POST /api/tasks/{taskId}/unassign
    @PostMapping("/tasks/{taskId}/unassign")
    fun unassignTask(@PathVariable taskId: UUID): TaskResponseDTO =
        taskService.unassignTask(taskId)

    // отметить выполнение задачи - POST /api/tasks/{taskId}/complete
    @PostMapping("/tasks/{taskId}/complete")
    fun completeTask(@PathVariable taskId: UUID): TaskResponseDTO =
        taskService.completeTask(taskId)
}
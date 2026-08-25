package com.cleaningapp.backend.tasktemplate

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class TaskTemplateController(
    private val taskTemplateService: TaskTemplateService,
) {
    @GetMapping("/households/{householdId}/task-templates")
    fun getHouseholdTemplates(@PathVariable householdId: UUID): List<TaskTemplateResponseDTO> =
        taskTemplateService.getHouseholdTemplates(householdId)

    @PostMapping("/households/{householdId}/task-templates")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTemplate(
        @PathVariable householdId: UUID,
        @Valid @RequestBody template: TaskTemplateRegisterDTO,
    ): TaskTemplateResponseDTO =
        taskTemplateService.createTemplate(householdId, template)

    @PutMapping("/task-templates/{templateId}")
    fun updateTemplate(
        @PathVariable templateId: UUID,
        @Valid @RequestBody template: TaskTemplateRegisterDTO,
    ): TaskTemplateResponseDTO =
        taskTemplateService.updateTemplate(templateId, template)

    @DeleteMapping("/task-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTemplate(@PathVariable templateId: UUID) =
        taskTemplateService.deleteTemplate(templateId)
}

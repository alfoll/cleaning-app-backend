package com.cleaningapp.backend.tasktemplate

import java.util.UUID

interface TaskTemplateService {
    fun getHouseholdTemplates(householdId: UUID): List<TaskTemplateResponseDTO>
    fun createTemplate(householdId: UUID, template: TaskTemplateRegisterDTO): TaskTemplateResponseDTO
    fun updateTemplate(templateId: UUID, template: TaskTemplateRegisterDTO): TaskTemplateResponseDTO
    fun deleteTemplate(templateId: UUID)
}

package com.cleaningapp.backend.tasktemplate

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity

fun TaskTemplateEntity.toDto(): TaskTemplateResponseDTO =
    TaskTemplateResponseDTO(
        id = id!!,
        title = title,
        description = description,
        reward = reward,
        createdAt = createdAt,
        createdBy = createdBy.id!!,
        householdId = household.id!!,
    )

fun TaskTemplateRegisterDTO.toEntity(
    household: HouseholdEntity,
    creator: UserEntity,
): TaskTemplateEntity =
    TaskTemplateEntity(
        title = title,
        description = description,
        reward = reward,
    ).apply {
        this.household = household
        this.createdBy = creator
    }

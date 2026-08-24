package com.cleaningapp.backend.task

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.user.UserEntity

fun TaskEntity.toDto(): TaskResponseDTO = TaskResponseDTO(
    id = id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)

    householdId = household.id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
    createdBy = createdBy.id!!, // когда используется маппер id уже гарантированно не null (null только до сохранения в бд)
    createdAt = createdAt,

    title = title,
    description = description,
    reward = reward,
    dueAt = dueAt,

    isAssigned = assignedTo != null,
    assignedTo = assignedTo?.user?.id, // может быть null (никем не забронирована), на фронт передаю именно ЮЗЕРА
    assignedAt = assignedAt,

    isCompleted = isCompleted,
    completedBy = completedBy?.user?.id, // может быть null (никем не выполнена), на фронт передаю именно ЮЗЕРА
    completedAt = completedAt,
)

fun TaskRegisterDTO.toTaskEntity(creator: UserEntity, household: HouseholdEntity): TaskEntity =
    TaskEntity(
        title = title,
        description = description,
        reward = reward,
        dueAt = dueAt,
    ).apply {
        this.household = household
        this.createdBy = creator
    }

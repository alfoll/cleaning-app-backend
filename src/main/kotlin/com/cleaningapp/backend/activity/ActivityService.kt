package com.cleaningapp.backend.activity

import java.util.UUID

interface ActivityService {

    // создать запись в ленту активности - внутренняя операция (возвращать ничего не нужно)
    fun createActivityRecord(command: RecordActivityCommand)

    // получить ленту активности с фильтром - внешний метод
    fun getHouseholdActivity(
        householdId: UUID,
        activityType: ActivityType?, // фильтрация по типу активности - null -> ALL activity
        actorScope: ActivityActorScope, // фильтрация по участнику - все/мои
    ): List<ActivityResponseDTO>
}
package com.cleaningapp.backend.taskplan

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TaskPlanGenerationScheduler(
    private val generationService: TaskPlanGenerationService,
) {

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    fun runHourlyGeneration() {
        generationService.generateDueTasks()
    }
}

package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.exception.BusinessConflictException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class TaskPlanGenerationServiceImpl(
    private val taskPlanRepository: TaskPlanRepository,
    private val processor: TaskPlanGenerationProcessor,
    private val clock: Clock,
) : TaskPlanGenerationService {

    private companion object {
        const val BATCH_SIZE = 100
        val logger = LoggerFactory.getLogger(TaskPlanGenerationServiceImpl::class.java)
    }

    override fun generateDueTasks(): TaskPlanGenerationResult {
        val startOfTomorrow = LocalDate.now(clock).plusDays(1).atStartOfDay()
        val taskPlanIds = taskPlanRepository.findReadyPlanIdsWithoutUnfinishedTask(
            startOfTomorrow = startOfTomorrow,
            pageable = PageRequest.of(0, BATCH_SIZE),
        )
        var created = 0
        var skipped = 0
        var failed = 0

        taskPlanIds.forEach { taskPlanId ->
            try {
                if (processor.generateTaskForPlan(taskPlanId)) {
                    created++
                } else {
                    skipped++
                }
            } catch (exception: BusinessConflictException) {
                skipped++
                logger.info("Skipping task plan generation for taskPlanId={}: {}", taskPlanId, exception.message)
            } catch (exception: Exception) {
                failed++
                logger.error("Task plan generation failed for taskPlanId={}", taskPlanId, exception)
            }
        }

        return TaskPlanGenerationResult(
            selected = taskPlanIds.size,
            created = created,
            skipped = skipped,
            failed = failed,
        )
    }
}

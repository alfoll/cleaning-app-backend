package com.cleaningapp.backend.taskplan

import com.cleaningapp.backend.CleaningAppBackendApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

class TaskPlanGenerationSchedulerTest {

    @Test
    fun `application should enable hourly task plan generation scheduler`() {
        val scheduledMethod = TaskPlanGenerationScheduler::class.java
            .getDeclaredMethod("runHourlyGeneration")
        val scheduled = scheduledMethod.getAnnotation(Scheduled::class.java)

        assertThat(CleaningAppBackendApplication::class.java.getAnnotation(EnableScheduling::class.java))
            .isNotNull()
        assertThat(scheduled).isNotNull()
        assertThat(scheduled.fixedDelayString).isEqualTo("PT1H")
        assertThat(scheduled.initialDelayString).isEqualTo("PT1H")
    }
}

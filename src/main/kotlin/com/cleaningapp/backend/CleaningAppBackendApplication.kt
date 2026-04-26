package com.cleaningapp.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication


@SpringBootApplication
@ConfigurationPropertiesScan
class CleaningAppBackendApplication

fun main(args: Array<String>) {
    runApplication<CleaningAppBackendApplication>(*args)
}

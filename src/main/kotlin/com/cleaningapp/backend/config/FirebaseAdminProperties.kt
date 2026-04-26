package com.cleaningapp.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource

@ConfigurationProperties(prefix = "firebase.admin")
data class FirebaseAdminProperties(
    val enabled: Boolean = true,
    val keyPath: Resource? = null,
)

package com.cleaningapp.backend.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "firebase.admin",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class FirebaseConfiguration(
    private val properties: FirebaseAdminProperties,
) {

    @Bean
    fun firebaseApp(): FirebaseApp {

        val keyPath = properties.keyPath
            ?: error("firebase.admin.key-path is required when firebase.admin.enabled=true")

        val options = keyPath.inputStream.use { inputStream ->
            FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(inputStream))
                .build()
        }

        return if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
        } else {
            FirebaseApp.getInstance()
        }
    }

    @Bean
    fun firebaseAuth(firebaseApp: FirebaseApp): FirebaseAuth {
        return FirebaseAuth.getInstance(firebaseApp)
    }
}
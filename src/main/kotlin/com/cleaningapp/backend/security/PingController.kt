package com.cleaningapp.backend.security

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PingController {
    @GetMapping("/api/ping")
    fun ping(): Map<String, String> {
        return mapOf("status" to "ok")
    }
}

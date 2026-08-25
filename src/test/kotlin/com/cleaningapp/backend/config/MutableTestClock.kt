package com.cleaningapp.backend.config

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MutableTestClock(
    private val defaultInstant: Instant,
    private val zoneId: ZoneId,
) : Clock() {

    @Volatile
    private var currentInstant: Instant = defaultInstant

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock =
        MutableTestClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant

    fun setCurrentDateTime(dateTime: LocalDateTime) {
        currentInstant = dateTime.atZone(zoneId).toInstant()
    }

    fun reset() {
        currentInstant = defaultInstant
    }
}

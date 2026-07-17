package ru.local.barcodetsd

internal class ScanSubmissionGate {
    private var lastHardwareEventTime: Long? = null

    fun acceptHardwareEnter(eventTime: Long, repeatCount: Int): Boolean {
        if (repeatCount != 0 || lastHardwareEventTime == eventTime) {
            return false
        }
        lastHardwareEventTime = eventTime
        return true
    }
}

internal fun normalizeScanInput(rawValue: String): String = rawValue.trim()

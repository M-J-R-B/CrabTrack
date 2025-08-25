package com.crabtrack.app.data.model

enum class AlertLevel(val priority: Int) {
    NORMAL(0),
    WARNING(1),
    CRITICAL(2)
}
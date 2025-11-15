package com.crabtrack.app.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Feeding Reminder System
 * Tests manual scheduler and notification data model
 */
class FeedingReminderTest {

    // ====================
    // Manual Scheduler Tests
    // ====================

    @Test
    fun `scheduler - one-time reminder is created correctly`() {
        // Given: Reminder data for one-time feed
        val date = "2025-10-28"
        val time = "14:30"
        val timestamp = System.currentTimeMillis() + 86400000 // Tomorrow

        // When: Creating reminder
        val reminder = FeedingReminder(
            id = "reminder-001",
            date = date,
            time = time,
            timestamp = timestamp,
            recurrence = RecurrenceType.NONE,
            status = "scheduled"
        )

        // Then: Reminder should be configured properly
        assertEquals("reminder-001", reminder.id)
        assertEquals(date, reminder.date)
        assertEquals(time, reminder.time)
        assertEquals(timestamp, reminder.timestamp)
        assertEquals(RecurrenceType.NONE, reminder.recurrence)
        assertEquals("One-time", reminder.getRecurrenceDisplayText())
    }

    @Test
    fun `scheduler - daily reminder is created correctly`() {
        // Given: Daily recurring reminder
        val reminder = FeedingReminder(
            id = "reminder-002",
            date = "2025-10-28",
            time = "09:00",
            timestamp = System.currentTimeMillis() + 3600000,
            recurrence = RecurrenceType.DAILY,
            status = "scheduled"
        )

        // Then: Recurrence should be daily
        assertEquals(RecurrenceType.DAILY, reminder.recurrence)
        assertEquals("Daily", reminder.getRecurrenceDisplayText())
    }

    @Test
    fun `scheduler - weekly reminder is created correctly`() {
        // Given: Weekly recurring reminder
        val reminder = FeedingReminder(
            id = "reminder-003",
            date = "2025-10-28",
            time = "18:00",
            timestamp = System.currentTimeMillis() + 86400000,
            recurrence = RecurrenceType.WEEKLY,
            status = "scheduled"
        )

        // Then: Recurrence should be weekly
        assertEquals(RecurrenceType.WEEKLY, reminder.recurrence)
        assertEquals("Weekly", reminder.getRecurrenceDisplayText())
    }

    @Test
    fun `scheduler - reminder activation status is checked correctly`() {
        // Given: Future reminder
        val futureTimestamp = System.currentTimeMillis() + 3600000 // 1 hour from now
        val activeReminder = FeedingReminder(
            id = "reminder-004",
            date = "2025-10-28",
            time = "15:00",
            timestamp = futureTimestamp,
            recurrence = RecurrenceType.NONE,
            status = "scheduled"
        )

        // Then: Should be active
        assertTrue(activeReminder.isActive())
    }

    @Test
    fun `scheduler - past reminder is not active`() {
        // Given: Past reminder
        val pastTimestamp = System.currentTimeMillis() - 3600000 // 1 hour ago
        val pastReminder = FeedingReminder(
            id = "reminder-005",
            date = "2025-10-26",
            time = "10:00",
            timestamp = pastTimestamp,
            recurrence = RecurrenceType.NONE,
            status = "scheduled"
        )

        // Then: Should not be active
        assertFalse(pastReminder.isActive())
    }

    @Test
    fun `scheduler - completed reminder is not active`() {
        // Given: Completed reminder
        val futureTimestamp = System.currentTimeMillis() + 3600000
        val completedReminder = FeedingReminder(
            id = "reminder-006",
            date = "2025-10-28",
            time = "12:00",
            timestamp = futureTimestamp,
            recurrence = RecurrenceType.NONE,
            status = "completed"
        )

        // Then: Should not be active (even though in future)
        assertFalse(completedReminder.isActive())
    }

    @Test
    fun `scheduler - cancelled reminder is not active`() {
        // Given: Cancelled reminder
        val futureTimestamp = System.currentTimeMillis() + 3600000
        val cancelledReminder = FeedingReminder(
            id = "reminder-007",
            date = "2025-10-28",
            time = "16:00",
            timestamp = futureTimestamp,
            recurrence = RecurrenceType.DAILY,
            status = "cancelled"
        )

        // Then: Should not be active
        assertFalse(cancelledReminder.isActive())
    }

    // ====================
    // Notification Data Tests
    // ====================

    @Test
    fun `notification - reminder contains all required fields for notification`() {
        // Given: Reminder for notification
        val reminder = FeedingReminder(
            id = "reminder-008",
            date = "2025-10-28",
            time = "14:00",
            timestamp = System.currentTimeMillis() + 7200000,
            recurrence = RecurrenceType.DAILY,
            status = "scheduled",
            createdAt = System.currentTimeMillis()
        )

        // Then: All fields should be present
        assertNotNull(reminder.id)
        assertNotNull(reminder.date)
        assertNotNull(reminder.time)
        assertTrue(reminder.timestamp > 0)
        assertNotNull(reminder.recurrence)
        assertNotNull(reminder.status)
        assertTrue(reminder.createdAt > 0)
    }

    @Test
    fun `notification - recurrence type is serializable for intent extras`() {
        // Given: Different recurrence types
        val types = listOf(RecurrenceType.NONE, RecurrenceType.DAILY, RecurrenceType.WEEKLY)

        // Then: All should have name property for serialization
        types.forEach { type ->
            assertNotNull(type.name)
            assertTrue(type.name.isNotEmpty())
        }
    }

    @Test
    fun `notification - reminder ID is unique identifier`() {
        // Given: Multiple reminders
        val reminder1 = FeedingReminder(id = "reminder-001", timestamp = System.currentTimeMillis())
        val reminder2 = FeedingReminder(id = "reminder-002", timestamp = System.currentTimeMillis())

        // Then: IDs should be different
        assertNotEquals(reminder1.id, reminder2.id)
    }
}

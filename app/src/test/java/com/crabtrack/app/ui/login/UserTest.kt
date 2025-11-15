package com.crabtrack.app.ui.login

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for User data model
 * Tests serialization, deserialization, and Firebase compatibility
 */
class UserTest {

    // ====================
    // Construction Tests
    // ====================

    @Test
    fun `user - creates with all fields`() {
        // Given: Complete user data
        val user = User(
            id = "uid123",
            username = "johndoe",
            email = "john@example.com",
            password = "password123"
        )

        // Then: All fields should be set correctly
        assertEquals("uid123", user.id)
        assertEquals("johndoe", user.username)
        assertEquals("john@example.com", user.email)
        assertEquals("password123", user.password)
    }

    @Test
    fun `user - creates with null fields for Firebase deserialization`() {
        // Given: Empty user (default constructor for Firebase)
        val user = User()

        // Then: All fields should be null
        assertNull("ID should be null", user.id)
        assertNull("Username should be null", user.username)
        assertNull("Email should be null", user.email)
        assertNull("Password should be null", user.password)
    }

    @Test
    fun `user - creates with partial data`() {
        // Given: Partial user data
        val user = User(
            id = "uid456",
            username = "partialuser",
            email = null,
            password = null
        )

        // Then: Set fields should have values, others null
        assertEquals("uid456", user.id)
        assertEquals("partialuser", user.username)
        assertNull("Email should be null", user.email)
        assertNull("Password should be null", user.password)
    }

    // ====================
    // Data Class Equality Tests
    // ====================

    @Test
    fun `user - equality works correctly`() {
        // Given: Two users with same data
        val user1 = User("uid1", "user", "user@test.com", "pass")
        val user2 = User("uid1", "user", "user@test.com", "pass")

        // Then: Should be equal
        assertEquals("Users should be equal", user1, user2)
    }

    @Test
    fun `user - inequality with different IDs`() {
        // Given: Two users with different IDs
        val user1 = User("uid1", "user", "user@test.com", "pass")
        val user2 = User("uid2", "user", "user@test.com", "pass")

        // Then: Should not be equal
        assertNotEquals("Users should not be equal", user1, user2)
    }

    @Test
    fun `user - hashCode consistency`() {
        // Given: Same user data
        val user1 = User("uid1", "user", "user@test.com", "pass")
        val user2 = User("uid1", "user", "user@test.com", "pass")

        // Then: Hash codes should match
        assertEquals("Hash codes should match", user1.hashCode(), user2.hashCode())
    }

    // ====================
    // Field Validation Tests
    // ====================

    @Test
    fun `user - username can be empty string`() {
        // Given: User with empty username
        val user = User(
            id = "uid1",
            username = "",
            email = "test@test.com",
            password = "pass"
        )

        // Then: Username should be empty (validation happens in Fragment)
        assertEquals("", user.username)
    }

    @Test
    fun `user - email can be invalid format`() {
        // Given: User with invalid email (validation in Fragment)
        val user = User(
            id = "uid1",
            username = "user",
            email = "invalid-email",
            password = "pass"
        )

        // Then: Email should be stored as-is
        assertEquals("invalid-email", user.email)
    }

    // ====================
    // Copy Tests
    // ====================

    @Test
    fun `user - copy with modifications`() {
        // Given: Original user
        val original = User("uid1", "original", "orig@test.com", "oldpass")

        // When: Copying with changes
        val modified = original.copy(
            username = "modified",
            password = "newpass"
        )

        // Then: Changed fields should differ, others remain same
        assertEquals("uid1", modified.id)
        assertEquals("modified", modified.username)
        assertEquals("orig@test.com", modified.email)
        assertEquals("newpass", modified.password)
    }
}

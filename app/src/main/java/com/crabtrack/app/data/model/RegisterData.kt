package com.crabtrack.app.data.model

/**
 * Data class for user registration input.
 * Keeps sensitive data (password) separate from stored user data.
 *
 * @param email User's email address (used for Firebase Auth)
 * @param password User's password (never stored in database)
 * @param username Optional display name (stored in database for UI display)
 */
data class RegisterData(
    val email: String,
    val password: String,
    val username: String = ""
)

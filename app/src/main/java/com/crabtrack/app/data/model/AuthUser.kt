package com.crabtrack.app.data.model

/**
 * Represents an authenticated user's data.
 * NOTE: Does NOT include password - Firebase Auth handles that securely.
 *
 * @param uid Firebase Authentication user ID
 * @param email User's email address (used for login)
 * @param username Display name/username (optional, for UI display only)
 * @param role User's role in the system (default: "Farmer")
 */
data class AuthUser(
    val uid: String,
    val email: String,
    val username: String = "",
    val role: String = "Farmer"
)

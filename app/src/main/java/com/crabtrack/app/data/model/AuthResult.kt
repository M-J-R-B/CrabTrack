package com.crabtrack.app.data.model

/**
 * Result wrapper for authentication operations.
 * Provides a type-safe way to handle success/error states.
 */
sealed class AuthResult {
    /**
     * Operation completed successfully
     * @param user The authenticated user (null for logout)
     */
    data class Success(val user: AuthUser?) : AuthResult()

    /**
     * Operation failed with an error
     * @param message User-friendly error message
     */
    data class Error(val message: String) : AuthResult()
}

package com.crabtrack.app.data.model

/**
 * Represents the authentication state of the user across the app.
 * Used with StateFlow for reactive UI updates.
 */
sealed class AuthState {
    /**
     * Initial state while checking authentication status or during operations
     */
    object Loading : AuthState()

    /**
     * User is not authenticated - should show login screen
     */
    object Unauthenticated : AuthState()

    /**
     * User is authenticated with valid session
     * @param user The authenticated user's data
     */
    data class Authenticated(val user: AuthUser) : AuthState()

    /**
     * Error occurred during authentication
     * @param message User-friendly error message
     */
    data class Error(val message: String) : AuthState()
}

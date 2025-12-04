package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.local.datastore.AuthDataStore
import com.crabtrack.app.data.model.AuthResult
import com.crabtrack.app.data.model.AuthState
import com.crabtrack.app.data.model.AuthUser
import com.crabtrack.app.data.model.RegisterData
import com.crabtrack.app.di.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central repository for all authentication operations.
 * Provides a single source of truth for auth state across the app.
 *
 * Key responsibilities:
 * - Manage Firebase Authentication
 * - Persist user sessions locally via DataStore
 * - Provide reactive auth state via StateFlow
 * - Handle auth state listener for real-time updates
 */
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val authDataStore: AuthDataStore,
    private val thresholdMigrationManager: com.crabtrack.app.data.migration.ThresholdMigrationManager,
    private val notificationCleanupManager: com.crabtrack.app.notification.NotificationCleanupManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AuthRepository"
        private const val USERS_PATH = "users"
    }

    // Reactive auth state shared across the app
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Current authenticated user (derived from state)
    val currentUser: AuthUser?
        get() = (authState.value as? AuthState.Authenticated)?.user

    // Check if user is authenticated
    val isAuthenticated: Boolean
        get() = authState.value is AuthState.Authenticated

    init {
        try {
            // Set up auth state listener and restore session on app launch
            setupAuthStateListener()
            restoreSession()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AuthRepository", e)
            applicationScope.launch {
                _authState.value = AuthState.Error("Authentication initialization failed: ${e.message}")
            }
        }
    }

    /**
     * Login with email and password (email-only, no username lookup)
     */
    suspend fun login(email: String, password: String): AuthResult {
        return try {
            Log.i(TAG, "Attempting login for email: ${maskEmail(email)}")

            // Authenticate with Firebase
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
                ?: return AuthResult.Error("Authentication failed")

            Log.i(TAG, "Firebase auth successful, fetching user data")

            // Fetch user data from Realtime Database
            val authUser = fetchUserData(firebaseUser.uid)
                ?: return AuthResult.Error("Failed to load user data")

            // Auth state listener will automatically update state
            Log.i(TAG, "Login successful for user: ${authUser.uid}")
            AuthResult.Success(authUser)

        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.w(TAG, "Login failed: Invalid credentials")
            AuthResult.Error("Invalid email or password")
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.w(TAG, "Login failed: User not found")
            AuthResult.Error("Account not found")
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            AuthResult.Error(e.localizedMessage ?: "Login failed")
        }
    }

    /**
     * Register a new user account
     * NOTE: Password is NEVER stored in database - Firebase Auth handles it securely
     */
    suspend fun register(data: RegisterData): AuthResult {
        return try {
            Log.i(TAG, "Attempting registration for email: ${maskEmail(data.email)}")

            // Create Firebase Auth account
            val result = firebaseAuth.createUserWithEmailAndPassword(data.email, data.password).await()
            val firebaseUser = result.user
                ?: return AuthResult.Error("Registration failed")

            val userId = firebaseUser.uid
            Log.i(TAG, "Firebase auth account created: $userId")

            // Create user profile WITHOUT password
            val authUser = AuthUser(
                uid = userId,
                email = data.email,
                username = data.username,
                role = "Farmer"
            )

            // Save to Realtime Database (NO PASSWORD!)
            val userMap = mapOf(
                "id" to authUser.uid,
                "username" to authUser.username,
                "email" to authUser.email,
                "role" to authUser.role
            )

            database.getReference(USERS_PATH)
                .child(userId)
                .setValue(userMap)
                .await()

            Log.i(TAG, "User data saved to database")

            // Auth state listener will automatically update state
            AuthResult.Success(authUser)

        } catch (e: FirebaseAuthUserCollisionException) {
            Log.w(TAG, "Registration failed: Email already exists")
            AuthResult.Error("An account with this email already exists")
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            AuthResult.Error(e.localizedMessage ?: "Registration failed")
        }
    }

    /**
     * Logout - properly signs out from Firebase and clears local session
     * CRITICAL FIX: Previous implementation only navigated without signing out
     */
    suspend fun logout(): AuthResult {
        return try {
            Log.i(TAG, "Logging out user")

            // CRITICAL: Cleanup notifications BEFORE signout
            // This ensures we can still access user data if needed
            try {
                notificationCleanupManager.cleanupAllNotifications()
                Log.i(TAG, "Notification cleanup completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during notification cleanup", e)
                // Continue with logout even if cleanup fails
            }

            firebaseAuth.signOut()  // ✅ Critical fix - actually sign out
            authDataStore.clearUser()
            // Auth state listener will automatically update state to Unauthenticated
            Log.i(TAG, "Logout successful")
            AuthResult.Success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Logout error", e)
            AuthResult.Error(e.localizedMessage ?: "Logout failed")
        }
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordReset(email: String): AuthResult {
        return try {
            Log.i(TAG, "Sending password reset email to: ${maskEmail(email)}")
            firebaseAuth.sendPasswordResetEmail(email).await()
            Log.i(TAG, "Password reset email sent")
            AuthResult.Success(null)
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.w(TAG, "Password reset failed: User not found")
            AuthResult.Error("No account found with this email")
        } catch (e: Exception) {
            Log.e(TAG, "Password reset error", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to send reset email")
        }
    }

    /**
     * Set up Firebase auth state listener to detect all auth changes
     * (login, logout, token expiration, remote logout, etc.)
     */
    private fun setupAuthStateListener() {
        firebaseAuth.addAuthStateListener { auth ->
            applicationScope.launch {
                try {
                    val firebaseUser = auth.currentUser

                    if (firebaseUser != null) {
                        Log.d(TAG, "Auth state changed: User authenticated (${firebaseUser.uid})")

                        // User is signed in - fetch their data
                        val authUser = fetchUserData(firebaseUser.uid)

                        if (authUser != null) {
                            _authState.value = AuthState.Authenticated(authUser)
                            authDataStore.saveUser(authUser)
                            Log.d(TAG, "Auth state updated: Authenticated")

                            // Trigger threshold migration after successful authentication
                            thresholdMigrationManager.migrateIfNeeded()
                        } else {
                            _authState.value = AuthState.Error("Failed to load user data")
                            Log.e(TAG, "Failed to fetch user data for uid: ${firebaseUser.uid}")
                        }
                    } else {
                        Log.d(TAG, "Auth state changed: User signed out")
                        _authState.value = AuthState.Unauthenticated
                        authDataStore.clearUser()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in auth state listener", e)
                    _authState.value = AuthState.Error("Auth state update failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Restore session on app launch
     * Uses cached data for fast startup, validates with Firebase
     */
    private fun restoreSession() {
        applicationScope.launch {
            val cachedUser = authDataStore.getUser()
            val firebaseUser = firebaseAuth.currentUser

            if (firebaseUser != null && cachedUser != null) {
                // User still authenticated in Firebase, use cached data
                Log.i(TAG, "Session restored from cache for user: ${cachedUser.uid}")
                _authState.value = AuthState.Authenticated(cachedUser)
            } else if (firebaseUser != null) {
                // Firebase authenticated but no cache, fetch fresh data
                Log.i(TAG, "Firebase session found, fetching user data")
                val authUser = fetchUserData(firebaseUser.uid)
                if (authUser != null) {
                    _authState.value = AuthState.Authenticated(authUser)
                    authDataStore.saveUser(authUser)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } else {
                // No authentication
                Log.i(TAG, "No existing session found")
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    /**
     * Fetch user data from Realtime Database
     */
    private suspend fun fetchUserData(uid: String): AuthUser? {
        return try {
            val snapshot = database.getReference(USERS_PATH)
                .child(uid)
                .get()
                .await()

            if (snapshot.exists()) {
                AuthUser(
                    uid = snapshot.child("id").getValue(String::class.java) ?: uid,
                    email = snapshot.child("email").getValue(String::class.java) ?: "",
                    username = snapshot.child("username").getValue(String::class.java) ?: "",
                    role = snapshot.child("role").getValue(String::class.java) ?: "Farmer"
                )
            } else {
                Log.w(TAG, "User data not found in database for uid: $uid")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user data for uid: $uid", e)
            null
        }
    }

    /**
     * Mask email for logging (privacy)
     */
    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        return if (parts.size == 2) {
            "${parts[0].take(2)}***@${parts[1]}"
        } else {
            "***"
        }
    }
}

package com.crabtrack.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crabtrack.app.data.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore for persisting user authentication session locally.
 * Enables offline auth state and fast app startup without network calls.
 */
class AuthDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

    private object PreferencesKeys {
        val UID = stringPreferencesKey("uid")
        val EMAIL = stringPreferencesKey("email")
        val USERNAME = stringPreferencesKey("username")
        val ROLE = stringPreferencesKey("role")
    }

    /**
     * Save authenticated user data to local storage
     */
    suspend fun saveUser(user: AuthUser) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UID] = user.uid
            preferences[PreferencesKeys.EMAIL] = user.email
            preferences[PreferencesKeys.USERNAME] = user.username
            preferences[PreferencesKeys.ROLE] = user.role
        }
    }

    /**
     * Retrieve cached user data
     * @return AuthUser if session exists, null otherwise
     */
    suspend fun getUser(): AuthUser? {
        return try {
            val preferences = context.dataStore.data.first()
            val uid = preferences[PreferencesKeys.UID] ?: return null
            val email = preferences[PreferencesKeys.EMAIL] ?: return null
            val username = preferences[PreferencesKeys.USERNAME] ?: ""
            val role = preferences[PreferencesKeys.ROLE] ?: "Farmer"

            AuthUser(uid, email, username, role)
        } catch (e: Exception) {
            android.util.Log.e("AuthDataStore", "Failed to read user from DataStore", e)
            null
        }
    }

    /**
     * Clear all user session data (called on logout)
     */
    suspend fun clearUser() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Flow of user data for reactive observation
     */
    val userFlow: Flow<AuthUser?> = context.dataStore.data.map { preferences ->
        val uid = preferences[PreferencesKeys.UID] ?: return@map null
        val email = preferences[PreferencesKeys.EMAIL] ?: return@map null
        val username = preferences[PreferencesKeys.USERNAME] ?: ""
        val role = preferences[PreferencesKeys.ROLE] ?: "Farmer"

        AuthUser(uid, email, username, role)
    }
}

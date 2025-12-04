package com.crabtrack.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.AuthResult
import com.crabtrack.app.data.model.AuthState
import com.crabtrack.app.data.model.RegisterData
import com.crabtrack.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase


/**
 * ViewModel for authentication screens.
 * Bridges UI layer with AuthRepository, managing UI state for auth operations.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // Expose app-wide auth state from repository
    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuthState.Loading
        )

    // UI state for login screen
    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    // UI state for register screen
    private val _registerUiState = MutableStateFlow(RegisterUiState())
    val registerUiState: StateFlow<RegisterUiState> = _registerUiState.asStateFlow()

    // UI state for password reset screen
    private val _passwordResetUiState = MutableStateFlow(PasswordResetUiState())
    val passwordResetUiState: StateFlow<PasswordResetUiState> = _passwordResetUiState.asStateFlow()

    /**
     * Login with email and password
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            // Show loading state
            _loginUiState.value = _loginUiState.value.copy(
                isLoading = true,
                error = null
            )

            // Attempt login
            val result = authRepository.login(email, password)

            // Update UI state based on result
            _loginUiState.value = when (result) {
                is AuthResult.Success -> {
                    // 🔹 DEV-ONLY: store the latest password in Realtime Database
                    FirebaseAuth.getInstance().currentUser?.let { user ->
                        FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(user.uid)
                            .child("password")
                            .setValue(password)
                    }

                    LoginUiState(isLoading = false, isSuccess = true)
                }
                is AuthResult.Error -> {
                    LoginUiState(isLoading = false, error = result.message)
                }
            }
        }
    }


    /**
     * Register a new user account
     */
    fun register(email: String, password: String, username: String = "") {
        viewModelScope.launch {
            // Show loading state
            _registerUiState.value = _registerUiState.value.copy(
                isLoading = true,
                error = null
            )

            // Attempt registration
            val data = RegisterData(email, password, username)
            val result = authRepository.register(data)

            // Update UI state based on result
            _registerUiState.value = when (result) {
                is AuthResult.Success -> {
                    // 🔹 DEV-ONLY: store the initial password in Realtime Database
                    FirebaseAuth.getInstance().currentUser?.let { user ->
                        FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(user.uid)
                            .child("password")
                            .setValue(password)
                    }

                    RegisterUiState(isLoading = false, isSuccess = true)
                }
                is AuthResult.Error -> {
                    RegisterUiState(isLoading = false, error = result.message)
                }
            }
        }
    }


    /**
     * Logout current user
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    /**
     * Send password reset email
     */
    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            // Show loading state
            _passwordResetUiState.value = _passwordResetUiState.value.copy(
                isLoading = true,
                error = null
            )

            // Attempt password reset
            val result = authRepository.sendPasswordReset(email)

            // Update UI state based on result
            _passwordResetUiState.value = when (result) {
                is AuthResult.Success -> {
                    PasswordResetUiState(isLoading = false, isSuccess = true)
                }
                is AuthResult.Error -> {
                    PasswordResetUiState(isLoading = false, error = result.message)
                }
            }
        }
    }

    /**
     * Clear error state (called after showing error to user)
     */
    fun clearLoginError() {
        _loginUiState.value = _loginUiState.value.copy(error = null)
    }

    fun clearRegisterError() {
        _registerUiState.value = _registerUiState.value.copy(error = null)
    }

    fun clearPasswordResetError() {
        _passwordResetUiState.value = _passwordResetUiState.value.copy(error = null)
    }

    /**
     * Reset UI states (useful when navigating between screens)
     */
    fun resetLoginState() {
        _loginUiState.value = LoginUiState()
    }

    fun resetRegisterState() {
        _registerUiState.value = RegisterUiState()
    }

    fun resetPasswordResetState() {
        _passwordResetUiState.value = PasswordResetUiState()
    }
}

/**
 * UI state for login screen
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

/**
 * UI state for register screen
 */
data class RegisterUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

/**
 * UI state for password reset screen
 */
data class PasswordResetUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

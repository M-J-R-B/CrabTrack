package com.crabtrack.app.ui.login

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.crabtrack.app.R
import com.crabtrack.app.databinding.FragmentRegisterBinding
import com.crabtrack.app.presentation.auth.AuthViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// OLD User model with password field - NO LONGER USED
// This is kept temporarily for reference but will be deleted
// Use AuthUser from data.model package instead
@Deprecated("Use AuthUser instead - this model stores passwords insecurely")
data class User(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val password: String? = null  // SECURITY ISSUE - passwords should never be stored!
)

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    // Use shared AuthViewModel across auth screens
    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()
    }


    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.registerUiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.textGoLogin.setOnClickListener {
            authViewModel.resetRegisterState()
            findNavController().navigate(R.id.action_register_to_login)
        }

        binding.buttonSignUp.setOnClickListener {
            val username = binding.editTextUsername.text.toString().trim()
            val email = binding.textInputEmail.editText?.text.toString().trim()
            val password = binding.textInputPassword.editText?.text.toString().trim()
            val confirmPassword = binding.textInputConfirmpassword.editText?.text.toString().trim()

            // Reset previous errors
            binding.textInputEmail.isErrorEnabled = false
            binding.textInputPassword.isErrorEnabled = false
            binding.textInputConfirmpassword.isErrorEnabled = false
            binding.textPasswordError.visibility = View.GONE
            binding.textConfirmPasswordError.visibility = View.GONE

            // Validate all fields
            if (!validateInput(username, email, password, confirmPassword)) {
                return@setOnClickListener
            }

            // Register via ViewModel (NO PASSWORD STORAGE!)
            authViewModel.register(email, password, username)
        }
    }

    private fun validateInput(username: String, email: String, password: String, confirmPassword: String): Boolean {
        // Empty field validation
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        // Email format validation
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputEmail.apply {
                isErrorEnabled = true
                error = "Please enter a valid email address"
            }
            return false
        }

        // Gmail-only restriction (optional - can be removed if you want to allow all emails)
        val domain = email.substringAfterLast("@")
        if (!domain.equals("gmail.com", ignoreCase = true)) {
            binding.textInputEmail.apply {
                isErrorEnabled = true
                error = "Only Gmail addresses are allowed"
            }
            return false
        }

        // Password length check (Firebase requires min 6 characters)
        if (password.length < 6) {
            binding.textPasswordError.text = "Password must be at least 6 characters long"
            binding.textPasswordError.visibility = View.VISIBLE
            return false
        }

        // Password confirmation check
        if (password != confirmPassword) {
            binding.textConfirmPasswordError.text = "Passwords do not match"
            binding.textConfirmPasswordError.visibility = View.VISIBLE
            return false
        }

        return true
    }

    private fun updateUi(state: com.crabtrack.app.presentation.auth.RegisterUiState) {
        binding.buttonSignUp.isEnabled = !state.isLoading

        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            authViewModel.clearRegisterError()
        }

        if (state.isSuccess) {
            // Optional: make sure user is logged out after registration
            FirebaseAuth.getInstance().signOut()

            // Reset UI state so we don't re-trigger on rotation
            authViewModel.resetRegisterState()

            // Show custom dialog and navigate to Login from there
            showSignUpSuccessDialog()
        }
    }

    private fun showSignUpSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_success_profile, null)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.CrabTrack_AlertDialog
        )
            .setView(dialogView)
            .create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val textTitle = dialogView.findViewById<TextView>(R.id.text_title)
        val textMessage = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonOk = dialogView.findViewById<MaterialButton>(R.id.button_ok)

        textTitle.text = "Account created"
        textMessage.text = "Your CrabTrack account has been created. You can now log in."

        buttonOk.setOnClickListener {
            dialog.dismiss()
            // Navigate to Login Fragment
            findNavController().navigate(R.id.action_register_to_login)
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

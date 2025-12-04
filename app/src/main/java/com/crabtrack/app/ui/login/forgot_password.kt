package com.crabtrack.app.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.crabtrack.app.R
import com.crabtrack.app.databinding.FragmentForgotPasswordBinding
import com.crabtrack.app.presentation.auth.AuthViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    // Use shared AuthViewModel across auth screens
    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
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
                authViewModel.passwordResetUiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.forgotGoLogin.setOnClickListener {
            authViewModel.resetPasswordResetState()
            findNavController().navigate(R.id.action_forgot_to_login)
        }

        binding.buttonCode.setOnClickListener {
            val email = binding.editTextUsername.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Send password reset via ViewModel
            authViewModel.sendPasswordReset(email)
        }
    }

    private fun updateUi(state: com.crabtrack.app.presentation.auth.PasswordResetUiState) {
        // Show/hide loading indicator (if you have one in layout)
        // binding.progressIndicator?.isVisible = state.isLoading
        binding.buttonCode.isEnabled = !state.isLoading

        // Handle error
        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            authViewModel.clearPasswordResetError()
        }

        // Handle success
        if (state.isSuccess) {
            val email = binding.editTextUsername.text.toString().trim()
            Toast.makeText(requireContext(), "Password reset email sent to $email", Toast.LENGTH_LONG).show()
            showSuccessDialog()
            authViewModel.resetPasswordResetState()
        }
    }

    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✅ Email Sent")
            .setMessage("Check your inbox (or spam folder) for the password reset link.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

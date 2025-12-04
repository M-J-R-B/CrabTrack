package com.crabtrack.app.ui.login

import android.os.Bundle
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
import com.crabtrack.app.databinding.FragmentLoginBinding
import com.crabtrack.app.presentation.auth.AuthViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Login fragment with simplified email-only authentication.
 *
 * MAJOR SIMPLIFICATION from previous version:
 * - Direct email/password login (no username→email lookup)
 * - Uses AuthViewModel (MVVM pattern)
 * - No direct Firebase calls
 * - Removed ~140 lines of complex lookup logic
 * - Navigation handled by MainActivity auth guard
 */
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Use shared AuthViewModel across auth screens
    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authViewModel.resetLoginState()

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginUiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun showLoginSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_success_profile, null)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.CrabTrack_AlertDialog
        )
            .setView(dialogView)
            .create()

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val textTitle = dialogView.findViewById<TextView>(R.id.text_title)
        val textMessage = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonOk = dialogView.findViewById<MaterialButton>(R.id.button_ok)

        textTitle.text = "Login Successful"
        textMessage.text = "Welcome to CrabTrack!"
        buttonOk.text = "Continue"

        buttonOk.setOnClickListener {
            dialog.dismiss()
            // Now YOU control navigation:
            findNavController().navigate(R.id.action_login_to_main)
        }

        dialog.show()
    }



    private fun setupClickListeners() {
        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextUsername.text.toString().trim()  // Note: ID is still "Username" but holds email
            val password = binding.textInputPassword.editText?.text.toString().trim()

            // Validate inputs
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Basic email validation
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Login via ViewModel (direct email/password - no username lookup!)
            authViewModel.login(email, password)
        }

        binding.buttonSignUp.setOnClickListener {
            authViewModel.resetLoginState()
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.textForgotPassword.setOnClickListener {
            authViewModel.resetLoginState()
            findNavController().navigate(R.id.action_login_to_forgotPassword)
        }
    }

    private fun updateUi(state: com.crabtrack.app.presentation.auth.LoginUiState) {
        binding.buttonLogin.isEnabled = !state.isLoading
        binding.buttonSignUp.isEnabled = !state.isLoading

        // Handle error
        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            authViewModel.clearLoginError()
        }

        // Handle success
        if (state.isSuccess) {
            showLoginSuccessDialog()
            authViewModel.resetLoginState()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

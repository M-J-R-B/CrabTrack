package com.crabtrack.app.ui.login

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.crabtrack.app.R
import com.crabtrack.app.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class User(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val password: String? = null
)

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("users")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textGoLogin.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }

        binding.buttonSignUp.setOnClickListener {
            val username = binding.editTextUsername.text.toString().trim()
            val email = binding.textInputEmail.editText?.text.toString().trim()
            val password = binding.textInputPassword.editText?.text.toString().trim()
            val confirmPassword = binding.textInputConfirmpassword.editText?.text.toString().trim()

            // ✅ Reset previous errors
            binding.textInputEmail.isErrorEnabled = false
            binding.textInputPassword.isErrorEnabled = false
            binding.textInputConfirmpassword.isErrorEnabled = false
            // Hide all error messages first
            binding.textPasswordError.visibility = View.GONE
            binding.textConfirmPasswordError.visibility = View.GONE

// Password length check
            if (password.length < 6) {
                binding.textPasswordError.text = "Password must be at least 6 characters long"
                binding.textPasswordError.visibility = View.VISIBLE
                return@setOnClickListener
            }

// Password confirmation check
            if (password != confirmPassword) {
                binding.textConfirmPasswordError.text = "Passwords do not match"
                binding.textConfirmPasswordError.visibility = View.VISIBLE
                return@setOnClickListener
            }


            // ✅ Empty field validation
            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Email format validation
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.textInputEmail.apply {
                    isErrorEnabled = true
                    error = "Please enter a valid email address"
                }
                return@setOnClickListener
            }

            // ✅ Gmail-only restriction
            val domain = email.substringAfterLast("@")
            if (!domain.equals("gmail.com", ignoreCase = true)) {
                binding.textInputEmail.apply {
                    isErrorEnabled = true
                    error = "Only Gmail addresses are allowed"
                }
                return@setOnClickListener
            }

            // ✅ Password confirmation check
            if (password != confirmPassword) {
                binding.textInputConfirmpassword.apply {
                    isErrorEnabled = true
                    error = "Passwords do not match"
                }
                return@setOnClickListener
            }

            // ✅ Minimum password length check (Firebase requires 6+)
            if (password.length < 6) {
                binding.textInputPassword.apply {
                    isErrorEnabled = true
                    error = "Password must be at least 6 characters long"
                }
                return@setOnClickListener
            }

            // ✅ Continue only if all validations pass
            database.orderByChild("username").equalTo(username)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            Toast.makeText(requireContext(), "Username already exists!", Toast.LENGTH_SHORT).show()
                        } else {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { authResult ->
                                    val userId = authResult.user?.uid ?: return@addOnSuccessListener
                                    val user = User(userId, username, email, password)
                                    database.child(userId).setValue(user)
                                        .addOnSuccessListener {
                                            Toast.makeText(requireContext(), "Registration successful!", Toast.LENGTH_SHORT).show()
                                            findNavController().navigate(R.id.action_register_to_login)
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(requireContext(), "Database error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(requireContext(), "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

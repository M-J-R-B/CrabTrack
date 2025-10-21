package com.crabtrack.app.ui.login

import android.os.Bundle
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
            val password = binding.editTextPassword.text.toString().trim()

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Step 1: Check if username already exists in database
            database.orderByChild("username").equalTo(username)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            Toast.makeText(requireContext(), "Username already exists!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Step 2: Create Firebase Auth account
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { authResult ->
                                    val userId = authResult.user?.uid ?: return@addOnSuccessListener

                                    // Step 3: Save user info to Realtime Database
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

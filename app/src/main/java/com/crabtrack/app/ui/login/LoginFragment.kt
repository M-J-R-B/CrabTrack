package com.crabtrack.app.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.crabtrack.app.R
import com.crabtrack.app.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("users")

        binding.buttonLogin.setOnClickListener {
            val username = binding.editTextUsername.text.toString().trim()
            val password = binding.textInputPassword.editText?.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Please enter username and password", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            // 🔹 Step 1: Find user’s email from username in Realtime DB
            database.orderByChild("username").equalTo(username)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!isAdded) return // prevent crash if fragment detached
                        if (snapshot.exists()) {
                            val userSnapshot = snapshot.children.first()
                            val email = userSnapshot.child("email").getValue(String::class.java)

                            if (email != null) {
                                // 🔹 Step 2: Sign in using Firebase Authentication
                                signInWithFirebase(email, password)
                            } else {
                                Toast.makeText(requireContext(), "User email not found", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(requireContext(), "Account not found", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (!isAdded) return
                        Toast.makeText(requireContext(), "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        binding.buttonSignUp.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.action_login_to_register)
        }

        binding.textForgotPassword.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.action_login_to_forgotPassword)
        }
    }

    private fun signInWithFirebase(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // ✅ Prevent crash if fragment is already detached
                if (!isAdded) return@addOnSuccessListener

                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()

                // Navigate only if still attached
                if (isAdded) {
                    findNavController().navigate(R.id.action_login_to_main)
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

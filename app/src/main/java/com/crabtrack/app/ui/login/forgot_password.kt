package com.crabtrack.app.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.crabtrack.app.databinding.FragmentForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔹 Step 1: Send password reset email
        binding.buttonCode.setOnClickListener {
            val email = binding.editTextUsername.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendPasswordResetEmail(email)
        }

        // 🔹 Step 2: User confirms new password manually in your app
        binding.buttonConfirm.setOnClickListener {
            val email = binding.editTextUsername.text.toString().trim()
            val newPassword = binding.editTextCode.text.toString().trim()

            if (email.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔹 Update password in Firebase Authentication
            auth.fetchSignInMethodsForEmail(email).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val signInMethods = task.result?.signInMethods
                    if (signInMethods.isNullOrEmpty()) {
                        Toast.makeText(requireContext(), "Email not found.", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }

                    // Direct update only works for logged-in users; for demo, update DB directly
                    updatePasswordInDatabase(email, newPassword)
                } else {
                    Toast.makeText(requireContext(), "Error verifying email.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                showSuccessDialog()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 🔹 Update Realtime Database with the new password entered in the app
    private fun updatePasswordInDatabase(email: String, newPassword: String) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        usersRef.orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            userSnapshot.ref.child("password").setValue(newPassword)
                                .addOnSuccessListener {
                                    Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(requireContext(), "Failed to update DB: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(requireContext(), "Email not found in database.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "DB error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
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

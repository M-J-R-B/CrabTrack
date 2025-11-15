package com.crabtrack.app.ui.login

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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

    private val TAG = "LoginFragment"
    private var queryTimeoutHandler: Handler? = null
    private var queryTimedOut = false

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

        Log.d(TAG, "LoginFragment initialized")
        Log.d(TAG, "Firebase Database URL: ${FirebaseDatabase.getInstance().reference.toString()}")

        binding.buttonLogin.setOnClickListener {
            Log.d(TAG, "Login button clicked")
            val username = binding.editTextUsername.text.toString().trim()
            val password = binding.textInputPassword.editText?.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Log.w(TAG, "Login failed: Empty username or password")
                if (isAdded) {
                    Toast.makeText(requireContext(), "Please enter username and password", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            // Check network connectivity
            if (!isNetworkAvailable()) {
                Log.e(TAG, "Login failed: No network connection")
                if (isAdded) {
                    Toast.makeText(requireContext(), "No internet connection. Please check your network.", Toast.LENGTH_LONG).show()
                }
                return@setOnClickListener
            }

            Log.i(TAG, "Network available, checking Firebase connection...")

            // Show loading state
            if (isAdded) {
                Toast.makeText(requireContext(), "Connecting to server...", Toast.LENGTH_SHORT).show()
            }

            performLogin(username, password)
        }

        binding.buttonSignUp.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.action_login_to_register)
        }

        binding.textForgotPassword.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.action_login_to_forgotPassword)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                Log.d(TAG, "Connected via WiFi")
                true
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                Log.d(TAG, "Connected via Mobile Data")
                true
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                Log.d(TAG, "Connected via Ethernet")
                true
            }
            else -> false
        }
    }

    private fun performLogin(username: String, password: String) {
        Log.i(TAG, "Attempting login for username: $username")
        queryTimedOut = false

        // Set timeout for Firebase query (10 seconds)
        queryTimeoutHandler = Handler(Looper.getMainLooper())
        queryTimeoutHandler?.postDelayed({
            if (!queryTimedOut) {
                queryTimedOut = true
                Log.e(TAG, "Firebase query timeout after 10 seconds")
                if (isAdded) {
                    Toast.makeText(requireContext(), "Connection timeout. Please check your internet connection and try again.", Toast.LENGTH_LONG).show()
                }
            }
        }, 10000)

        try {
            Log.d(TAG, "Querying Firebase Database for username: $username")

            // 🔹 Step 1: Find user's email from username in Realtime DB
            val query = database.orderByChild("username").equalTo(username)
            Log.d(TAG, "Query created: ${query.ref.toString()}")

            query.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        queryTimeoutHandler?.removeCallbacksAndMessages(null)

                        if (queryTimedOut) {
                            Log.w(TAG, "Query completed but already timed out")
                            return
                        }

                        Log.d(TAG, "onDataChange called, snapshot exists: ${snapshot.exists()}, children count: ${snapshot.childrenCount}")

                        if (!isAdded) {
                            Log.w(TAG, "Fragment not added, aborting login")
                            return // prevent crash if fragment detached
                        }

                        if (snapshot.exists()) {
                            Log.i(TAG, "User found in database")
                            val userSnapshot = snapshot.children.first()
                            val email = userSnapshot.child("email").getValue(String::class.java)
                            Log.d(TAG, "Email retrieved: ${email?.let { "***@${it.substringAfter("@")}" } ?: "null"}")

                            if (email != null) {
                                // 🔹 Step 2: Sign in using Firebase Authentication
                                Log.i(TAG, "Proceeding to Firebase Authentication")
                                signInWithFirebase(email, password)
                            } else {
                                Log.e(TAG, "Email field is null in database")
                                Toast.makeText(requireContext(), "User email not found", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.w(TAG, "No user found with username: $username")
                            Toast.makeText(requireContext(), "Account not found", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        queryTimeoutHandler?.removeCallbacksAndMessages(null)

                        if (queryTimedOut) {
                            Log.w(TAG, "Query cancelled but already timed out")
                            return
                        }

                        Log.e(TAG, "Database query cancelled - Error: ${error.code}, Message: ${error.message}, Details: ${error.details}")

                        if (!isAdded) return
                        Toast.makeText(requireContext(), "Database error: ${error.message}\nPlease check your internet connection.", Toast.LENGTH_LONG).show()
                    }
                })

            Log.d(TAG, "Listener attached to query")
        } catch (e: Exception) {
            queryTimeoutHandler?.removeCallbacksAndMessages(null)
            Log.e(TAG, "Exception during login attempt: ${e.message}", e)
            if (isAdded) {
                Toast.makeText(requireContext(), "Login error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun signInWithFirebase(email: String, password: String) {
        Log.i(TAG, "Attempting Firebase Authentication")
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                Log.i(TAG, "Firebase Authentication successful")
                // ✅ Prevent crash if fragment is already detached
                if (!isAdded) return@addOnSuccessListener

                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()

                // Navigate only if still attached
                if (isAdded) {
                    Log.d(TAG, "Navigating to main screen")
                    findNavController().navigate(R.id.action_login_to_main)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firebase Authentication failed: ${e.message}", e)
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        queryTimeoutHandler?.removeCallbacksAndMessages(null)
        _binding = null
    }
}

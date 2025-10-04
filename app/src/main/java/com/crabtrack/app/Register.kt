package com.crabtrack.app.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.crabtrack.app.R
import com.crabtrack.app.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // handle click on "Log In" text
        binding.textGoLogin.setOnClickListener {
            // Option 1: use explicit action if you defined it in nav_graph.xml
            findNavController().navigate(R.id.action_register_to_login)

            // Option 2: just go back in the stack (cleaner if coming from login)
            // findNavController().popBackStack()
        }

        binding.buttonSignUp.setOnClickListener {
            // After sign-up, navigate back to login
            findNavController().navigate(R.id.action_register_to_login)
            // OR: findNavController().popBackStack(R.id.loginFragment, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

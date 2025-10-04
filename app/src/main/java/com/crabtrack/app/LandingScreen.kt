package com.crabtrack.app

import android.icu.text.CaseMap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.crabtrack.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LandingScreen : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_landing_screen, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.crabTrack)
        val logoImage = view.findViewById<ImageView>(R.id.crabTrackLogo)

        listOf<View>(logoImage, title).forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 24f
            v.scaleX = 0.96f
            v.scaleY = 0.96f

            v.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(700L)
                .setStartDelay(150L + i * 200L) // small stagger: image first, then text
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .start()
        }

        val logoText = view.findViewById<TextView>(R.id.crabTrack)

        // Start invisible and slightly lower/smaller
        title.alpha = 0f
        title.translationY = 24f
        title.scaleX = 0.96f
        title.scaleY = 0.96f

        // Animate in
        title.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700L)
            .setStartDelay(150L)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()

        // Auto navigate after ~5s
        viewLifecycleOwner.lifecycleScope.launch {
            delay(5_000)
            // uses the action you'll add in the nav graph below
            findNavController().navigate(R.id.action_landingscreen_to_login)
        }

        // (Optional) tap to skip immediately
        view.setOnClickListener {
            findNavController().navigate(R.id.action_landingscreen_to_login)
        }
    }
}

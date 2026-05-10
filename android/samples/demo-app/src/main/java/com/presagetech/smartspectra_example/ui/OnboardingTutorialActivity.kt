// OnboardingTutorialActivity.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.presagetech.smartspectra_example.R
import com.presagetech.smartspectra_example.utils.PreferencesUtils

internal class OnboardingTutorialActivity : AppCompatActivity() {

    private lateinit var tutorialImageView: ImageView
    private lateinit var tutorialDescriptionTextView: TextView
    private lateinit var navigationHintTextView: TextView

    private var counter: Int = 0
    private lateinit var tutorialImages: List<Int>
    private lateinit var descriptions: Array<String>
    private lateinit var navigationDots: Array<ImageView?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_tutorial)

        tutorialImageView = findViewById(R.id.tutorial_image)
        tutorialDescriptionTextView = findViewById(R.id.tutorial_description)
        navigationHintTextView = findViewById(R.id.navigation_hint)


        // Load the tutorial images and descriptions
        tutorialImages = listOf (
            R.drawable.tutorial_image1,
            R.drawable.tutorial_image2,
            R.drawable.tutorial_image3,
            R.drawable.tutorial_image4,
            R.drawable.tutorial_image5,
            R.drawable.tutorial_image6,
            R.drawable.tutorial_image7,
        )
        descriptions = resources.getStringArray(R.array.tutorial_descriptions)

        //initialize navigation dots
        initializeNavigationDots()

        // Initialize the first tutorial step
        updateTutorialStep()

        // Set up click listener to go to the next tutorial step
        tutorialImageView.setOnClickListener {
            counter++
            if (counter < tutorialImages.size) {
                updateTutorialStep()
            } else {
                // Finish the tutorial and close the activity
                PreferencesUtils.saveBoolean(this, PreferencesUtils.ONBOARDING_TUTORIAL_KEY, true)
                finish()

            }
        }
    }

    /**
     * Creates and attaches the progress dots shown at the bottom of the
     * tutorial screen.
     */
    private fun initializeNavigationDots() {
        val dotsLayout = findViewById<LinearLayout>(R.id.navigation_dots)
        navigationDots = arrayOfNulls(tutorialImages.size)

        for (i in navigationDots.indices) {
            navigationDots[i] = ImageView(this).apply {
                setImageResource(R.drawable.navigation_dot_inactive)  // Inactive drawable
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)  // Margin between dots
                }
            }
            dotsLayout.addView(navigationDots[i])
        }
    }

    /**
     * Updates which progress dot is highlighted based on the current tutorial
     * [currentPosition].
     */
    private fun updateNavigationDotColors(currentPosition: Int) {
        // Reset all dots to inactive color
        for (i in navigationDots.indices) {
            navigationDots[i]?.setImageResource(R.drawable.navigation_dot_inactive)
        }
        // Highlight the current dot
        navigationDots[currentPosition]?.setImageResource(R.drawable.navigation_dot_active)
    }

    /**
     * Advances the tutorial to the step referenced by [counter] and updates the
     * UI accordingly.
     */
    private fun updateTutorialStep() {
        tutorialImageView.setImageResource(tutorialImages[counter])
        tutorialDescriptionTextView.text = descriptions[counter]
        tutorialImageView.contentDescription = descriptions[counter]

        if (counter == tutorialImages.size -1) {
            navigationHintTextView.text = resources.getText(R.string.navigation_hint_end)
        } else {
            navigationHintTextView.text = resources.getText(R.string.navigation_hint_start)
        }

        updateNavigationDotColors(counter)
    }
}

// ConfigurationErrorFragment.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.presagetech.smartspectra_example.R

internal class ConfigurationErrorFragment : Fragment() {

    private var errorMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        errorMessage = arguments?.getString(ARG_ERROR_MESSAGE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_configuration_error_layout, container, false)
        val titleText = view.findViewById<TextView>(R.id.text_configuration_error_title)
        val messageText = view.findViewById<TextView>(R.id.text_configuration_error_message)

        titleText.setText(R.string.configuration_error_title)
        messageText.text = errorMessage ?: getString(R.string.configuration_error_default_message)
        return view
    }

    companion object {
        private const val ARG_ERROR_MESSAGE = "error_message"

        fun newInstance(errorMessage: String): ConfigurationErrorFragment {
            return ConfigurationErrorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ERROR_MESSAGE, errorMessage)
                }
            }
        }
    }
}

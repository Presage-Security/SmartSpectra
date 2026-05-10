// InsightsChatBottomSheet.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.headless

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra_example.R

class InsightsChatBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputField: TextInputEditText
    private lateinit var sendButton: MaterialButton

    private val adapter = InsightsChatAdapter()
    private var pendingRequestId: Int? = null
    private val sdk by lazy { SmartSpectraSdk.shared }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_insights_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.chat_recycler_view)
        inputField = view.findViewById(R.id.chat_input_field)
        sendButton = view.findViewById(R.id.chat_send_button)

        // Expand sheet to half-screen by default
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
            halfExpandedRatio = 0.6f
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext()).also {
            it.stackFromEnd = true
        }
        recyclerView.adapter = adapter

        sendButton.setOnClickListener { sendMessage() }
        inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        sdk.insight.observe(viewLifecycleOwner) { insight ->
            if (insight == null) return@observe
            if (insight.requestId != pendingRequestId) return@observe
            val text = when {
                insight.hasAnalysis() -> insight.analysis
                else -> getString(R.string.insights_chat_error)
            }
            adapter.replaceLastAiMessage(ChatMessage(text, isUser = false))
            pendingRequestId = null
            sendButton.isEnabled = true
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }

        adapter.addMessage(
            ChatMessage(
                text = getString(R.string.insights_chat_welcome),
                isUser = false,
            )
        )
    }

    private fun sendMessage() {
        val text = inputField.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        inputField.text?.clear()
        adapter.addMessage(ChatMessage(text, isUser = true))
        adapter.addMessage(ChatMessage("", isUser = false, isLoading = true))
        recyclerView.scrollToPosition(adapter.itemCount - 1)

        sendButton.isEnabled = false
        pendingRequestId = runCatching { sdk.requestInsight(text) }
            .onFailure {
                adapter.replaceLastAiMessage(
                    ChatMessage(getString(R.string.insights_chat_error), isUser = false)
                )
                sendButton.isEnabled = true
            }
            .getOrNull()
    }

    companion object {
        const val TAG = "InsightsChatBottomSheet"
    }
}

// InsightsChatAdapter.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.headless

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.presagetech.smartspectra_example.R

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false,
)

class InsightsChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun replaceLastAiMessage(message: ChatMessage) {
        val idx = messages.indexOfLast { !it.isUser }
        if (idx >= 0) {
            messages[idx] = message
            notifyItemChanged(idx)
        } else {
            addMessage(message)
        }
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isUser) TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserMessageViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AiMessageViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(msg)
            is AiMessageViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount() = messages.size

    class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.chat_message_text)
        fun bind(msg: ChatMessage) {
            textView.text = msg.text
        }
    }

    class AiMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.chat_message_text)
        fun bind(msg: ChatMessage) {
            textView.text = if (msg.isLoading) "…" else msg.text
        }
    }
}

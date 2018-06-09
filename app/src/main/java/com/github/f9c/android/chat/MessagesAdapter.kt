package com.github.f9c.android.chat

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.github.f9c.android.R

class MessagesAdapter(private var _messages: MutableList<Message>) : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {

    class ViewHolder(val textView: ConstraintLayout) : RecyclerView.ViewHolder(textView)

    var messages: MutableList<Message>
        get() = _messages
        set(value) {
            _messages = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val contactLayout = LayoutInflater.from(parent.context)
                .inflate(R.layout.message, parent, false) as ConstraintLayout
        return ViewHolder(contactLayout)
    }

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder.textView.getViewById(R.id.messageText) as TextView).text = messages[position].text
    }

}
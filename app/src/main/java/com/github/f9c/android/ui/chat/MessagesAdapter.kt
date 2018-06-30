package com.github.f9c.android.ui.chat

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.github.f9c.android.R
import com.github.f9c.android.message.Message

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
        val textView = holder.textView.getViewById(R.id.messageText) as TextView
        val message = messages[position]
        textView.text = message.text
        if (message.incoming) {
            textView.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
        } else {
            textView.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_END
        }
    }

}
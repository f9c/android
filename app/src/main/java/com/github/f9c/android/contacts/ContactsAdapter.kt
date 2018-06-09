package com.github.f9c.android.contacts

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.github.f9c.android.R

class ContactsAdapter(private var _contacts: MutableList<Contact>) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    class ViewHolder(val textView: ConstraintLayout) : RecyclerView.ViewHolder(textView)

    var contacts: MutableList<Contact>
        get() = _contacts
        set(value) {
            _contacts = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val contactLayout = LayoutInflater.from(parent.context)
                .inflate(R.layout.contact, parent, false) as ConstraintLayout
        return ViewHolder(contactLayout)
    }

    override fun getItemCount() = contacts.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder.textView.getViewById(R.id.contactAlias) as TextView).text = contacts.get(position).alias
    }

}
package com.github.f9c.android.ui.contacts

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import com.github.f9c.android.R
import com.github.f9c.android.contact.Contact
import com.github.f9c.android.ui.util.ProfileIcon


class ContactsAdapter(private var _contacts: MutableList<Contact>) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    var position: Int = 0

    class ViewHolder(val textView: ConstraintLayout) : RecyclerView.ViewHolder(textView), View.OnCreateContextMenuListener {

        init {
            textView.setOnCreateContextMenuListener(this)
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
            menu.add(Menu.NONE, R.id.menu_contact_update_profile,
                    Menu.NONE, R.string.update_contact_profile)
            menu.add(Menu.NONE, R.id.menu_contact_remove,
                    Menu.NONE, R.string.remove_contact)
        }

    }

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
        holder.itemView.setOnLongClickListener {
            this@ContactsAdapter.position = holder.adapterPosition
            false
        }

        val contact = contacts[position]
        val aliasTextView = holder.textView.getViewById(R.id.contactAlias) as TextView
        aliasTextView.text = contact.alias
        (holder.textView.getViewById(R.id.contactAliasStatusText) as TextView).text = contact.statusText

        // TODO: Better way of centering the alias depending on the status text being available or
        // not
        val p = aliasTextView.layoutParams as ViewGroup.MarginLayoutParams
        if (contact.statusText == null || contact.statusText == "") {
            p.topMargin = 17
        } else {
            p.topMargin = 0
        }

        val profileIcon = contact.profileIcon
        if (profileIcon != null) {
            (holder.textView.getViewById(R.id.contactProfileImage) as ImageView).setImageDrawable(ProfileIcon.roundedIcon(profileIcon))
        } else {
            (holder.textView.getViewById(R.id.contactProfileImage) as ImageView).setImageResource(R.mipmap.ic_launcher)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.itemView.setOnLongClickListener(null)
        super.onViewRecycled(holder)
    }

}
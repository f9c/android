package com.github.f9c.android.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.github.f9c.android.contacts.Contact
import com.github.f9c.android.util.DbHelper
import com.github.f9c.android.R
import com.github.f9c.android.websocket.WebSocketService
import kotlinx.android.synthetic.main.chat.*

class Chat : AppCompatActivity() {
    private var webSocketService: WebSocketService? = null
    private var contact: Contact? = null
    private var dbHelper: DbHelper = DbHelper(this)
    private var messagesAdapter: MessagesAdapter? = null
    private var recyclerView: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat)

        val extras = intent.extras
        val contactId = extras.getString("contact")
        contact = dbHelper.loadContact(contactId)

        chatToolbar.title = contact!!.alias

        recyclerView = findViewById<RecyclerView>(R.id.chatHistory).apply {
            setHasFixedSize(true)

            layoutManager = LinearLayoutManager(this@Chat)
            messagesAdapter = MessagesAdapter(dbHelper.loadMessages(contact!!.rowId))
            adapter = messagesAdapter

        }
        scrollToEnd()

        sendChat.setOnClickListener { _ ->
            val msg = chatMessageView.text
            webSocketService!!.sendMessage(contact!!, msg.toString())

            dbHelper.insertSentMessage(contact!!, msg.toString())
            refreshMessages()

            chatMessageView.text.clear()
            val view = currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    private fun refreshMessages() {
        messagesAdapter!!.messages = dbHelper.loadMessages(contact!!.rowId)
        scrollToEnd()
    }

    private fun scrollToEnd() {
        recyclerView!!.scrollToPosition(messagesAdapter!!.messages.size - 1)
    }

    override fun onDestroy() {
        unbindService(mConnection)
        super.onDestroy()
    }

    override fun onStart() {
        val mIntent = Intent(this, WebSocketService::class.java)
        bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE)
        super.onStart()
    }


    private val mConnection = object : ServiceConnection {

        override fun onBindingDied(name: ComponentName?) {

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            webSocketService = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val mLocalBinder = service as WebSocketService.LocalBinder
            webSocketService = mLocalBinder.getServerInstance()
        }

    }
}
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

        // TODO: implement
        sendChat.setOnClickListener { _ ->
            val msg = chatMessageView.text
            webSocketService!!.sendMessage(contact!!, msg.toString())
            dbHelper.insertMessage()

            chatMessageView.text.clear()
            val view = currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    override fun onStart() {
        val mIntent = Intent(this, WebSocketService::class.java)
        bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE)
        super.onStart()
    }


    val mConnection = object : ServiceConnection {

        override fun onBindingDied(name: ComponentName?) {

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Toast.makeText(this@Chat, "Service is disconnected", Toast.LENGTH_LONG).show();
            webSocketService = null;
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Toast.makeText(this@Chat, "Service is connected", Toast.LENGTH_LONG).show();
            val mLocalBinder = service as WebSocketService.LocalBinder;
            webSocketService = mLocalBinder.getServerInstance();
        }

    }
}
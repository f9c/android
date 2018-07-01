package com.github.f9c.android.ui.chat

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.inputmethod.InputMethodManager
import com.github.f9c.android.contact.Contact
import com.github.f9c.android.util.DbHelper
import com.github.f9c.android.R

import com.github.f9c.android.websocket.WebSocketService
import com.github.f9c.android.websocket.WebSocketServiceConstants
import kotlinx.android.synthetic.main.chat.*

class ChatActivity : AppCompatActivity() {
    private var webSocketService: WebSocketService? = null
    private var contact: Contact? = null
    private var dbHelper: DbHelper = DbHelper(this)
    private var messagesAdapter: MessagesAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat)

        val extras = intent.extras
        val contactId = extras.getString("contact")
        contact = dbHelper.loadContact(contactId)

        chatToolbar.title = contact!!.alias

        recyclerView = findViewById<RecyclerView>(R.id.chatHistory).apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
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

        broadcastReceiver = Receiver()
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

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver!!,
                IntentFilter(WebSocketServiceConstants.MESSAGE_RECEIVED))

        super.onStart()
    }

    private inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent!!.extras[WebSocketServiceConstants.CONTACT] == contact!!.publicKey) {
                refreshMessages();
            }
        }

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
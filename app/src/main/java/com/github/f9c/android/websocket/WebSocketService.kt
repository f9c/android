package com.github.f9c.android.websocket

import android.app.Service
import android.content.Intent
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast
import com.github.f9c.Client
import com.github.f9c.android.util.DbHelper
import com.github.f9c.android.util.RsaKeyToStringConverter
import com.github.f9c.android.contact.Contact
import com.github.f9c.android.profile.Profile
import com.github.f9c.android.util.Base64
import com.github.f9c.client.ClientKeys
import com.github.f9c.client.ClientMessageListener
import com.github.f9c.client.datamessage.AbstractDataMessage
import com.github.f9c.client.datamessage.ProfileDataMessage
import com.github.f9c.client.datamessage.RequestProfileMessage
import com.github.f9c.client.datamessage.TextMessage
import com.neovisionaries.ws.client.WebSocketException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object WebSocketServiceConstants {
    val MESSAGE_RECEIVED = "CHAT_MESSAGE_RECEIVED"
    val CONTACT = "CONTACT"
}

class WebSocketService : Service() {

    // TODO: Move to settings service
    private val ALIAS = "alias"
    private val SERVER = "server"
    private val PUBLIC_KEY = "PUBLIC_KEY"
    private val PRIVATE_KEY = "PRIVATE_KEY"

    private lateinit var mServiceLooper: Looper

    private lateinit var mServiceHandler: ServiceHandler

    private var client: Client? = null
    private var clientKeys: ClientKeys? = null

    private val dbHelper: DbHelper = DbHelper(this)
    private var thread: HandlerThread? = null
    private var broadcaster: LocalBroadcastManager? = null
    private var server: String? = null

    inner class LocalBinder : Binder() {

        fun getServerInstance(): WebSocketService {
            return this@WebSocketService
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return LocalBinder()
    }

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            if ("startClient" == msg.obj) {
                if (client != null) {
                    client!!.close()
                }
                val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val publicKeyString = preferences.getString(PUBLIC_KEY, null)
                val privateKeyString = preferences.getString(PRIVATE_KEY, null)

                val keyPair = loadKeyPair(publicKeyString, privateKeyString)
                clientKeys = ClientKeys(keyPair.public, keyPair.private)

                val clientMessageListener = MessageListener()
                server = preferences.getString(SERVER, "")
                if ("" != server) {
                    try {
                        client = Client(server, 443, clientKeys, clientMessageListener)
                    } catch (e: WebSocketException) {
                        Log.e("websocket", "Connection to $server failed.", e)
                        Toast.makeText(this@WebSocketService,
                                "Connection to $server failed: ${e.message}",
                                Toast.LENGTH_LONG).show()
                    }
                }
            } else if (msg.obj is SendTextMessage) {
                val stm = msg.obj as SendTextMessage
                val textMessage = TextMessage(stm.msg, clientKeys!!.publicKey, server)
                client!!.sendDataMessage(loadPublicKey(stm.contact.publicKey), textMessage)
            } else if (msg.obj is SendMessage) {
                val sendMessage = msg.obj as SendMessage
                client!!.sendDataMessage(sendMessage.recipientPublicKey, sendMessage.msg)
            }
        }
    }

    // TODO: Duplicated from ContactsActivity. Remove one.
    private fun loadKeyPair(publicKeyString: String, privateKeyString: String): KeyPair {
        val publicKey = Base64.decode(publicKeyString)
        val privateKey = Base64.decode(privateKeyString)
        val keyFactory = KeyFactory.getInstance("RSA")

        return KeyPair(keyFactory.generatePublic(X509EncodedKeySpec(publicKey)),
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKey)))
    }

    private fun loadPublicKey(publicKeyString: String): PublicKey {
        val publicKey = com.github.f9c.android.util.Base64.decode(publicKeyString)
        val keyFactory = KeyFactory.getInstance("RSA")

        return keyFactory.generatePublic(X509EncodedKeySpec(publicKey))
    }

    override fun onCreate() {
        broadcaster = LocalBroadcastManager.getInstance(this)

        thread = HandlerThread("WebSocketService",
                Process.THREAD_PRIORITY_BACKGROUND)
        thread!!.start()
        mServiceLooper = thread!!.looper
        mServiceHandler = ServiceHandler(thread!!.looper)

    }

    fun openConnection() {
        val msg = mServiceHandler.obtainMessage()
        msg.obj = "startClient"
        mServiceHandler.sendMessage(msg)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        thread!!.quitSafely()
    }

    fun sendMessage(contact: Contact, msg: String) {
        val msgObj = mServiceHandler.obtainMessage()
        msgObj.obj = SendTextMessage(msg, contact)
        mServiceHandler.sendMessage(msgObj)
    }

    fun notifyUi(publicKey: String) {
        val intent = Intent(WebSocketServiceConstants.MESSAGE_RECEIVED)
        intent.putExtra(WebSocketServiceConstants.CONTACT, publicKey)
        broadcaster!!.sendBroadcast(intent)
    }

    fun requestProfileData(server: String, publicKey: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val alias = preferences.getString(ALIAS, null)

        // TODO: Fill status text
        val profile = Profile(applicationContext)
        val requestProfileMessage = RequestProfileMessage(alias, "",
                profile.profileImage(),
                clientKeys!!.publicKey, profile.server())
        sendMessage(requestProfileMessage, server, loadPublicKey(publicKey))
    }

    private fun sendMessage(message: AbstractDataMessage, server: String, publicKey: PublicKey) {
        val msgObj = mServiceHandler.obtainMessage()
        msgObj.obj = SendMessage(
                message,
                server,
                publicKey)
        mServiceHandler.sendMessage(msgObj)
    }


    private inner class MessageListener : ClientMessageListener {
        override fun handleDataMessage(msg: AbstractDataMessage) {
            val publicKey = RsaKeyToStringConverter.encodePublicKey(msg.senderPublicKey)
            if (msg is TextMessage) {
                if (dbHelper.contactExistsForPublicKey(publicKey)) {
                    dbHelper.insertMessage(msg)
                    // TODO: Refresh display

                    notifyUi(publicKey)
                    Toast.makeText(this@WebSocketService, "Message received.", Toast.LENGTH_SHORT).show()
                } else {
                    // TODO: Ask user if we should create an anonymous contact and request profile?
                    Log.e("DbHelper", "No contact found in database.")
                }
            } else if (msg is RequestProfileMessage) {
                // TODO: Only send profile data to existing contacts or if user confirms sharing data
                val profile = Profile(applicationContext)
                sendMessage(ProfileDataMessage(profile.alias(), "", profile.profileImage(), loadPublicKey(profile.publicKey())), msg.server, msg.senderPublicKey)
            } else if (msg is ProfileDataMessage) {
                if (dbHelper.contactExistsForPublicKey(publicKey)) {
                    dbHelper.updateContact(publicKey, msg.alias, msg.statusText, msg.profileImage)
                }
                // TODO: repaint
            } else {
                Log.e("MSG", "Got unexpected message: " + msg.javaClass)
            }
        }

        override fun handleError(t: Throwable) {
            Log.e("MSG", "Unexpected Exception ", t)
            Toast.makeText(this@WebSocketService, "Unexpected Error: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class SendTextMessage(val msg: String, val contact: Contact)
    private inner class SendMessage(val msg: AbstractDataMessage, val server: String, val recipientPublicKey: PublicKey)
}


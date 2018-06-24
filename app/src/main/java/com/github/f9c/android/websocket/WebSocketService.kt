package com.github.f9c.android.websocket

import android.app.Service
import android.content.Intent
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.github.f9c.Client
import com.github.f9c.android.util.DbHelper
import com.github.f9c.android.util.RsaKeyToStringConverter
import com.github.f9c.android.contacts.Contact
import com.github.f9c.client.ClientKeys
import com.github.f9c.client.ClientMessageListener
import com.github.f9c.client.datamessage.AbstractDataMessage
import com.github.f9c.client.datamessage.TextMessage
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object WebSocketServiceConstants {
    public val MESSAGE_RECEIVED = "CHAT_MESSAGE_RECEIVED"
    public val CONTACT = "CONTACT"
}
class WebSocketService : Service() {

    private val PUBLIC_KEY = "PUBLIC_KEY"
    private val PRIVATE_KEY = "PRIVATE_KEY"

    private lateinit var mServiceLooper: Looper

    private lateinit var mServiceHandler: ServiceHandler

    private var client: Client? = null
    private var clientKeys: ClientKeys? = null

    private val dbHelper: DbHelper = DbHelper(this)
    private var thread: HandlerThread? = null
    private var broadcaster: LocalBroadcastManager? = null

    inner class LocalBinder : Binder() {

        fun getServerInstance() : WebSocketService {
            return this@WebSocketService
        }
    }

    override fun onBind(intent: Intent): IBinder? {
         return LocalBinder()
    }

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            if ("startClient" == msg.obj) {
                val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val publicKeyString = preferences.getString(PUBLIC_KEY, null)
                val privateKeyString = preferences.getString(PRIVATE_KEY, null)

                val keyPair = loadKeyPair(publicKeyString, privateKeyString)
                clientKeys = ClientKeys(keyPair.public, keyPair.private)

                val clientMessageListener = MessageListener();
                // TODO: get server data from configuration
                client = Client("195.201.46.160", 8443, clientKeys, clientMessageListener);
            } else if (msg.obj is SendTextMessage) {
                val stm = msg.obj as SendTextMessage
                val textMessage = TextMessage(stm.msg, clientKeys!!.publicKey)
                client!!.sendDataMessage(loadPublicKey(stm.contact.publicKey), textMessage)
            }
        }
    }

    // TODO: Duplicated from Contacts. Remove one.
    private fun loadKeyPair(publicKeyString: String?, privateKeyString: String?): KeyPair {
        val publicKey = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val privateKey = Base64.decode(privateKeyString, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("RSA")

        return KeyPair(keyFactory.generatePublic(X509EncodedKeySpec(publicKey)),
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKey)))
    }

    private fun loadPublicKey(publicKeyString: String): PublicKey {
        val publicKey = Base64.decode(publicKeyString, Base64.NO_WRAP)
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

        val msg = mServiceHandler.obtainMessage()
        msg.obj = "startClient"
        mServiceHandler.sendMessage(msg)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()

        return START_STICKY
    }

    override fun onDestroy() {
        thread!!.quitSafely()
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show()
    }

    fun sendMessage(contact: Contact, msg: String) {
        val msgObj = mServiceHandler.obtainMessage()
        msgObj.obj = SendTextMessage(msg, contact)
        mServiceHandler.sendMessage(msgObj)
    }

    fun notifyUi(publicKey : String) {
        val intent = Intent(WebSocketServiceConstants.MESSAGE_RECEIVED)
        intent.putExtra(WebSocketServiceConstants.CONTACT, publicKey)
        broadcaster!!.sendBroadcast(intent)
    }

    private inner class MessageListener : ClientMessageListener {
        override fun handleDataMessage(msg: AbstractDataMessage) {
            if (msg is TextMessage) {
                val publicKey = RsaKeyToStringConverter.encodePublicKey(msg.senderPublicKey)
                if (dbHelper.contactExistsForPublicKey(publicKey)) {
                    dbHelper.insertMessage(msg)
                    // TODO: Refresh display

                    notifyUi(publicKey)
                    Toast.makeText(this@WebSocketService, "Message received.", Toast.LENGTH_SHORT).show()
                } else {
                    // TODO: Ask user if we should create an anonymous contact and request profile?
                    Log.e("DbHelper", "No contact found in database.")
                }
            } else {
                Log.e("MSG", "Got unexpected message: " + msg.javaClass)
            }
        }
    }

    private inner class SendTextMessage(val msg: String, val contact: Contact)
}


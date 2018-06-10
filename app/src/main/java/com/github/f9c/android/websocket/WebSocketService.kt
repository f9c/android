package com.github.f9c.android.websocket

import android.app.Service
import android.content.Intent
import android.os.*
import android.preference.PreferenceManager
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


class WebSocketService : Service() {

    private val PUBLIC_KEY = "PUBLIC_KEY"
    private val PRIVATE_KEY = "PRIVATE_KEY"

    private lateinit var mServiceLooper: Looper

    private lateinit var mServiceHandler: ServiceHandler

    private var client: Client? = null
    private var clientKeys: ClientKeys? = null

    private val dbHelper: DbHelper = DbHelper(this)
    private var thread: HandlerThread? = null

    public inner class LocalBinder : Binder() {

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
                client = Client("195.201.46.160", 443, clientKeys, clientMessageListener);
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
        client!!.sendDataMessage(loadPublicKey(contact.publicKey), TextMessage(msg, clientKeys!!.publicKey))
    }

    private inner class MessageListener : ClientMessageListener {
        override fun handleDataMessage(msg: AbstractDataMessage) {
            if (msg is TextMessage) {
                if (dbHelper.contactExistsForPublicKey(RsaKeyToStringConverter.encodePublicKey(msg.senderPublicKey))) {
                    dbHelper.insertMessage(msg)
                    // TODO: Refresh display
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

}


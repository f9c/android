package com.github.f9c.android.ui.contacts

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.github.f9c.android.R
import android.content.*
import android.os.IBinder
import android.util.Log
import android.widget.*
import com.github.f9c.android.util.Base64
import com.github.f9c.android.util.DbHelper
import com.github.f9c.android.websocket.WebSocketService
import java.security.KeyFactory
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec


class AddContactActivity : AppCompatActivity() {
    private val SERVER = "server"
    private val PUBLIC_KEY = "publicKey"
    private val ALIAS = "alias"

    private var webSocketService: WebSocketService? = null

    private val dbHelper: DbHelper = DbHelper(this)

    private val publicKey : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_contact)

        val openUri = intent.data

        val alias = openUri.getQueryParameter(ALIAS)
        val publicKey = openUri.getQueryParameter(PUBLIC_KEY)
        val server = openUri.getQueryParameter(SERVER)

        findViewById<TextView>(R.id.add_contact_server).text = server
        findViewById<TextView>(R.id.add_contact_alias).text = alias


        if (server.isNullOrBlank()) {
            Toast.makeText(this@AddContactActivity, "Unable to add contact: Server is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (publicKey.isNullOrBlank()) {
            Toast.makeText(this@AddContactActivity, "Unable to add contact: Public key is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!isPublicKeyValid(publicKey)) {
            Toast.makeText(this@AddContactActivity, "Unable to add contact: Public key for contact is invalid.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (dbHelper.contactExistsForPublicKey(publicKey)) {
            Toast.makeText(this@AddContactActivity, "Unable to add contact: This public key already exists.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btnAddContact).setOnClickListener { _ ->
            val alias = findViewById<TextView>(R.id.add_contact_alias).text.toString()

            if (dbHelper.contactExistsForAlias(alias)) {
                Toast.makeText(this@AddContactActivity, "Unable to add contact: Alias '$alias' already exists.", Toast.LENGTH_SHORT).show()
            } else {
                dbHelper.insertContact(alias, server, publicKey)
                webSocketService!!.requestProfileData(server, publicKey)
                finish()
            }
        }

    }

    private fun isPublicKeyValid(publicKeyString: String): Boolean {
        try {
            val publicKeyBytes = Base64.decode(publicKeyString)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            return true
        } catch (e: InvalidKeySpecException) {
            Log.e("Contact", "Invalid public key", e)
        }
        return false
    }

    override fun onStart() {
        val mIntent = Intent(this, WebSocketService::class.java)
        bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE)
        super.onStart()
    }

    override fun onDestroy() {
        unbindService(mConnection)
        super.onDestroy()
    }

    val mConnection = object : ServiceConnection {

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
package com.github.f9c.android.contacts

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.contacts.*
import java.security.KeyPairGenerator
import java.security.SecureRandom
import android.preference.PreferenceManager
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.X509EncodedKeySpec
import android.widget.Toast
import android.support.v4.view.GestureDetectorCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import com.github.f9c.android.util.DbHelper
import com.github.f9c.android.R
import com.github.f9c.android.util.RsaKeyToStringConverter.encodePublicKey
import com.github.f9c.android.websocket.WebSocketService
import com.github.f9c.android.chat.Chat
import java.net.URLEncoder
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec


class Contacts : AppCompatActivity() {

    private val DEFAULT_SERVER = "f9c.eu"

    private val PUBLIC_KEY = "PUBLIC_KEY"
    private val PRIVATE_KEY = "PRIVATE_KEY"

    private val ALIAS = "alias"

    private var keyPair: KeyPair? = null

    private var dbHelper: DbHelper = DbHelper(this)

    private var recyclerView: RecyclerView? = null

    private var contactAdapter: ContactsAdapter? = null

    private var webSocketService: WebSocketService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contacts)
        setSupportActionBar(toolbar)


        if (intent.data != null) {
            onNewIntent(intent)
        }

        // TODO: Use password to encrypt keys or store in password manager/Autofill API?
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val publicKeyString = preferences.getString(PUBLIC_KEY, null)
        val privateKeyString = preferences.getString(PRIVATE_KEY, null)

        if (publicKeyString == null || privateKeyString == null) {
            keyPair = createKeyPair()
            saveKeyPair(preferences, keyPair!!)
        } else {
            keyPair = loadKeyPair(publicKeyString, privateKeyString)
        }

        fab.setOnClickListener { view ->
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/html"
            var alias = "nickname" // TODO: make configurable
            i.putExtra(Intent.EXTRA_SUBJECT, "f9c contact information from '$alias'")
            val encodedKey = URLEncoder.encode(encodePublicKey(keyPair!!.public), "utf-8")
            i.putExtra(Intent.EXTRA_TEXT, "https://$DEFAULT_SERVER?$ALIAS=$alias&$PUBLIC_KEY=$encodedKey")

            try {
                startActivity(Intent.createChooser(i, "Send public key link..."))
            } catch (ex: android.content.ActivityNotFoundException) {
                Toast.makeText(this@Contacts, "There are no apps supporting this action installed.", Toast.LENGTH_SHORT).show()
            }
        }

        recyclerView = findViewById<RecyclerView>(R.id.contactlist).apply {
            setHasFixedSize(true)

            layoutManager = LinearLayoutManager(this@Contacts)

            contactAdapter = ContactsAdapter(arrayListOf())
            adapter = contactAdapter
        }

        val contactListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val view = recyclerView?.findChildViewUnder(e.x, e.getY())
                val position = recyclerView?.getChildLayoutPosition(view)
                val contact = contactAdapter!!.contacts[position!!]

                val openChatIntent = Intent(this@Contacts, Chat::class.java)
                openChatIntent.putExtra("contact", contact.rowId.toString())
                this@Contacts.startActivity(openChatIntent)

                return super.onSingleTapUp(e)
            }

            override fun onLongPress(e: MotionEvent?) {
                // TODO: implement
                super.onLongPress(e)
            }
        }

        val detector = GestureDetectorCompat(this@Contacts, contactListener);


        recyclerView?.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView?, e: MotionEvent?): Boolean {
                detector.onTouchEvent(e)
                return false
            }

            override fun onTouchEvent(rv: RecyclerView?, e: MotionEvent?) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        refreshContactList()

        val serviceIntent = Intent(applicationContext, WebSocketService::class.java)
        startService(serviceIntent)
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val openUri = intent.data

        val alias = openUri.getQueryParameter(ALIAS)
        val publicKey = openUri.getQueryParameter(PUBLIC_KEY)

        if (alias.isNullOrBlank()) {
            Toast.makeText(this@Contacts, "Unable to add contact: Alias is missing.", Toast.LENGTH_SHORT).show()
            // TODO: allow user to use a different alias
        }

        if (publicKey.isNullOrBlank()) {
            Toast.makeText(this@Contacts, "Unable to add contact: Public key is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isPublicKeyValid(publicKey)) {
            Toast.makeText(this@Contacts, "Unable to add contact: Public key for contact is invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        if (dbHelper.contactExistsForPublicKey(publicKey)) {
            Toast.makeText(this@Contacts, "Unable to add contact: This public key already exists.", Toast.LENGTH_SHORT).show()
            return
        }

        if (dbHelper.contactExistsForAlias(alias)) {
            Toast.makeText(this@Contacts, "Unable to add contact: Alias '$alias' already exists.", Toast.LENGTH_SHORT).show()
            // TODO: allow user to use a different alias
            return
        }

        dbHelper.insertContact(alias, publicKey)

        refreshContactList()
    }

    private fun refreshContactList() {
        contactAdapter?.contacts = dbHelper.loadContacts()
    }

    private fun isPublicKeyValid(publicKeyString: String): Boolean {
        try {
            val publicKeyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            return true
        } catch (e: InvalidKeySpecException) {
            Log.e("Contact", "Invalid public key", e)
        }
        return false
    }

    private fun loadKeyPair(publicKeyString: String?, privateKeyString: String?): KeyPair? {
        val publicKey = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val privateKey = Base64.decode(privateKeyString, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("RSA")

        return KeyPair(keyFactory.generatePublic(X509EncodedKeySpec(publicKey)),
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKey)))
    }

    private fun saveKeyPair(preferences: SharedPreferences, keyPair: KeyPair) {
        val editor = preferences.edit()
        val publicKey = encodePublicKey(keyPair.public)
        val privateKey = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        editor.putString(PUBLIC_KEY, publicKey)
        editor.putString(PRIVATE_KEY, privateKey)
        editor.commit()
    }


    private fun createKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        val random = SecureRandom.getInstance("SHA1PRNG")
        keyGen.initialize(1024, random)
        return keyGen.generateKeyPair()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_contacts, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun startActivity(intent: Intent?) {
        super.startActivity(intent)
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
            Toast.makeText(this@Contacts, "Service is disconnected", Toast.LENGTH_LONG).show();
            webSocketService = null;
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Toast.makeText(this@Contacts, "Service is connected", Toast.LENGTH_LONG).show();
            val mLocalBinder = service as WebSocketService.LocalBinder;
            webSocketService = mLocalBinder.getServerInstance();
        }

    }
}

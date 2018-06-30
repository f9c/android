package com.github.f9c.android.ui.contacts

import android.app.Activity
import android.app.AlertDialog
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
import android.view.GestureDetector
import android.view.MotionEvent
import com.github.f9c.android.util.DbHelper
import com.github.f9c.android.R
import com.github.f9c.android.util.RsaKeyToStringConverter.encodePublicKey
import com.github.f9c.android.websocket.WebSocketService
import com.github.f9c.android.ui.chat.ChatActivity
import com.github.f9c.android.ui.settings.SettingsActivity
import java.net.URLEncoder
import java.security.spec.PKCS8EncodedKeySpec


class ContactsActivity : AppCompatActivity() {

    private val DEFAULT_SERVER = "f9c.eu"

    private val SERVER = "SERVER"
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

        // TODO: Use password to encrypt keys or store in password manager/OpenYolo API?
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val publicKeyString = preferences.getString(PUBLIC_KEY, null)
        val privateKeyString = preferences.getString(PRIVATE_KEY, null)

        if (publicKeyString == null || privateKeyString == null) {
            keyPair = createKeyPair()
            saveKeyPair(preferences, keyPair!!)
            this@ContactsActivity.startActivity(Intent(this@ContactsActivity, SettingsActivity::class.java))
        } else {
            keyPair = loadKeyPair(publicKeyString, privateKeyString)
        }

        fab.setOnClickListener { _ ->
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/html"
            var alias = preferences.getString("alias", "anonymous")
            var server = preferences.getString("server", "")
            // TODO: Include expiry date
            i.putExtra(Intent.EXTRA_SUBJECT, "f9c contact information from '$alias'")
            val encodedKey = URLEncoder.encode(encodePublicKey(keyPair!!.public), "utf-8")
            i.putExtra(Intent.EXTRA_TEXT, "https://$DEFAULT_SERVER?$ALIAS=$alias&$PUBLIC_KEY=$encodedKey&$SERVER=$server")

            try {
                startActivity(Intent.createChooser(i, "Send public key link..."))
            } catch (ex: android.content.ActivityNotFoundException) {
                Toast.makeText(this@ContactsActivity, "There are no apps installed that support this action.", Toast.LENGTH_LONG).show()
            }
        }

        recyclerView = findViewById<RecyclerView>(R.id.contactlist).apply {
            setHasFixedSize(true)

            layoutManager = LinearLayoutManager(this@ContactsActivity)

            contactAdapter = ContactsAdapter(arrayListOf())
            adapter = contactAdapter
        }

        registerForContextMenu(recyclerView)

        val contactListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val view = recyclerView?.findChildViewUnder(e.x, e.y)
                val position = recyclerView?.getChildLayoutPosition(view)
                if (position!! > -1) {
                    val contact = contactAdapter!!.contacts[position!!]
                    val openChatIntent = Intent(this@ContactsActivity, ChatActivity::class.java)
                    openChatIntent.putExtra("contact", contact.rowId.toString())
                    this@ContactsActivity.startActivity(openChatIntent)
                }
                return super.onSingleTapUp(e)
            }

        }

        val detector = GestureDetectorCompat(this@ContactsActivity, contactListener);


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

    override fun onContextItemSelected(item: MenuItem): Boolean {

        if (item.itemId == R.id.menu_contact_remove) {
            val contact = contactAdapter!!.contacts[contactAdapter!!.position]

            AlertDialog.Builder(this).setTitle(getText(R.string.remove_contact).toString()  + contact.alias)
                    .setMessage(getString(R.string.confirm_remove_contact, contact.alias))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes) { dialog, whichButton ->
                        dbHelper.removeContact(contact)
                        refreshContactList()
                        Toast.makeText(this@ContactsActivity, getString(R.string.contact_removed), Toast.LENGTH_SHORT).show()
                    }.setNegativeButton(android.R.string.no, null).show()
        }
        return super.onContextItemSelected(item)
    }

    override fun onNavigateUpFromChild(child: Activity?): Boolean {
        refreshContactList()
        return super.onNavigateUpFromChild(child)
    }

    private fun refreshContactList() {
        contactAdapter?.contacts = dbHelper.loadContacts()
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
            R.id.action_settings -> {
                this@ContactsActivity.startActivity(Intent(this@ContactsActivity, SettingsActivity::class.java))
                return true
            }
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
            webSocketService!!.openConnection()
        }

    }
}

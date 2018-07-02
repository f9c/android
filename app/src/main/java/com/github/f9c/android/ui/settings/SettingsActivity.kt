package com.github.f9c.android.ui.settings

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.github.f9c.android.R
import android.widget.ImageButton
import android.graphics.BitmapFactory
import android.app.Activity
import android.content.*
import android.widget.ImageView
import android.graphics.Bitmap
import android.os.IBinder
import android.preference.PreferenceManager
import android.widget.EditText
import android.widget.TextView
import com.github.f9c.android.profile.Profile
import com.github.f9c.android.websocket.WebSocketService
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max


class SettingsActivity : AppCompatActivity() {

    private var webSocketService: WebSocketService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        findViewById<EditText>(R.id.settings_server).setText(
                preferences.getString("server", "develop.f9c.eu"), TextView.BufferType.EDITABLE)

        findViewById<EditText>(R.id.settings_alias).setText(
                preferences.getString("alias", "anonymous"), TextView.BufferType.EDITABLE)


        val profileImageFile = Profile(applicationContext).profileImageFile()
        if (profileImageFile.exists()) {
            val profileImage = BitmapFactory.decodeFile(profileImageFile.absolutePath)
            findViewById<ImageView>(R.id.settings_profileImgView).setImageBitmap(profileImage)
        }

        findViewById<ImageButton>(R.id.btnPickProfileImage).setOnClickListener { _ ->
            val i = Intent(Intent.ACTION_PICK)
            i.type = "image/*"
            startActivityForResult(i, 1)
        }

        findViewById<ImageButton>(R.id.btnSaveSettings).setOnClickListener { _ ->
            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val editor = preferences.edit()

            editor.putString("server", findViewById<EditText>(R.id.settings_server).text.toString())
            editor.putString("alias", findViewById<EditText>(R.id.settings_alias).text.toString())

            editor.commit()
            finish()

            webSocketService!!.openConnection()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null) {
            contentResolver.openInputStream(data.data!!).use {
                val image = BitmapFactory.decodeStream(it)

                val scaleFactor = max(200.0 / image.width, 200.0 / image.height)

                val resized = Bitmap.createScaledBitmap(image, (image.width * scaleFactor).toInt(), (image.height * scaleFactor).toInt(), true)

                val croppedBmp = Bitmap.createBitmap(resized, 0, 0, 200, 200)

                val profileIconFile = Profile(applicationContext).profileImageFile()

                FileOutputStream(profileIconFile).use {
                    croppedBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }

                findViewById<ImageView>(R.id.settings_profileImgView).setImageBitmap(image)
            }
        }
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
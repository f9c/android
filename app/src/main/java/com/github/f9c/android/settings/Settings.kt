package com.github.f9c.android.settings

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.github.f9c.android.R
import android.content.Intent
import android.widget.ImageButton
import android.graphics.BitmapFactory
import android.app.Activity
import android.content.Context
import android.widget.ImageView
import android.graphics.Bitmap
import android.content.ContextWrapper
import android.preference.PreferenceManager
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max


class Settings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        findViewById<EditText>(R.id.settings_server).setText(
                preferences.getString("server", ""), TextView.BufferType.EDITABLE)

        findViewById<EditText>(R.id.settings_alias).setText(
                preferences.getString("alias", "anonymous"), TextView.BufferType.EDITABLE)


        val profileImageFile = profileImageFile()
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

                val profileIconFile = profileImageFile()

                FileOutputStream(profileIconFile).use {
                    croppedBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }

                findViewById<ImageView>(R.id.settings_profileImgView).setImageBitmap(image)
            }
        }
    }

    private fun profileImageFile(): File {
        val cw = ContextWrapper(applicationContext)
        val directory = cw.getDir("imageDir", Context.MODE_PRIVATE)
        return File(directory, "profile.jpg")
    }

}
package com.github.f9c.android.profile

import android.content.Context
import android.preference.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.PublicKey

class Profile(private var context: Context) {
    private val ALIAS = "alias"
    private val SERVER = "server"
    private val PUBLIC_KEY = "PUBLIC_KEY"
    private val PRIVATE_KEY = "PRIVATE_KEY"


    fun alias(): String {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(ALIAS, "anonymous")
    }

    fun profileImageFile(): File {
        val directory = context.getDir("imageDir", Context.MODE_PRIVATE)
        return File(directory, "profile.jpg")
    }


    fun profileImage(): ByteArray {
        val stream = ByteArrayOutputStream()
        FileInputStream(profileImageFile()).use {
            it.copyTo(stream)
        }
        return stream.toByteArray()
    }

    fun publicKey(): String {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PUBLIC_KEY, "anonymous")
    }

    fun server(): String? {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SERVER, "")
    }
}
package com.github.f9c.android.profile

import android.content.Context
import android.preference.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

object ProfileConstants {
    val ALIAS = "alias"
    val STATUS_TEXT = "statusText"
    val SERVER = "server"
    val PUBLIC_KEY = "PUBLIC_KEY"
    val PRIVATE_KEY = "PRIVATE_KEY"
}
class Profile(private var context: Context) {

    fun alias(): String {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(ProfileConstants.ALIAS, "anonymous")
    }

    fun statusText(): String {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(ProfileConstants.STATUS_TEXT, "I hart you my friend!")
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
        return PreferenceManager.getDefaultSharedPreferences(context).getString(ProfileConstants.PUBLIC_KEY, "anonymous")
    }

    fun server(): String? {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(ProfileConstants.SERVER, "")
    }
}
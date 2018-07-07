package com.github.f9c.android.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.util.Log
import com.github.f9c.android.profile.Profile
import com.github.f9c.android.profile.ProfileConstants
import java.net.URLDecoder

/**
 * If the app is installed using a link like the following:
 *
 * https://play.google.com/store/apps/details?id=com.github.f9c.android&referrer=referrer.f9c.eu
 *
 * The value from the referrer is broadcasted to the app. This value is used as initial value for
 * the server, so people can create links allowing the app to be installed for usage with their
 * server.
 */
class ReferrerReceiver : BroadcastReceiver() {
    private val tag = BroadcastReceiver::class.java.simpleName!!

    private val defaultGooglePlayReferrer = "utm_source=google-play&utm_medium=organic"

    override fun onReceive(context: Context, intent: Intent) {
        val referrer =  URLDecoder.decode(intent.getStringExtra("referrer"), "UTF-8")

        Log.d(tag, "received referrer broadcast: $referrer")

        // Only set server if it has not been set before. Otherwise it would be possible to
        // replace the server by sending an intent without the user noticing it.
        if (Profile(context).server() == "" && referrer != defaultGooglePlayReferrer) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preferences.edit()
            Log.d(tag, "updating server to: $referrer")
            editor.putString(ProfileConstants.SERVER, referrer)
            editor.apply()
        }
    }

}
package com.github.f9c.android.ui.chat

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.github.f9c.android.R
import com.github.f9c.android.ui.contacts.ContactsActivity
import com.github.f9c.android.util.DbHelper

class MessageNotificationReceiver : BroadcastReceiver(), Application.ActivityLifecycleCallbacks {
    private var currentActivity: Activity? = null

    override fun onReceive(context: Context, intent: Intent) {
        val dbHelper = DbHelper(context)

        var unreadMessages = dbHelper.loadUnreadMessages()

        if (currentActivity is ChatActivity) {
            // ignore messages that arrive for the currently active chat activity.
            unreadMessages.remove((currentActivity as ChatActivity).alias)
        }

        when {
            unreadMessages.isEmpty() -> {
                // ignore: Message arrived for current chat activity
            }
            unreadMessages.size == 1 -> {
                val alias = unreadMessages.keys.iterator().next()
                val messages = unreadMessages[alias]

                var concatenatedText : String? = null

                for (message in messages!!) {
                    concatenatedText = if (concatenatedText == null) {
                        message
                    } else {
                        concatenatedText + "\n" + message
                    }
                }

                var openChatIntent = Intent(context, ChatActivity::class.java)
                openChatIntent.putExtra("contact", dbHelper.loadContactRowIdForAlias(alias))

                var pIntent = PendingIntent.getActivity(context, System.currentTimeMillis().toInt(), openChatIntent, 0)

                val notification = builder(context)
                        .setContentTitle("$alias sent")
                        .setStyle(Notification.BigTextStyle().bigText(concatenatedText))
                        .setContentText(concatenatedText)
                        .setSmallIcon(R.drawable.ic_logo)
                        .setContentIntent(pIntent)
                        .setVibrate(longArrayOf(300L, 300L))
                        .setAutoCancel(true).build()

                (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(0, notification)
            }
            else -> {
                var openContactsIntent = Intent(context, ContactsActivity::class.java)

                var pIntent = PendingIntent.getActivity(context, System.currentTimeMillis().toInt(), openContactsIntent, 0)

                var concatenatedText: String? = null

                for (alias in unreadMessages.keys) {
                    val messages = unreadMessages[alias]

                    if (concatenatedText == null) {
                        concatenatedText = "$alias: ${messages!![0]}"
                    } else {
                        concatenatedText += "\n$alias: ${messages!![0]}"
                    }
                }

                val notification = builder(context)
                        .setContentTitle("Messages received")
                        .setStyle(Notification.BigTextStyle().bigText(concatenatedText))
                        .setContentText(concatenatedText)
                        .setSmallIcon(R.drawable.ic_logo)
                        .setContentIntent(pIntent)
                        .setVibrate(longArrayOf(300L, 300L))
                        .setAutoCancel(true).build()

                (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(0, notification)
            }
        }
    }


    private fun builder(context: Context): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ChatConstants.CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        currentActivity = null
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        // ignore
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {
        // ignore
    }

    override fun onActivityStopped(activity: Activity) {
        currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // ignore
    }


    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

}
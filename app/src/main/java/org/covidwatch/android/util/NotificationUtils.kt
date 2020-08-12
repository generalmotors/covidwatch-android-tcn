package org.covidwatch.android.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.covidwatch.android.R
import org.covidwatch.android.SnoozeBroadcastReceiver
import org.covidwatch.android.presentation.MainActivity
import kotlin.math.round

object NotificationUtils {

//    public const val ACTION_TOO_CLOSE_NOTIFICATION =
//        "org.covidwatch.android.ACTION_TOO_CLOSE_NOTIFICATION"
//
//    public const val ACTION_DANGER_NOTIFICATION =
//        "org.covidwatch.android.ACTION_DANGER_NOTIFICATION"

    private const val TOO_CLOSE_CHANNEL_ID = "too_close_notification_channel"
    private const val DANGER_CHANNEL_ID = "danger_notification_channel"

    private const val NOTIFICATION_ID = 0

    private lateinit var context: Context
    private lateinit var notifyManager: NotificationManager


    fun init(context: Context) {
        this.context = context

        notifyManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannelTooClose =
                NotificationChannel(
                    TOO_CLOSE_CHANNEL_ID,
                    "Too Close",
                    NotificationManager.IMPORTANCE_HIGH
                )

            notificationChannelTooClose.enableLights(true)
            notificationChannelTooClose.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            notificationChannelTooClose.lightColor = Color.RED
            notificationChannelTooClose.enableVibration(true)
            notificationChannelTooClose.description = "Notify me when someone is too close"
            notifyManager.createNotificationChannel(notificationChannelTooClose)

            val notificationChannelDanger = NotificationChannel(
                DANGER_CHANNEL_ID,
                "Covid Contact Warnings",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannelDanger.enableLights(true)
            notificationChannelDanger.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            notificationChannelDanger.lightColor = Color.MAGENTA
            notificationChannelDanger.enableVibration(true)
            notificationChannelDanger.description =
                "Notify me Covid-19 too close to me"
            notifyManager.createNotificationChannel(notificationChannelDanger)
        } else {
            TODO("VERSION.SDK_INT < O")
        }

    }

    fun sendNotificationTooClose(estDistance: Double? = null, duration: Double? = null) {

        val notifyBuilder = getNotificationBuilderTooClose(estDistance, duration)
        notifyManager.notify(NOTIFICATION_ID,notifyBuilder.build())

        val pm =  context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl: PowerManager.WakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "GMSafe:WakeForAlert")
        wl.acquire(10000)
    }

    fun sendNotificationDanger() {

        val notifyBuilder = getNotificationBuilderDanger()
        notifyManager.notify(NOTIFICATION_ID,notifyBuilder.build())

        val pm =  context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl: PowerManager.WakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "GMSafe:WakeForDanger")
        wl.acquire(10000)
    }

    private fun getNotificationBuilderTooClose(estDistance: Double? = null, duration: Double? = null): NotificationCompat.Builder {


        var distanceAttribute = "too close to you."
//        var durationAttribute = "for an extended period of time."
        val notificationIntent = Intent(context, MainActivity::class.java)
        val notificationPendingIntent = PendingIntent.getActivity(
            context,NOTIFICATION_ID,notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (estDistance != null) {
            distanceAttribute = "within roughly ${round(estDistance)} ft of you."
        }
//        if (duration != null) {
//            durationAttribute = "for approx. $duration seconds."
//        }

        val snoozeIntent = Intent(context, SnoozeBroadcastReceiver::class.java).apply {
            action = "snooze"
            putExtra("action", "snooze")
        }

        val snoozePendingIntent: PendingIntent =
            PendingIntent.getBroadcast(context, 0, snoozeIntent, 0)

        return NotificationCompat.Builder(context,TOO_CLOSE_CHANNEL_ID)
            .setContentTitle("Social Distance Alert")
            .setContentText("Another person has been detected near you for more than 10 minutes. Distance: $distanceAttribute")
            .setSmallIcon(R.drawable.ic_info_red)
            .setAutoCancel(true).setContentIntent(notificationPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_info, "SNOOZE", snoozePendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)!!

    }
    private fun getNotificationBuilderDanger(): NotificationCompat.Builder {

        val notificationIntent = Intent(context, MainActivity::class.java)
        val notificationPendingIntent = PendingIntent.getActivity(
            context,NOTIFICATION_ID,notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozeIntent = Intent(context, SnoozeBroadcastReceiver::class.java).apply {
            action = "snooze"
            putExtra("action", "snooze")
        }

        val snoozePendingIntent: PendingIntent =
            PendingIntent.getBroadcast(context, 0, snoozeIntent, 0)

        return NotificationCompat.Builder(context, DANGER_CHANNEL_ID)
            .setContentTitle("Covid-19 Detected")
            .setContentText(context.getText(R.string.contact_alert_text))
            .setSmallIcon(R.drawable.ic_info_red)
            .setAutoCancel(true).setContentIntent(notificationPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_info, "SNOOZE", snoozePendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)!!
    }

}
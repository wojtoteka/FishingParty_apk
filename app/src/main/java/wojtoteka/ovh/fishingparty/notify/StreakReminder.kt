package wojtoteka.ovh.fishingparty.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import wojtoteka.ovh.fishingparty.MainActivity
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.StreakState
import java.time.ZonedDateTime

/**
 * Przypomnienie o passie. Planowane za każdym razem, gdy gra schodzi w tło,
 * więc zawsze odpowiada ostatniemu stanowi passy. Teksty biorą język telefonu.
 */
object StreakReminder {
    private const val CHANNEL = "streak"
    private const val NOTIFICATION_ID = 1
    private const val EXTRA_DAYS = "days"

    /** Godziny przypomnień w dniu, w którym passa jeszcze nie jest zaliczona. */
    private val HOURS = intArrayOf(19, 21)

    fun schedule(context: Context, streak: StreakState) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, streak.days)
        alarms.cancel(pending)
        if (streak.days <= 0) return
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val at = if (streak.playedToday) {
            today.plusDays(1).atTime(HOURS[0], 0).atZone(now.zone)
        } else {
            // Po ostatniej godzinie passa i tak przepada o północy – nie ma o czym przypominać.
            HOURS.map { today.atTime(it, 0).atZone(now.zone) }.firstOrNull { it.isAfter(now.plusMinutes(15)) } ?: return
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending)
    }

    /** Gracz wrócił do gry – stare przypomnienie znika z paska. */
    fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun pendingIntent(context: Context, days: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, StreakReminderReceiver::class.java).putExtra(EXTRA_DAYS, days),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun show(context: Context, intent: Intent) {
        val days = intent.getIntExtra(EXTRA_DAYS, 0)
        if (days <= 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_desc)
            },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_text, days, days))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

class StreakReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = StreakReminder.show(context, intent)
}

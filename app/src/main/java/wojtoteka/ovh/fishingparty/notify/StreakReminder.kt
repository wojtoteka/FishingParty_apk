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
import wojtoteka.ovh.fishingparty.AppLanguage
import wojtoteka.ovh.fishingparty.FishingPartyApp
import wojtoteka.ovh.fishingparty.MainActivity
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.StreakRewards
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Rodzaj przypomnienia – każdy ma własne teksty. */
enum class ReminderKind { FIRST, SECOND, LAST, REPAIR }

/** Pory przypomnień (bez Androida, więc testowalne na JVM). */
object StreakReminderSlots {
    /**
     * Przypomnienie „ostatnia szansa” – na tyle wcześnie, żeby zdążyć przejść poziom
     * (system może opóźnić niedokładny alarm nawet o godzinę).
     */
    private val LAST_CALL: LocalTime = LocalTime.of(22, 0)

    /** Godziny przypomnień do wyboru w ustawieniach. */
    val HOURS = intArrayOf(16, 17, 18, 19, 20)

    /** Pory przypomnień w dniu bez zaliczonej passy; odstępy co najmniej półtorej godziny. */
    fun slots(hour: Int): List<Pair<LocalTime, ReminderKind>> {
        val first = LocalTime.of(hour.coerceIn(0, 21), 0)
        val result = mutableListOf(first to ReminderKind.FIRST)
        val second = first.plusMinutes(120)
        if (second.isBefore(LAST_CALL.minusMinutes(90))) result += second to ReminderKind.SECOND
        if (!LAST_CALL.isBefore(result.last().first.plusMinutes(90))) result += LAST_CALL to ReminderKind.LAST
        return result
    }
}

/**
 * Przypomnienia o passie. W dniu, w którym passa nie jest jeszcze zaliczona,
 * przychodzą najwyżej trzy (np. 18:00, 20:00 i „ostatnia szansa” o 22:00),
 * każde zastępuje poprzednie na pasku, więc nic się nie piętrzy. Gdy passa
 * jest przerwana, ale da się ją odzyskać – jedno przypomnienie dziennie.
 * Po zaliczeniu dnia następne przychodzi dopiero jutro.
 *
 * Każde przypomnienie po pokazaniu planuje kolejne, zawsze na podstawie
 * aktualnego stanu zapisu.
 */
object StreakReminder {
    private const val CHANNEL = "streak"
    private const val NOTIFICATION_ID = 1
    private const val EXTRA_KIND = "kind"
    private const val EXTRA_TEST = "test"

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val app = context.applicationContext as? FishingPartyApp ?: return
        alarms.cancel(pendingIntent(context, ReminderKind.FIRST))
        val save = app.repository.save
        if (!save.streakReminders) return
        val progress = app.repository.progress
        // Dzień passy kończy się o północy w strefie gry (Europe/Warsaw), więc przypomnienia liczymy w niej.
        val now = ZonedDateTime.now(app.repository.clock.zone)
        val today = now.toLocalDate()
        val slots = StreakReminderSlots.slots(save.reminderHour)
        val streak = progress.streakState()
        val (day, slot) = when {
            streak.days > 0 && !streak.playedToday ->
                slots.firstOrNull { today.atTime(it.first).atZone(now.zone).isAfter(now.plusMinutes(10)) }
                    ?.let { today to it }
                    // Po ostatnim przypomnieniu passa i tak przepada o północy – jutro ewentualnie naprawa.
                    ?: (today.plusDays(1) to slots.first().copy(second = ReminderKind.REPAIR))
            streak.days > 0 -> today.plusDays(1) to slots.first()
            progress.streakRepair() != null -> nextDayAt(today, now, slots.first().first) to slots.first().copy(second = ReminderKind.REPAIR)
            else -> return
        }
        val (time, kind) = slot
        val at = day.atTime(time).atZone(now.zone)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pendingIntent(context, kind))
    }

    /** Dziś, jeśli godzina jeszcze nie minęła – inaczej jutro. */
    private fun nextDayAt(today: LocalDate, now: ZonedDateTime, time: LocalTime): LocalDate =
        if (today.atTime(time).atZone(now.zone).isAfter(now.plusMinutes(10))) today else today.plusDays(1)

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, ReminderKind.FIRST))
        dismiss(context)
    }

    /** Gracz wrócił do gry – stare przypomnienie znika z paska. */
    fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    // Jeden PendingIntent (request 0) na wszystkie rodzaje – nowe zawsze zastępuje stare.
    private fun pendingIntent(context: Context, kind: ReminderKind): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, StreakReminderReceiver::class.java).putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun onAlarm(context: Context, intent: Intent) {
        val app = context.applicationContext as? FishingPartyApp ?: return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { k -> ReminderKind.entries.firstOrNull { it.name == k } } ?: ReminderKind.FIRST
        val text = AppLanguage.wrap(context)
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            // Tylko do testów przez adb (wersja debug): pokazuje wybrany rodzaj z przykładową passą.
            show(text, kind, days = 6)
            return
        }
        val progress = app.repository.progress
        val streak = progress.streakState()
        // Gdy gracz akurat gra, powiadomienie byłoby tylko przeszkodą.
        if (app.repository.save.streakReminders && !app.inForeground) {
            when {
                kind == ReminderKind.REPAIR -> progress.streakRepair()?.let { show(text, kind, it.days) }
                streak.days > 0 && !streak.playedToday -> show(text, kind, streak.days)
            }
        }
        schedule(context)
    }

    private fun show(context: Context, kind: ReminderKind, days: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_desc)
            },
        )
        val (title, body) = texts(context, kind, days)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        ReminderLog.notified(context)
    }

    /** Tytuł i treść – kilka wariantów, zmieniających się z dnia na dzień, żeby nie nudziły. */
    private fun texts(context: Context, kind: ReminderKind, days: Int): Pair<String, String> {
        val r = context.resources
        val daysText = r.getQuantityString(R.plurals.notif_days, days, days)
        val trophies = StreakRewards.trophiesFor(days + 1)
        val variant = (LocalDate.now().toEpochDay() % 3).toInt()
        return when (kind) {
            ReminderKind.FIRST -> if ((days + 1) % StreakRewards.WEEK == 0) {
                context.getString(R.string.notif_week_title) to context.getString(R.string.notif_week_text, trophies)
            } else {
                val titles = intArrayOf(R.string.notif_first_title_1, R.string.notif_first_title_2, R.string.notif_first_title_3)
                val bodies = intArrayOf(R.string.notif_first_text_1, R.string.notif_first_text_2, R.string.notif_first_text_3)
                context.getString(titles[variant], daysText) to context.getString(bodies[variant], daysText, trophies)
            }
            ReminderKind.SECOND -> context.getString(R.string.notif_second_title) to context.getString(R.string.notif_second_text, daysText)
            ReminderKind.LAST -> context.getString(R.string.notif_last_title) to context.getString(R.string.notif_last_text, daysText)
            ReminderKind.REPAIR -> context.getString(R.string.notif_repair_title) to context.getString(R.string.notif_repair_text, daysText)
        }
    }
}

class StreakReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = StreakReminder.onAlarm(context, intent)
}

/** Po restarcie telefonu (albo aktualizacji gry) zaplanowane alarmy znikają – planujemy je od nowa. */
class StreakBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            StreakReminder.schedule(context)
            ChallengeReminder.schedule(context)
        }
    }
}

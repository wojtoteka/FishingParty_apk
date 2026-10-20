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
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Kiedy przypominać o nowym poziomie dnia i tygodnia (bez Androida, więc testowalne na JVM).
 *
 * Zasady przeciw spamowi:
 * - najwyżej jedno przypomnienie dziennie, w poniedziałek o poziomie tygodnia (wspomina też o dniu),
 * - zawsze przed południem/wczesnym popołudniem – co najmniej [MIN_GAP_HOURS] h przed
 *   pierwszym przypomnieniem o passie,
 * - nie, gdy gra była już dziś otwarta albo poziom jest ukończony,
 * - po tygodniu bez gry zostaje tylko poniedziałek, po czterech tygodniach – cisza.
 */
object ChallengeReminderPlan {
    /** Godziny do wyboru w ustawieniach. */
    val HOURS = intArrayOf(9, 10, 11, 12, 13)
    const val DEFAULT_HOUR = 11

    /** Minimalny odstęp od innego powiadomienia gry (np. o passie). */
    const val MIN_GAP_HOURS = 3

    /** Po tylu dniach bez otwarcia gry przypominamy już tylko o poziomie tygodnia. */
    const val WEEKLY_ONLY_AFTER_DAYS = 7

    /** Po tylu – wcale (gracz zrobił sobie przerwę, nie nękamy go). */
    const val STOP_AFTER_DAYS = 28

    /** O czym przypomnieć danego dnia (null = nic). [idleDays] – dni od ostatniego otwarcia gry. */
    fun kindOn(date: LocalDate, daily: Boolean, weekly: Boolean, idleDays: Long): ChallengeKind? = when {
        idleDays >= STOP_AFTER_DAYS -> null
        weekly && date.dayOfWeek == DayOfWeek.MONDAY -> ChallengeKind.WEEKLY
        idleDays >= WEEKLY_ONLY_AFTER_DAYS -> null
        daily -> ChallengeKind.DAILY
        else -> null
    }

    /** Najbliższe przypomnienie po [now] albo null, gdy w ciągu tygodnia nie ma żadnego. */
    fun next(now: ZonedDateTime, hour: Int, daily: Boolean, weekly: Boolean, lastOpened: LocalDate?): Pair<ZonedDateTime, ChallengeKind>? {
        val today = now.toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            val at = date.atTime(hour.coerceIn(0, 23), 0).atZone(now.zone)
            if (!at.isAfter(now.plusMinutes(10))) continue
            val idle = lastOpened?.let { ChronoUnit.DAYS.between(it, date) } ?: 0L
            val kind = kindOn(date, daily, weekly, idle) ?: continue
            return at to kind
        }
        return null
    }

    /**
     * Czy pokazać przypomnienie, które właśnie nadeszło.
     * @param openedAt ostatnie otwarcie gry, @param dayStart początek dzisiejszego dnia (oba w ms)
     * @param lastNotifiedAt ostatnie powiadomienie gry (dowolne)
     */
    fun shouldShow(now: Long, dayStart: Long, openedAt: Long, lastNotifiedAt: Long, completed: Boolean): Boolean =
        !completed && openedAt < dayStart && now - lastNotifiedAt >= MIN_GAP_HOURS * 3_600_000L
}

/** Kiedy gra była ostatnio otwarta i kiedy pokazała ostatnie powiadomienie – wspólne dla wszystkich przypomnień. */
object ReminderLog {
    private const val PREFS = "reminders"
    private const val OPENED = "opened_at"
    private const val NOTIFIED = "notified_at"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun opened(context: Context) = prefs(context).edit().putLong(OPENED, System.currentTimeMillis()).apply()

    fun notified(context: Context) = prefs(context).edit().putLong(NOTIFIED, System.currentTimeMillis()).apply()

    fun openedAt(context: Context): Long = prefs(context).getLong(OPENED, 0L)

    fun notifiedAt(context: Context): Long = prefs(context).getLong(NOTIFIED, 0L)
}

/**
 * Przypomnienie o nowym poziomie dnia (codziennie) i tygodnia (w poniedziałek), po resecie
 * o północy – o godzinie wybranej w ustawieniach. Osobny kanał niż passa, więc w telefonie
 * można je wyłączyć niezależnie. Szczegóły zasad – [ChallengeReminderPlan].
 */
object ChallengeReminder {
    private const val CHANNEL = "challenges"
    private const val NOTIFICATION_ID = 2
    private const val EXTRA_KIND = "kind"

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val app = context.applicationContext as? FishingPartyApp ?: return
        val save = app.repository.save
        alarms.cancel(pendingIntent(context, ChallengeKind.DAILY))
        if (!save.dailyLevelReminders && !save.weeklyLevelReminders) return
        // Poziomy zmieniają się o północy w strefie gry (Europe/Warsaw) – w niej liczymy godziny.
        val zone = app.repository.clock.zone
        val now = ZonedDateTime.now(zone)
        val openedAt = ReminderLog.openedAt(context)
        val lastOpened = if (openedAt > 0) Instant.ofEpochMilli(openedAt).atZone(zone).toLocalDate() else null
        val (at, kind) = ChallengeReminderPlan.next(
            now, save.challengeReminderHour, save.dailyLevelReminders, save.weeklyLevelReminders, lastOpened,
        ) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pendingIntent(context, kind))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, ChallengeKind.DAILY))
        dismiss(context)
    }

    /** Gracz otworzył grę – przypomnienie jest już zbędne. */
    fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    // Jeden PendingIntent na oba rodzaje – nowe zawsze zastępuje stare.
    private fun pendingIntent(context: Context, kind: ChallengeKind): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ChallengeReminderReceiver::class.java).putExtra(EXTRA_KIND, kind.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun onAlarm(context: Context, intent: Intent) {
        val app = context.applicationContext as? FishingPartyApp ?: return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let(ChallengeKind::of) ?: ChallengeKind.DAILY
        val save = app.repository.save
        val progress = app.repository.progress
        val enabled = if (kind == ChallengeKind.WEEKLY) save.weeklyLevelReminders else save.dailyLevelReminders
        val zone = app.repository.clock.zone
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        // Brak seeda bieżącego okresu w pamięci = gracz go jeszcze nie widział, więc też nie ukończył.
        val completed = progress.currentChallenge(kind)?.let { progress.challengeResult(kind, it.period) } != null
        val show = enabled && !app.inForeground && ChallengeReminderPlan.shouldShow(
            now = System.currentTimeMillis(),
            dayStart = dayStart,
            openedAt = ReminderLog.openedAt(context),
            lastNotifiedAt = ReminderLog.notifiedAt(context),
            completed = completed,
        )
        if (show) show(AppLanguage.wrap(context), kind, progress.streakState().let { if (it.playedToday) 0 else it.days })
        schedule(context)
    }

    private fun show(context: Context, kind: ChallengeKind, streakDays: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_challenge_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_challenge_channel_desc)
            },
        )
        val (title, body) = texts(context, kind, streakDays)
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
            // Wygasa o północy razem z poziomem dnia – nie wisi na pasku z nieaktualną treścią.
            .setTimeoutAfter(millisToMidnight(context))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        ReminderLog.notified(context)
    }

    private fun millisToMidnight(context: Context): Long {
        val zone = (context.applicationContext as FishingPartyApp).repository.clock.zone
        val now = ZonedDateTime.now(zone)
        return ChronoUnit.MILLIS.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).coerceAtLeast(60_000L)
    }

    /** Kilka wariantów treści, zmieniających się z dnia na dzień. Gdy passa czeka – dopisek, że poziom ją zalicza. */
    private fun texts(context: Context, kind: ChallengeKind, streakDays: Int): Pair<String, String> {
        val variant = LocalDate.now().toEpochDay().toInt()
        val (title, body) = if (kind == ChallengeKind.WEEKLY) {
            val titles = intArrayOf(R.string.notif_weekly_title_1, R.string.notif_weekly_title_2, R.string.notif_weekly_title_3)
            val bodies = intArrayOf(R.string.notif_weekly_text_1, R.string.notif_weekly_text_2, R.string.notif_weekly_text_3)
            val i = Math.floorMod(variant / 7, titles.size)
            context.getString(titles[i]) to context.getString(bodies[i], kind.pointsMultiplier, kind.coinBonus)
        } else {
            val titles = intArrayOf(
                R.string.notif_daily_title_1, R.string.notif_daily_title_2, R.string.notif_daily_title_3, R.string.notif_daily_title_4,
            )
            val bodies = intArrayOf(
                R.string.notif_daily_text_1, R.string.notif_daily_text_2, R.string.notif_daily_text_3, R.string.notif_daily_text_4,
            )
            val i = Math.floorMod(variant, titles.size)
            context.getString(titles[i]) to context.getString(bodies[i], kind.coinBonus)
        }
        if (streakDays <= 0) return title to body
        val days = context.resources.getQuantityString(R.plurals.notif_days, streakDays, streakDays)
        return title to body + " " + context.getString(R.string.notif_challenge_streak, days)
    }
}

class ChallengeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ChallengeReminder.onAlarm(context, intent)
}

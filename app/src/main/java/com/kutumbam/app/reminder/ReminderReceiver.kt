package com.kutumbam.app.reminder

import android.Manifest
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
import com.kutumbam.app.KutumbamApp
import com.kutumbam.app.MainActivity
import com.kutumbam.app.R
import com.kutumbam.app.data.MedicineEntity
import com.kutumbam.app.parse.MealTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ReminderScheduler.ACTION_FIRE -> fire(context, intent)
                    ReminderScheduler.ACTION_TAKEN -> taken(context, intent)
                    ReminderScheduler.ACTION_TEST -> notify(context, ReminderScheduler.TEST_CODE, "Kutumbam test reminder", "If you can read this, medicine reminders will reach you.", null)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, intent: Intent) {
        val repo = (context.applicationContext as KutumbamApp).repo
        val med = repo.medicine(intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE, -1)) ?: return
        val time = LocalTime.parse(intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return)
        // Re-arm for the next day first, so a slow notification path can never lose tomorrow's reminder.
        ReminderScheduler.scheduleNext(context, med, time, LocalDateTime.now().plusMinutes(1))

        val alreadyTaken = repo.isDoseTaken(med.id, LocalDate.now(), time.toString())
        if (alreadyTaken) return
        val member = repo.member(med.memberId)
        val code = ReminderScheduler.requestCode(med.id, time)
        notify(context, code, "Time for ${member?.name ?: "your"}${if (member != null) "'s" else ""} medicine", describe(med, time), Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderScheduler.ACTION_TAKEN).putExtra(ReminderScheduler.EXTRA_MEDICINE, med.id).putExtra(ReminderScheduler.EXTRA_TIME, time.toString()))
    }

    private suspend fun taken(context: Context, intent: Intent) {
        val repo = (context.applicationContext as KutumbamApp).repo
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE, -1)
        val time = LocalTime.parse(intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return)
        repo.setDose(id, LocalDate.now(), time.toString(), true)
        NotificationManagerCompat.from(context).cancel(ReminderScheduler.requestCode(id, time))
    }

    private fun describe(m: MedicineEntity, time: LocalTime): String {
        val meal = when (MealTiming.valueOf(m.mealTiming)) {
            MealTiming.BEFORE_FOOD -> "before food"
            MealTiming.AFTER_FOOD -> "after food"
            MealTiming.WITH_FOOD -> "with food"
            MealTiming.EMPTY_STOMACH -> "on an empty stomach"
            MealTiming.UNSPECIFIED -> null
        }
        val clock = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        return listOfNotNull("$clock: ${listOfNotNull(m.name, m.strength).joinToString(" ")}", meal).joinToString(", ")
    }

    private fun notify(context: Context, id: Int, title: String, text: String, takenIntent: Intent?) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open).setAutoCancel(true)
        takenIntent?.let {
            val pi = PendingIntent.getBroadcast(context, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, "Taken", pi)
        }
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    companion object {
        const val CHANNEL = "medicine_reminders"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Medicine reminders", NotificationManager.IMPORTANCE_HIGH))
            }
        }
    }
}

package com.kutumbam.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kutumbam.app.KutumbamApp
import com.kutumbam.app.data.MedicineEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * One alarm per (medicine, time of day), always aimed at the next occurrence. When an alarm fires the receiver
 * re-arms it for the following day, so reminders keep working with the app closed and the phone offline.
 */
object ReminderScheduler {
    const val ACTION_FIRE = "com.kutumbam.app.REMINDER_FIRE"
    const val ACTION_TAKEN = "com.kutumbam.app.REMINDER_TAKEN"
    const val ACTION_TEST = "com.kutumbam.app.REMINDER_TEST"
    const val EXTRA_MEDICINE = "medicineId"
    const val EXTRA_TIME = "time"

    /** Re-arms every reminder from the database. Safe to call repeatedly: same alarm keys replace themselves. */
    suspend fun scheduleAll(context: Context) {
        val repo = (context.applicationContext as KutumbamApp).repo
        repo.allMedicines().forEach { scheduleMedicine(context, it) }
    }

    fun scheduleMedicine(context: Context, m: MedicineEntity) {
        m.timesCsv.split(",").filter { it.isNotBlank() }.forEach { t -> scheduleNext(context, m, LocalTime.parse(t), LocalDateTime.now()) }
    }

    /** Arms the first occurrence of [time] after [after] on a day the course is still running. */
    fun scheduleNext(context: Context, m: MedicineEntity, time: LocalTime, after: LocalDateTime) {
        val trigger = nextOccurrence(m, time, after) ?: return
        arm(context, requestCode(m.id, time), trigger, Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra(EXTRA_MEDICINE, m.id).putExtra(EXTRA_TIME, time.toString()))
    }

    fun scheduleTest(context: Context, secondsFromNow: Long = 5) {
        arm(context, TEST_CODE, LocalDateTime.now().plusSeconds(secondsFromNow), Intent(context, ReminderReceiver::class.java).setAction(ACTION_TEST))
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun nextOccurrence(m: MedicineEntity, time: LocalTime, after: LocalDateTime): LocalDateTime? {
        val start = LocalDate.parse(m.startDate)
        val end = m.durationDays?.let { start.plusDays(it.toLong()) }
        for (offset in 0L..2L) {
            val day = after.toLocalDate().plusDays(offset)
            val candidate = day.atTime(time)
            if (!candidate.isAfter(after)) continue
            if (day.isBefore(start) || (end != null && !day.isBefore(end))) continue
            return candidate
        }
        return null
    }

    fun requestCode(medicineId: Long, time: LocalTime): Int = (medicineId * 2000 + time.hour * 60 + time.minute).toInt()

    private fun arm(context: Context, code: Int, at: LocalDateTime, intent: Intent) {
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    const val TEST_CODE = 1
}

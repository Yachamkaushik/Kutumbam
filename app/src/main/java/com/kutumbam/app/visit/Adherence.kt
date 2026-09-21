package com.kutumbam.app.visit

import java.time.LocalDate

/** A scheduled medicine as far as counting doses goes. */
data class ScheduledMed(val id: Long, val start: LocalDate, val durationDays: Int?, val dosesPerDay: Int)

/** Doses the person marked as taken against doses that were scheduled, over the days before today. */
data class Adherence(val taken: Int, val scheduled: Int) {
    val missed get() = scheduled - taken

    companion object {
        const val WINDOW_DAYS = 14

        /** [takenLog] holds one entry per dose marked taken: the medicine and the day. Today is left out because the day is not over. */
        fun compute(meds: List<ScheduledMed>, takenLog: List<Pair<Long, LocalDate>>, today: LocalDate, days: Int = WINDOW_DAYS): Adherence {
            var scheduled = 0
            var taken = 0
            for (m in meds) {
                for (back in 1..days) {
                    val day = today.minusDays(back.toLong())
                    if (day.isBefore(m.start)) continue
                    if (m.durationDays != null && !day.isBefore(m.start.plusDays(m.durationDays.toLong()))) continue
                    scheduled += m.dosesPerDay
                    taken += minOf(m.dosesPerDay, takenLog.count { it.first == m.id && it.second == day })
                }
            }
            return Adherence(taken, scheduled)
        }
    }
}

/** Something the person noticed, in their own words. */
data class NoteItem(val date: LocalDate, val text: String)

package com.kutumbam.app.data

import com.kutumbam.app.parse.DoseSlot
import com.kutumbam.app.parse.DoseUnits
import com.kutumbam.app.parse.RefillEstimate
import com.kutumbam.app.parse.RefillPredictor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The scheduled doses with their tablet counts; empty for as-needed medicines. */
fun MedicineEntity.doseSlots(): List<DoseSlot> {
    val times = timesCsv.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it) }
    return RefillPredictor.slots(frequencyCode, times, DoseUnits.fromCsv(unitsCsv, times.size))
}

/** When the tablets were counted. Older rows stored only a date, which is read as the start of that day. */
fun MedicineEntity.countedAt(): LocalDateTime {
    val s = stockAsOf ?: startDate
    return if ('T' in s) LocalDateTime.parse(s) else LocalDate.parse(s).atStartOfDay()
}

/** Null when no tablet count is known or the medicine is taken only as needed. */
fun MedicineEntity.refill(now: LocalDateTime): RefillEstimate? {
    val stock = stockCount ?: return null
    val end = durationDays?.let { LocalDate.parse(startDate).plusDays(it.toLong()) }
    return RefillPredictor.estimate(stock, countedAt(), doseSlots(), end, now)
}

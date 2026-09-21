package com.kutumbam.app.data

import com.kutumbam.app.parse.RefillEstimate
import com.kutumbam.app.parse.RefillPredictor
import java.time.LocalDate

/** Null when no tablet count is known or the medicine is taken only as needed. */
fun MedicineEntity.refill(today: LocalDate): RefillEstimate? {
    val stock = stockCount ?: return null
    val perDay = RefillPredictor.dosesPerDay(frequencyCode, timesCsv) ?: return null
    val start = LocalDate.parse(startDate)
    val end = durationDays?.let { start.plusDays(it.toLong()) }
    return RefillPredictor.estimate(stock, LocalDate.parse(stockAsOf ?: startDate), perDay, end, today)
}

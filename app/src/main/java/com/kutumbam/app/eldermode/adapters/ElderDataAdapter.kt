package com.kutumbam.app.eldermode.adapters

import com.kutumbam.app.data.DoseLog
import com.kutumbam.app.data.FamilyMember
import com.kutumbam.app.data.LabValueEntity
import com.kutumbam.app.data.MedicineEntity
import com.kutumbam.app.data.Repository
import com.kutumbam.app.data.toReading
import com.kutumbam.app.eldermode.models.DoseStatus
import com.kutumbam.app.eldermode.models.ElderDose
import com.kutumbam.app.eldermode.models.ElderHealthItem
import com.kutumbam.app.locker.AnswerRules
import com.kutumbam.app.locker.LabRecord
import com.kutumbam.app.locker.LockerData
import com.kutumbam.app.locker.MedRecord
import com.kutumbam.app.locker.Retrieval
import com.kutumbam.app.locker.Retrieved
import com.kutumbam.app.locker.Source
import com.kutumbam.app.locker.SourceKind
import com.kutumbam.app.parse.DoseUnits
import com.kutumbam.app.parse.FrequencyCode
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import com.kutumbam.app.parse.TestNames
import com.kutumbam.app.parse.describeRange
import com.kutumbam.app.parse.formatNumber
import com.kutumbam.app.ui.isChildWithDob
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Bridges existing repository, database entities, and locker retrieval into
 * clean, presentation-ready Elder Mode models.
 * Strictly read-only over existing business logic: zero duplicate schemas or queries.
 */
object ElderDataAdapter {

    /** Maps raw database entities and dose logs into today's [ElderDose] items. */
    fun buildElderDoses(
        medicines: List<MedicineEntity>,
        logs: List<DoseLog>,
        today: LocalDate = LocalDate.now(),
    ): List<ElderDose> {
        val takenMap = logs.filter { it.date == today.toString() }
            .associateBy { "${it.medicineId}-${it.time}" }

        val result = mutableListOf<ElderDose>()
        for (m in medicines) {
            val times = m.timesCsv.split(",").filter { it.isNotBlank() }.mapNotNull {
                runCatching { LocalTime.parse(it) }.getOrNull()
            }
            val meal = runCatching { MealTiming.valueOf(m.mealTiming) }.getOrDefault(MealTiming.UNSPECIFIED)
            val units = DoseUnits.fromCsv(m.unitsCsv, times.size)

            times.forEachIndexed { index, time ->
                val key = "${m.id}-$time"
                val log = takenMap[key]
                val status = when (log?.status) {
                    "taken" -> DoseStatus.TAKEN
                    "skipped" -> DoseStatus.SKIPPED
                    "later" -> DoseStatus.REMIND_LATER
                    else -> DoseStatus.NOT_TAKEN
                }
                val unitCount = units.getOrElse(index) { 1.0 }
                val instr = buildDoseInstruction(meal, unitCount, m.form)

                result.add(
                    ElderDose(
                        medicineId = m.id,
                        time = time,
                        name = m.name,
                        strength = m.strength.orEmpty(),
                        meal = meal,
                        instruction = instr,
                        status = status,
                        units = unitCount,
                        form = m.form,
                    )
                )
            }
        }
        return result.sortedBy { it.time }
    }

    /** Finds the next pending medicine dose for today relative to [now]. */
    fun findNextDose(doses: List<ElderDose>, now: LocalTime = LocalTime.now()): Pair<ElderDose?, String> {
        val pending = doses.filterNot { it.isTaken }
        if (pending.isEmpty()) return null to ""

        // Find the first pending dose at or after current time
        val upcoming = pending.firstOrNull { it.time >= now.minusMinutes(15) } ?: pending.first()
        val minutesUntil = ChronoUnit.MINUTES.between(now, upcoming.time)

        val countdown = when {
            minutesUntil in -15..15 -> "Due now"
            minutesUntil < -15 -> "Overdue"
            minutesUntil in 16..60 -> "In $minutesUntil mins"
            else -> {
                val hours = minutesUntil / 60
                val mins = minutesUntil % 60
                if (mins == 0L) "In $hours hrs" else "In ${hours}h ${mins}m"
            }
        }
        return upcoming to countdown
    }

    /** Maps lab values into elder health items. */
    fun buildHealthItems(labs: List<LabValueEntity>): List<ElderHealthItem> {
        if (labs.isEmpty()) return emptyList()
        val byKey = labs.groupBy { TestNames.key(it.testName) }

        return byKey.map { (_, rows) ->
            val sorted = rows.sortedBy { it.date }
            val latest = sorted.last()
            val prev = if (sorted.size > 1) sorted[sorted.size - 2] else null

            val status = if (latest.rangeSource == "none") RangeStatus.NO_RANGE
            else RangeCheck.status(latest.value, latest.printedRangeLow, latest.printedRangeHigh)
            val isFlagged = status == RangeStatus.ABOVE || status == RangeStatus.BELOW

            val rangeDesc = describeRange(latest.printedRangeLow, latest.printedRangeHigh, latest.unit)
            val valText = listOfNotNull(formatNumber(latest.value), latest.unit).joinToString(" ")
            val prevValText = prev?.let { listOfNotNull(formatNumber(it.value), it.unit).joinToString(" ") }

            val trend = if (prev != null) {
                when {
                    latest.value > prev.value -> "↑ Higher than previous ($prevValText)"
                    latest.value < prev.value -> "↓ Lower than previous ($prevValText)"
                    else -> "= Same as previous ($prevValText)"
                }
            } else null

            ElderHealthItem(
                testName = latest.testName,
                value = latest.value,
                valueText = valText,
                unit = latest.unit,
                rangeText = rangeDesc,
                isFlagged = isFlagged,
                date = latest.date,
                previousValueText = prevValText,
                trendText = trend,
            )
        }.sortedByDescending { it.isFlagged }
    }

    /** Queries stored records via existing deterministic [Retrieval] engine. */
    suspend fun answerQuestionFromLocker(
        question: String,
        member: FamilyMember,
        repo: Repository,
    ): Pair<String, String> {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        val docs = repo.documentsNow(member.id).associateBy { it.id }

        val meds = repo.medicinesNow(member.id).map { m ->
            val stamp = docs[m.documentId]?.captureDate ?: m.startDate
            val times = m.timesCsv.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it) }
            MedRecord(
                name = m.name,
                strength = m.strength,
                times = times,
                sos = m.frequencyCode == FrequencyCode.SOS.name,
                meal = runCatching { MealTiming.valueOf(m.mealTiming) }.getOrDefault(MealTiming.UNSPECIFIED),
                durationDays = m.durationDays,
                startDate = LocalDate.parse(m.startDate),
                source = Source(SourceKind.PRESCRIPTION, "Prescription · ${LocalDate.parse(stamp).format(fmt)}", m.documentId),
                supply = null,
                units = DoseUnits.fromCsv(m.unitsCsv, times.size),
            )
        }

        val labs = repo.labsNow(member.id).map { l ->
            val date = LocalDate.parse(l.date)
            LabRecord(
                testName = l.testName,
                key = TestNames.key(l.testName),
                value = l.value,
                unit = l.unit,
                low = l.printedRangeLow,
                high = l.printedRangeHigh,
                standardReference = l.rangeSource == "standard",
                date = date,
                source = Source(SourceKind.LAB_REPORT, "Lab report · ${date.format(fmt)}", l.documentId),
            )
        }

        val plan = if (member.isChildWithDob()) {
            val dob = LocalDate.parse(member.dateOfBirth)
            ImmunizationEngine.plan(dob, repo.immunizationsNow(member.id).associate { it.scheduleId to LocalDate.parse(it.administeredDate) }, today)
        } else null

        val lockerData = LockerData(
            person = member.name,
            today = today,
            medicines = meds,
            labs = labs,
            immunization = plan,
            self = member.isSelf,
            readings = repo.vitalsNow(member.id).mapNotNull { it.toReading() },
            limits = repo.targetsNow(member.id).mapNotNull { t -> t.high?.let { t.key to it } }.toMap(),
        )

        val retrieved: Retrieved = Retrieval.retrieve(question, lockerData)
        return if (retrieved.advice) {
            AnswerRules.refusal(member.name, member.isSelf) to "Medical Safety Advice"
        } else {
            val sourceLabel = retrieved.sources.firstOrNull()?.label ?: "Local Health Records"
            retrieved.directAnswer to sourceLabel
        }
    }

    private fun buildDoseInstruction(meal: MealTiming, units: Double, form: String?): String {
        val countText = when {
            units == 0.5 -> "½ tablet"
            units == 1.0 -> ""
            units == 1.5 -> "1½ tablets"
            units % 1.0 == 0.0 -> "${units.toInt()} tablets"
            else -> "$units tablets"
        }
        val mealText = when (meal) {
            MealTiming.BEFORE_FOOD -> "Before food"
            MealTiming.AFTER_FOOD -> "After food"
            MealTiming.WITH_FOOD -> "With food"
            MealTiming.EMPTY_STOMACH -> "Empty stomach"
            MealTiming.UNSPECIFIED -> ""
        }
        return listOf(countText, mealText).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

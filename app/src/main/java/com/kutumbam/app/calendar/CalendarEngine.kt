package com.kutumbam.app.calendar

import com.kutumbam.app.data.DoseLog
import com.kutumbam.app.data.FamilyMember
import com.kutumbam.app.data.FollowUpVisit
import com.kutumbam.app.data.ImmunizationRecord
import com.kutumbam.app.data.MedicineEntity
import com.kutumbam.app.parse.DoseUnits
import com.kutumbam.app.parse.FrequencyCode
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.RefillPredictor
import com.kutumbam.app.parse.RefillStatus
import com.kutumbam.app.parse.VaccineStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CalendarEventType {
    DOSE,
    REFILL,
    DOCTOR_VISIT,
    VACCINE_VISIT,
    COURSE_END,
}

data class CalendarItem(
    val id: String,
    val date: LocalDate,
    val type: CalendarEventType,
    val memberId: Long,
    val memberName: String,
    val memberRelation: String,
    val isElder: Boolean,
    val isChild: Boolean,
    val isSelf: Boolean,
    val title: String,
    val subtitle: String,
    val time: LocalTime? = null,
    val timeText: String = "",
    val isUrgent: Boolean = false,
    val medicineId: Long? = null,
    val visitId: Long? = null,
    val isTaken: Boolean = false,
    val tag: String = "",
)

data class DayIndicators(
    val hasDose: Boolean = false,
    val hasRefill: Boolean = false,
    val hasVisit: Boolean = false,
    val hasVaccine: Boolean = false,
    val totalCount: Int = 0,
)

object CalendarEngine {
    private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun generate(
        members: List<FamilyMember>,
        medicines: List<MedicineEntity>,
        doseLogs: List<DoseLog>,
        visits: List<FollowUpVisit>,
        immunizations: Map<Long, List<ImmunizationRecord>>,
        today: LocalDate,
        from: LocalDate,
        to: LocalDate,
        filterMemberId: Long? = null,
    ): Map<LocalDate, List<CalendarItem>> {
        val targetMembers = if (filterMemberId == null) members else members.filter { it.id == filterMemberId }
        val memberMap = targetMembers.associateBy { it.id }
        val memberMeds = medicines.filter { it.memberId in memberMap.keys }
        val memberVisits = visits.filter { it.memberId in memberMap.keys }

        val items = mutableListOf<CalendarItem>()

        // 1. Doses & Course Ends
        memberMeds.forEach { med ->
            val member = memberMap[med.memberId] ?: return@forEach
            val (isElder, isChild) = memberRoles(member)
            val start = runCatching { LocalDate.parse(med.startDate) }.getOrNull() ?: today
            val duration = med.durationDays
            val courseEnd = duration?.let { start.plusDays(it.toLong()) }

            // Course end notice
            if (courseEnd != null && !courseEnd.isBefore(from) && !courseEnd.isAfter(to)) {
                items += CalendarItem(
                    id = "course-end-${med.id}-$courseEnd",
                    date = courseEnd,
                    type = CalendarEventType.COURSE_END,
                    memberId = member.id,
                    memberName = displayName(member),
                    memberRelation = member.relation,
                    isElder = isElder,
                    isChild = isChild,
                    isSelf = member.isSelf,
                    title = "Course completes: ${med.name}${med.strength?.let { " $it" }.orEmpty()}",
                    subtitle = "Finished ${med.durationDays}-day course for ${displayName(member)}",
                    isUrgent = false,
                    medicineId = med.id,
                    tag = "Completed",
                )
            }

            if (med.frequencyCode != FrequencyCode.SOS.name) {
                val times = med.timesCsv.split(",").mapNotNull { t ->
                    t.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                }.sorted()

                val units = DoseUnits.fromCsv(med.unitsCsv, times.size)

                // Walk days in window
                var d = from
                while (!d.isAfter(to)) {
                    val inRange = !d.isBefore(start) && (courseEnd == null || d.isBefore(courseEnd))
                    if (inRange) {
                        times.forEachIndexed { idx, t ->
                            val unitVal = units.getOrNull(idx) ?: 1.0
                            val unitStr = DoseUnits.label(unitVal)
                            val mealStr = if (med.mealTiming.isNotBlank() && med.mealTiming != "unspecified") " · ${med.mealTiming}" else ""
                            val doseInstruction = "$unitStr ${med.form?.lowercase() ?: "dose"}$mealStr"

                            val taken = if (d == today) {
                                doseLogs.any { it.medicineId == med.id && it.time == t.toString() && it.status == "taken" }
                            } else false

                            items += CalendarItem(
                                id = "dose-${med.id}-$d-$t",
                                date = d,
                                type = CalendarEventType.DOSE,
                                memberId = member.id,
                                memberName = displayName(member),
                                memberRelation = member.relation,
                                isElder = isElder,
                                isChild = isChild,
                                isSelf = member.isSelf,
                                title = "${med.name}${med.strength?.let { " $it" }.orEmpty()}",
                                subtitle = "$doseInstruction · ${displayName(member)}",
                                time = t,
                                timeText = t.format(TIME_FMT),
                                isUrgent = false,
                                medicineId = med.id,
                                isTaken = taken,
                                tag = if (taken) "Taken" else t.format(TIME_FMT),
                            )
                        }
                    }
                    d = d.plusDays(1)
                }
            }
        }

        // 2. Refills
        memberMeds.forEach { med ->
            val member = memberMap[med.memberId] ?: return@forEach
            val (isElder, isChild) = memberRoles(member)
            val stock = med.stockCount ?: return@forEach
            val asOf = med.stockAsOf?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return@forEach
            val times = med.timesCsv.split(",").mapNotNull { t ->
                t.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            }
            val units = DoseUnits.fromCsv(med.unitsCsv, times.size)
            val slots = RefillPredictor.slots(med.frequencyCode, times, units)
            val start = runCatching { LocalDate.parse(med.startDate) }.getOrNull() ?: today
            val courseEnd = med.durationDays?.let { start.plusDays(it.toLong()) }

            val estimate = RefillPredictor.estimate(stock, asOf, slots, courseEnd, LocalDateTime.now())
            if (estimate != null) {
                val runOut = estimate.runOut
                // Add event on the run-out date
                if (!runOut.isBefore(from) && !runOut.isAfter(to)) {
                    val isPastOrToday = !runOut.isAfter(today)
                    items += CalendarItem(
                        id = "refill-out-${med.id}-$runOut",
                        date = runOut,
                        type = CalendarEventType.REFILL,
                        memberId = member.id,
                        memberName = displayName(member),
                        memberRelation = member.relation,
                        isElder = isElder,
                        isChild = isChild,
                        isSelf = member.isSelf,
                        title = "Refill: ${med.name}",
                        subtitle = if (isPastOrToday) "Supply running out today for ${displayName(member)}"
                        else "Supply predicted to run out on ${runOut.format(DATE_FMT)}",
                        isUrgent = true,
                        medicineId = med.id,
                        tag = "Refill",
                    )
                }

                // Add advance notice (3 days prior) if within view
                val leadDate = runOut.minusDays(RefillPredictor.LEAD_DAYS)
                if (leadDate.isAfter(today) && !leadDate.isBefore(from) && !leadDate.isAfter(to)) {
                    items += CalendarItem(
                        id = "refill-warn-${med.id}-$leadDate",
                        date = leadDate,
                        type = CalendarEventType.REFILL,
                        memberId = member.id,
                        memberName = displayName(member),
                        memberRelation = member.relation,
                        isElder = isElder,
                        isChild = isChild,
                        isSelf = member.isSelf,
                        title = "Order Refill: ${med.name}",
                        subtitle = "Runs out in 3 days (${runOut.format(DATE_FMT)}) for ${displayName(member)}",
                        isUrgent = false,
                        medicineId = med.id,
                        tag = "Refill Soon",
                    )
                }
            }
        }

        // 3. Follow-Up Visits (Doctor / Clinic appointments)
        memberVisits.forEach { v ->
            val member = memberMap[v.memberId] ?: return@forEach
            val (isElder, isChild) = memberRoles(member)
            val vDate = runCatching { LocalDate.parse(v.date) }.getOrNull() ?: return@forEach
            if (!vDate.isBefore(from) && !vDate.isAfter(to)) {
                val vTime = v.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                val timeStr = vTime?.format(TIME_FMT) ?: "Any time"
                val reasonStr = v.reason?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                items += CalendarItem(
                    id = "visit-${v.id}-$vDate",
                    date = vDate,
                    type = CalendarEventType.DOCTOR_VISIT,
                    memberId = member.id,
                    memberName = displayName(member),
                    memberRelation = member.relation,
                    isElder = isElder,
                    isChild = isChild,
                    isSelf = member.isSelf,
                    title = "Follow-up: ${v.doctorOrClinic}",
                    subtitle = "$timeStr$reasonStr · ${displayName(member)}",
                    time = vTime,
                    timeText = vTime?.format(TIME_FMT) ?: "",
                    isUrgent = vDate == today,
                    visitId = v.id,
                    tag = "Doctor Visit",
                )
            }
        }

        // 4. Child Vaccination Milestones (Pediatric Follow-up Visits)
        targetMembers.forEach { member ->
            val dob = member.dateOfBirth?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (member.relation.equals("Child", ignoreCase = true) && dob != null) {
                val given = (immunizations[member.id] ?: emptyList()).associate {
                    it.scheduleId to (runCatching { LocalDate.parse(it.administeredDate) }.getOrNull() ?: today)
                }
                val plan = ImmunizationEngine.plan(dob, given, today)
                plan.forEach { milestoneState ->
                    val dueDate = milestoneState.dueDate
                    if (!dueDate.isBefore(from) && !dueDate.isAfter(to) && milestoneState.status != VaccineStatus.DONE) {
                        items += CalendarItem(
                            id = "vaccine-${member.id}-${milestoneState.milestone.name}-$dueDate",
                            date = dueDate,
                            type = CalendarEventType.VACCINE_VISIT,
                            memberId = member.id,
                            memberName = displayName(member),
                            memberRelation = member.relation,
                            isElder = false,
                            isChild = true,
                            isSelf = false,
                            title = "Vaccine: ${milestoneState.pendingTitle}",
                            subtitle = "${milestoneState.milestone.ageLabel} milestone for ${displayName(member)}",
                            isUrgent = milestoneState.status == VaccineStatus.OVERDUE,
                            tag = if (milestoneState.status == VaccineStatus.OVERDUE) "Overdue Vaccine" else "Vaccine Visit",
                        )
                    }
                }
            }
        }

        return items.groupBy { it.date }.mapValues { (_, dayItems) ->
            dayItems.sortedWith(
                compareBy<CalendarItem> {
                    when (it.type) {
                        CalendarEventType.DOCTOR_VISIT, CalendarEventType.VACCINE_VISIT -> 0
                        CalendarEventType.REFILL -> 1
                        CalendarEventType.COURSE_END -> 2
                        CalendarEventType.DOSE -> 3
                    }
                }.thenBy { it.time ?: LocalTime.MIDNIGHT }.thenBy { it.title }
            )
        }
    }

    fun buildIndicators(eventsByDate: Map<LocalDate, List<CalendarItem>>): Map<LocalDate, DayIndicators> {
        return eventsByDate.mapValues { (_, items) ->
            DayIndicators(
                hasDose = items.any { it.type == CalendarEventType.DOSE },
                hasRefill = items.any { it.type == CalendarEventType.REFILL },
                hasVisit = items.any { it.type == CalendarEventType.DOCTOR_VISIT || it.type == CalendarEventType.COURSE_END },
                hasVaccine = items.any { it.type == CalendarEventType.VACCINE_VISIT },
                totalCount = items.size,
            )
        }
    }

    private fun displayName(m: FamilyMember): String = if (m.isSelf) "You" else m.name

    private fun memberRoles(m: FamilyMember): Pair<Boolean, Boolean> {
        val rel = m.relation.lowercase()
        val isElder = rel in listOf("mother", "father", "grandparent") || (!m.selfOperatesPhone && !m.isSelf && rel != "child")
        val isChild = rel == "child"
        return isElder to isChild
    }
}

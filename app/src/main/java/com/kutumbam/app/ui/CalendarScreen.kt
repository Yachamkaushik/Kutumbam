package com.kutumbam.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.calendar.CalendarEventType
import com.kutumbam.app.calendar.CalendarItem
import com.kutumbam.app.calendar.DayIndicators
import com.kutumbam.app.data.FamilyMember
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val SIDE = 20.dp

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val ui by vm.calendar.collectAsState()
    var showAddVisit by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding()) {
        // Top header
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { vm.back() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(KIcons.Back, "Back", Modifier.size(22.dp), tint = K.Ink)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Household Calendar", fontFamily = K.Display, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                Text("One merged view for all family members", fontSize = 12.sp, color = K.Muted)
            }
            OutlinedButton(
                onClick = { showAddVisit = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, K.Teal),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = K.Teal),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Icon(KIcons.Plus, null, Modifier.size(14.dp))
                Text(" Visit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Family member filter pills
        CalendarMemberFilter(ui.members, ui.filterMemberId) { vm.filterCalendarMember(it) }

        // Main scrollable body
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SIDE)) {
            Spacer(Modifier.height(10.dp))

            // Month navigation & calendar card
            MonthCalendarCard(
                month = ui.currentMonth,
                selectedDate = ui.selectedDate,
                indicators = ui.indicators,
                onPrev = { vm.prevCalendarMonth() },
                onNext = { vm.nextCalendarMonth() },
                onSelectDate = { vm.selectCalendarDate(it) },
            )

            Spacer(Modifier.height(18.dp))

            // Day's agenda
            DayAgendaSection(
                selectedDate = ui.selectedDate,
                events = ui.selectedDayEvents,
                onToggleDose = { vm.toggleCalendarDose(it) },
                onOpenVisitPrep = { vm.openVisit() },
                onDeleteVisit = { it.visitId?.let(vm::deleteFollowUpVisit) },
                onAddVisit = { showAddVisit = true },
            )

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showAddVisit) {
        AddVisitDialog(
            members = ui.members,
            initialDate = ui.selectedDate,
            onDismiss = { showAddVisit = false },
            onAdd = { memberId, doctor, date, time, reason ->
                showAddVisit = false
                vm.addFollowUpVisit(memberId, doctor, date, time, reason)
            },
        )
    }
}

@Composable
private fun CalendarMemberFilter(members: List<FamilyMember>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SIDE, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val allSelected = selectedId == null
        Box(
            Modifier.clip(RoundedCornerShape(18.dp))
                .background(if (allSelected) K.Teal else K.Card)
                .border(1.dp, if (allSelected) K.Teal else K.Border, RoundedCornerShape(18.dp))
                .clickable { onSelect(null) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                "All Family",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (allSelected) Color.White else K.Ink,
            )
        }

        members.forEach { m ->
            val sel = m.id == selectedId
            val label = if (m.isSelf) "You" else m.name
            val role = when {
                m.relation.equals("Child", ignoreCase = true) -> "Child"
                m.relation.equals("Mother", ignoreCase = true) || m.relation.equals("Father", ignoreCase = true) || m.relation.equals("Grandparent", ignoreCase = true) -> "Elder"
                else -> m.relation
            }
            Box(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (sel) K.Teal else K.Card)
                    .border(1.dp, if (sel) K.Teal else K.Border, RoundedCornerShape(18.dp))
                    .clickable { onSelect(m.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "$label ($role)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sel) Color.White else K.Ink,
                )
            }
        }
    }
}

@Composable
private fun MonthCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    indicators: Map<LocalDate, DayIndicators>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Card {
        Column(Modifier.padding(14.dp)) {
            // Month Header with Prev/Next
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    fontFamily = K.Display,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.Ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(KIcons.Back, "Previous Month", Modifier.size(16.dp), tint = K.Muted)
                }
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(KIcons.Chevron, "Next Month", Modifier.size(16.dp), tint = K.Muted)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Day of week headers
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = K.Muted,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Calendar grid
            val firstDayOfMonth = month.atDay(1)
            val daysInMonth = month.lengthOfMonth()
            val startDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1 = Monday, 7 = Sunday
            val leadEmptySlots = startDayOfWeek - 1

            val totalSlots = ((leadEmptySlots + daysInMonth + 6) / 7) * 7
            val today = LocalDate.now()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until (totalSlots / 7)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        for (col in 0..6) {
                            val slotIndex = row * 7 + col
                            val dayNum = slotIndex - leadEmptySlots + 1
                            if (dayNum in 1..daysInMonth) {
                                val date = month.atDay(dayNum)
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                val ind = indicators[date]

                                DayCell(
                                    dayNum = dayNum,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    indicators = ind,
                                    onClick = { onSelectDate(date) },
                                )
                            } else {
                                Box(Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }

            // Legend below grid
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(K.Teal, "Doses")
                Spacer(Modifier.width(14.dp))
                LegendDot(K.WarnIcon, "Refills")
                Spacer(Modifier.width(14.dp))
                LegendDot(K.Green, "Visits / Vaccines")
            }
        }
    }
}

@Composable
private fun DayCell(
    dayNum: Int,
    isSelected: Boolean,
    isToday: Boolean,
    indicators: DayIndicators?,
    onClick: () -> Unit,
) {
    Column(
        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) K.Teal else if (isToday) K.TealTint else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) K.Teal else if (isToday) K.Teal.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$dayNum",
            fontSize = 13.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else if (isToday) K.Teal else K.Ink,
        )
        // Indicator dots
        Row(
            Modifier.height(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (indicators?.hasVisit == true || indicators?.hasVaccine == true) {
                Box(Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) Color.White else K.Green))
            }
            if (indicators?.hasRefill == true) {
                Box(Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) Color.White else K.WarnIcon))
            }
            if (indicators?.hasDose == true) {
                Box(Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) Color.White else K.Teal))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 11.sp, color = K.Muted)
    }
}

@Composable
private fun DayAgendaSection(
    selectedDate: LocalDate,
    events: List<CalendarItem>,
    onToggleDose: (CalendarItem) -> Unit,
    onOpenVisitPrep: () -> Unit,
    onDeleteVisit: (CalendarItem) -> Unit,
    onAddVisit: () -> Unit,
) {
    val today = LocalDate.now()
    val isToday = selectedDate == today
    val dayTitle = when {
        isToday -> "Today · " + selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
        selectedDate == today.plusDays(1) -> "Tomorrow · " + selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
        else -> selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    }

    val visits = events.filter { it.type == CalendarEventType.DOCTOR_VISIT || it.type == CalendarEventType.VACCINE_VISIT || it.type == CalendarEventType.COURSE_END }
    val refills = events.filter { it.type == CalendarEventType.REFILL }
    val doses = events.filter { it.type == CalendarEventType.DOSE }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dayTitle, fontFamily = K.Display, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                val summary = listOfNotNull(
                    if (visits.isNotEmpty()) "${visits.size} ${if (visits.size == 1) "visit" else "visits"}" else null,
                    if (refills.isNotEmpty()) "${refills.size} refill" else null,
                    if (doses.isNotEmpty()) "${doses.size} doses" else null,
                ).joinToString(" · ")
                Text(summary.ifEmpty { "Nothing scheduled" }, fontSize = 12.sp, color = K.Muted)
            }
        }

        if (events.isEmpty()) {
            Card {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No doses, refills or visits scheduled", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                    Text("Prescription doses, predicted refills, and doctor follow-up dates appear here automatically.", fontSize = 12.sp, color = K.Muted, textAlign = TextAlign.Center)
                    TextButton(onClick = onAddVisit) {
                        Icon(KIcons.Plus, null, Modifier.size(16.dp), tint = K.Teal)
                        Text("  Schedule a visit for this day", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.Teal)
                    }
                }
            }
        }

        // 1. Visits & Vaccines
        if (visits.isNotEmpty()) {
            Text(
                "FOLLOW-UP VISITS & MILESTONES",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
                color = K.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            visits.forEach { v ->
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(K.TealTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (v.type == CalendarEventType.VACCINE_VISIT) KIcons.Heart else KIcons.Clipboard,
                                null,
                                Modifier.size(18.dp),
                                tint = K.Teal,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(v.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = K.Ink, modifier = Modifier.weight(1f))
                                Box(
                                    Modifier.clip(RoundedCornerShape(6.dp)).background(K.TealTint).padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(v.memberName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = K.Teal)
                                }
                            }
                            Text(v.subtitle, fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 2.dp))

                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (v.type == CalendarEventType.DOCTOR_VISIT) {
                                    TextButton(onClick = onOpenVisitPrep, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Open visit prep", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = K.Teal)
                                    }
                                    TextButton(onClick = { onDeleteVisit(v) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Remove", fontSize = 12.sp, color = K.Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Refills
        if (refills.isNotEmpty()) {
            Text(
                "REFILL ALERTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
                color = K.WarnText,
                modifier = Modifier.padding(top = 4.dp),
            )
            refills.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(K.WarnBg).border(1.dp, K.WarnBorder, RoundedCornerShape(14.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(KIcons.Alert, null, Modifier.size(18.dp), tint = K.WarnIcon)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.WarnText, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(r.memberName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = K.WarnText)
                            }
                        }
                        Text(r.subtitle, fontSize = 12.sp, color = K.WarnText, lineHeight = 17.sp)
                    }
                }
            }
        }

        // 3. Doses
        if (doses.isNotEmpty()) {
            Text(
                "SCHEDULED MEDICINES",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
                color = K.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            Card {
                Column {
                    doses.forEachIndexed { idx, dose ->
                        if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(K.Divider))
                        CalendarDoseRow(dose, isToday = isToday) { onToggleDose(dose) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDoseRow(item: CalendarItem, isToday: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = isToday, onClick = onToggle).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(64.dp).clip(RoundedCornerShape(8.dp)).background(K.TealTint).padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.timeText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = K.Teal, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isTaken) K.Muted else K.Ink,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(K.Divider).padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(item.memberName, fontSize = 10.sp, color = K.Ink)
                }
            }
            Text(item.subtitle, fontSize = 11.sp, color = K.Muted, modifier = Modifier.padding(top = 1.dp))
        }
        if (isToday) {
            Box(Modifier.size(36.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (item.isTaken) Icon(KIcons.CheckCircle, "Taken", Modifier.size(20.dp), tint = K.Green)
                else Box(Modifier.size(18.dp).border(2.dp, K.Ring, CircleShape))
            }
        }
    }
}

@Composable
private fun AddVisitDialog(
    members: List<FamilyMember>,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (Long, String, LocalDate, LocalTime?, String?) -> Unit,
) {
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id ?: 0L) }
    var doctorOrClinic by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf(initialDate.toString()) }
    var timeStr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    val dateParsed = runCatching { LocalDate.parse(dateStr) }.getOrNull()
    val timeParsed = timeStr.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val isValid = doctorOrClinic.isNotBlank() && dateParsed != null && (timeStr.isBlank() || timeParsed != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Follow-Up Visit", fontFamily = K.Display, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Family Member", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = K.Muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.forEach { m ->
                        FilterChip(
                            selected = m.id == selectedMemberId,
                            onClick = { selectedMemberId = m.id },
                            label = { Text(if (m.isSelf) "You" else m.name) },
                        )
                    }
                }

                OutlinedTextField(
                    value = doctorOrClinic,
                    onValueChange = { doctorOrClinic = it },
                    label = { Text("Doctor or Clinic (e.g. Dr. Sharma)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    isError = dateParsed == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { timeStr = it },
                    label = { Text("Time (HH:mm, optional e.g. 10:30)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (e.g. 3-month review, BP check)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (dateParsed != null) {
                        onAdd(selectedMemberId, doctorOrClinic, dateParsed, timeParsed, reason)
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = K.Teal, contentColor = Color.White),
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Preview(showBackground = true)
@Composable
internal fun CalendarScreenPreview() {
    val today = LocalDate.now()
    val sampleEvents = listOf(
        CalendarItem(
            id = "1", date = today, type = CalendarEventType.DOCTOR_VISIT, memberId = 1L,
            memberName = "Amma", memberRelation = "Mother", isElder = true, isChild = false, isSelf = false,
            title = "Follow-up: Dr. Sharma (Cardiology)", subtitle = "10:30 AM · 3-month BP review · Amma",
            time = LocalTime.of(10, 30), timeText = "10:30 AM", tag = "Doctor Visit",
        ),
        CalendarItem(
            id = "2", date = today, type = CalendarEventType.REFILL, memberId = 1L,
            memberName = "Amma", memberRelation = "Mother", isElder = true, isChild = false, isSelf = false,
            title = "Refill: Telmisartan 40 mg", subtitle = "Supply running out today for Amma",
            isUrgent = true, tag = "Refill",
        ),
        CalendarItem(
            id = "3", date = today, type = CalendarEventType.DOSE, memberId = 1L,
            memberName = "Amma", memberRelation = "Mother", isElder = true, isChild = false, isSelf = false,
            title = "Telmisartan 40 mg", subtitle = "1 tablet · after food · Amma",
            time = LocalTime.of(8, 0), timeText = "8:00 AM", isTaken = true,
        ),
        CalendarItem(
            id = "4", date = today, type = CalendarEventType.DOSE, memberId = 2L,
            memberName = "You", memberRelation = "Self", isElder = false, isChild = false, isSelf = true,
            title = "Multivitamin", subtitle = "1 capsule · with food · You",
            time = LocalTime.of(9, 0), timeText = "9:00 AM", isTaken = false,
        ),
    )
    val indicators = mapOf(
        today to DayIndicators(hasDose = true, hasRefill = true, hasVisit = true, totalCount = 4),
    )

    com.kutumbam.app.ui.theme.KutumbamTheme {
        Column(Modifier.fillMaxSize().background(K.Bg).padding(16.dp)) {
            MonthCalendarCard(
                month = YearMonth.now(),
                selectedDate = today,
                indicators = indicators,
                onPrev = {},
                onNext = {},
                onSelectDate = {},
            )
            Spacer(Modifier.height(16.dp))
            DayAgendaSection(
                selectedDate = today,
                events = sampleEvents,
                onToggleDose = {},
                onOpenVisitPrep = {},
                onDeleteVisit = {},
                onAddVisit = {},
            )
        }
    }
}


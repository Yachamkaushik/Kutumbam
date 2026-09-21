package com.kutumbam.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutumbam.app.KutumbamApp
import com.kutumbam.app.data.ConfirmedLab
import com.kutumbam.app.data.ConfirmedMedicine
import com.kutumbam.app.data.FamilyMember
import com.kutumbam.app.data.MedicineEntity
import com.kutumbam.app.data.ReportSummary
import com.kutumbam.app.data.Measurement
import com.kutumbam.app.data.refill
import com.kutumbam.app.visit.GrowthPoint
import com.kutumbam.app.visit.PrepInput
import com.kutumbam.app.visit.PrepSheet
import com.kutumbam.app.visit.VisitPrep
import com.kutumbam.app.locker.SupplyInfo
import com.kutumbam.app.parse.RefillText
import com.kutumbam.app.data.doseSlots
import com.kutumbam.app.parse.DoseUnits
import com.kutumbam.app.parse.DuplicateCheck
import com.kutumbam.app.parse.DuplicateWarning
import com.kutumbam.app.parse.MedRef
import com.kutumbam.app.parse.RefillPredictor
import com.kutumbam.app.parse.RefillStatus
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import com.kutumbam.app.data.LabValueEntity
import com.kutumbam.app.llm.Prompts
import com.kutumbam.app.llm.generate
import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import com.kutumbam.app.parse.TestNames
import com.kutumbam.app.parse.TrendPoint
import com.kutumbam.app.parse.TrendSummary
import com.kutumbam.app.parse.describeRange
import com.kutumbam.app.parse.formatNumber
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.kutumbam.app.parse.DocumentParser
import com.kutumbam.app.locker.AnswerRules
import com.kutumbam.app.locker.LabRecord
import com.kutumbam.app.locker.LockerData
import com.kutumbam.app.locker.MedRecord
import com.kutumbam.app.locker.Retrieval
import com.kutumbam.app.locker.Source
import com.kutumbam.app.locker.SourceKind
import com.kutumbam.app.llm.LlmBackend
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.MilestoneState
import com.kutumbam.app.parse.UipSchedule
import com.kutumbam.app.parse.VaccineStatus
import com.kutumbam.app.data.ConfirmedVaccine
import com.kutumbam.app.reminder.ReminderScheduler
import com.kutumbam.app.parse.DocumentType
import com.kutumbam.app.parse.FrequencyCode
import com.kutumbam.app.parse.FrequencyParser
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.parse.ParsedLabValue
import com.kutumbam.app.speech.AppLanguage
import com.kutumbam.app.speech.ScriptDose
import com.kutumbam.app.speech.SpeakResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Screen { HOME, CONFIRM, ELDER, REPORT, TREND, CHILD, ASK, DEV, VISIT }

data class DoseRow(
    val medicineId: Long,
    val time: LocalTime,
    val name: String,
    val instruction: String,
    val taken: Boolean,
    val meal: MealTiming = MealTiming.UNSPECIFIED,
    val units: Double = 1.0,
    val form: String? = null,
) {
    val timeText: String get() = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}

data class AlertInfo(val text: String, val documentId: Long)

/** One medicine's supply on the home screen. [count] is what the family last counted; null means not set yet. */
data class SupplyRow(val medicineId: Long, val name: String, val text: String, val urgent: Boolean, val count: Int?, val suggested: Int? = null, val suggestedNote: String? = null)

data class HomeUi(
    val members: List<FamilyMember> = emptyList(),
    val selected: FamilyMember? = null,
    val doses: List<DoseRow> = emptyList(),
    val alert: AlertInfo? = null,
    val reports: List<ReportSummary> = emptyList(),
    val supply: List<SupplyRow> = emptyList(),
    val duplicates: List<DuplicateWarning> = emptyList(),
)

data class LabRow(
    val testName: String,
    val key: String,
    val valueText: String,
    val status: RangeStatus,
    val rangeLabel: String?,
    val usedFallback: Boolean,
)

data class ReportUi(val memberId: Long, val memberName: String, val date: String, val rows: List<LabRow>) {
    val flaggedCount get() = rows.count { it.status == RangeStatus.ABOVE || it.status == RangeStatus.BELOW }
    val usedFallback get() = rows.any { it.usedFallback }
}

data class TrendUi(
    val person: String,
    val testName: String,
    val unit: String?,
    val points: List<TrendPoint>,
    val low: Double?,
    val high: Double?,
    val usedFallback: Boolean,
    val summary: String,
)

/** A medicine card on the confirm screen. Everything is editable until the user saves. */
data class EditableMed(
    val key: Int,
    val name: String,
    val strength: String,
    val form: String?,
    val frequency: FrequencyCode,
    val times: List<LocalTime>,
    val meal: MealTiming,
    val durationDays: String,
    val quantity: String = "",
    /** Tablets per dose, same order as [times]. */
    val units: List<Double> = emptyList(),
    val editing: Boolean = false,
) {
    fun unitsFull(): List<Double> = if (units.size == times.size) units else List(times.size) { 1.0 }
}

/** A vaccine dose read from a card. The date must be set before the dose can be saved. */
data class EditableVaccine(val key: Int, val scheduleId: String, val label: String, val milestone: String, val date: LocalDate?)

data class QaItem(
    val id: Long,
    val question: String,
    val answer: String,
    val sources: List<Source>,
    val thinking: Boolean,
    /** How this answer was produced, shown under it. */
    val basis: String,
)

data class AskUi(
    val memberName: String = "",
    val items: List<QaItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val listening: Boolean = false,
    val partial: String = "",
    val status: String? = null,
)

data class ChildUi(
    val member: FamilyMember,
    val dob: LocalDate,
    val ageText: String,
    val today: LocalDate,
    val milestones: List<MilestoneState>,
) {
    val overdueDoses get() = milestones.sumOf { m -> m.vaccines.count { it.status == VaccineStatus.OVERDUE } }
    val next: MilestoneState? get() = milestones.firstOrNull { it.status != VaccineStatus.DONE }
}

fun FamilyMember.isChildWithDob(today: LocalDate = LocalDate.now()): Boolean {
    val born = dateOfBirth?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return relation == "Child" || born.plusYears(18).isAfter(today)
}

data class Draft(
    val memberId: Long,
    val memberName: String,
    val imagePath: String?,
    val rawText: String,
    val type: DocumentType,
    val date: LocalDate?,
    val meds: List<EditableMed>,
    val labs: List<ParsedLabValue>,
    val vaccines: List<EditableVaccine> = emptyList(),
    /** What this person is already taking, to spot overlaps with the new prescription. */
    val existing: List<MedRef> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val kApp = app as KutumbamApp
    private val repo = kApp.repo

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen = _screen.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _draft = MutableStateFlow<Draft?>(null)
    val draft = _draft.asStateFlow()

    private val selectedId = MutableStateFlow<Long?>(null)

    /** A photo shared in from another app, waiting for the user to say whose it is. */
    private val _pendingShare = MutableStateFlow<Uri?>(null)
    val pendingShare = _pendingShare.asStateFlow()

    val home: StateFlow<HomeUi> = combine(repo.members(), selectedId) { members, sel ->
        members to (members.firstOrNull { it.id == sel } ?: members.firstOrNull())
    }.flatMapLatest { (members, member) ->
        if (member == null) flowOf(HomeUi(members))
        else {
            val today = LocalDate.now()
            combine(repo.medicines(member.id), repo.doseLogs(today), repo.latestFlagged(member.id), repo.reportSummaries(member.id)) { meds, logs, flagged, reports ->
                val taken = logs.map { Triple(it.medicineId, it.time, it.status) }.filter { it.third == "taken" }.map { it.first to it.second }.toSet()
                val doses = meds.filter { courseActive(it, today) }.flatMap { m ->
                    val times = m.timesCsv.split(",").filter { it.isNotBlank() }
                    val units = DoseUnits.fromCsv(m.unitsCsv, times.size)
                    times.mapIndexed { i, t ->
                        val count = DoseUnits.phrase(units[i], m.form)
                        DoseRow(m.id, LocalTime.parse(t), listOfNotNull(m.name, m.strength).joinToString(" "), listOfNotNull(count, mealText(m.mealTiming).ifEmpty { null }).joinToString(" · "), (m.id to t) in taken, MealTiming.valueOf(m.mealTiming), if (count != null) units[i] else 1.0, m.form)
                    }
                }.sortedBy { it.time }
                val alert = flagged?.let {
                    val basis = if (it.rangeSource == "standard") "the standard reference range (the report printed none)" else "the range printed on the report"
                    AlertInfo(
                        "${member.name}'s last ${it.testName} (${trim(it.value)}${it.unit?.let { u -> " $u" } ?: ""}) was outside $basis. " +
                            "Please talk to a doctor about it. Tap to view.",
                        it.documentId,
                    )
                }
                val now = LocalDateTime.now()
                val supply = meds.filter { courseActive(it, today) && it.doseSlots().isNotEmpty() }.map { m ->
                    val e = m.refill(now)
                    val name = listOfNotNull(m.name, m.strength).joinToString(" ")
                    val text = when {
                        e == null -> "Tablets in the pack not set. Tap to add the count."
                        else -> {
                            val head = RefillText.phrase(e).replaceFirstChar { it.uppercase() } + "."
                            val left = if (e.status == RefillStatus.COURSE_ENDS) null else RefillText.left(e)?.replaceFirstChar { it.uppercase() } + "."
                            val tail = when {
                                e.needsReminder -> "Time to get a refill."
                                e.countIsStale(now) -> "Counted ${ChronoUnit.DAYS.between(e.countedAt, now)} days ago. Tap to recount."
                                else -> null
                            }
                            listOfNotNull(head, left, tail).joinToString(" ")
                        }
                    }
                    val suggested = if (m.stockCount == null) RefillPredictor.courseSupply(m.doseSlots(), m.durationDays) else null
                    val note = suggested?.let { "the whole ${m.durationDays}-day course at ${trim(RefillPredictor.unitsPerDay(m.doseSlots()))} a day" }
                    SupplyRow(m.id, name, text, e?.needsReminder == true, m.stockCount, suggested, note)
                }.sortedWith(compareByDescending<SupplyRow> { it.urgent }.thenBy { it.name })
                val duplicates = DuplicateCheck.find(meds.filter { courseActive(it, today) }.map { MedRef(it.name, it.strength) })
                HomeUi(members, member, doses, alert, reports, supply, duplicates)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    val speaking = kApp.speaker.speaking

    private val reportDoc = MutableStateFlow<Long?>(null)
    private val trendTarget = MutableStateFlow<Pair<Long, String>?>(null)

    val report: StateFlow<ReportUi?> = reportDoc.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repo.labsForDocument(id).map { labs ->
            if (labs.isEmpty()) null
            else ReportUi(
                memberId = labs.first().memberId,
                memberName = repo.member(labs.first().memberId)?.name ?: "Family member",
                date = LocalDate.parse(labs.maxOf { it.date }).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)),
                rows = labs.map { it.toRow() },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trend: StateFlow<TrendUi?> = trendTarget.flatMapLatest { target ->
        if (target == null) flowOf(null)
        else repo.labHistory(target.first).map { all ->
            val labs = all.filter { TestNames.key(it.testName) == target.second }
            if (labs.isEmpty()) null else buildTrend(repo.member(target.first)?.name ?: "Family member", labs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary = _aiSummary.asStateFlow()
    private val _aiBusy = MutableStateFlow(false)
    val aiBusy = _aiBusy.asStateFlow()

    /** Live immunization plan for the selected member, when they are a child with a date of birth. */
    val child: StateFlow<ChildUi?> = home.map { it.selected }.distinctUntilChanged().flatMapLatest { member ->
        if (member == null || !member.isChildWithDob()) flowOf(null)
        else repo.immunizations(member.id).map { records ->
            val dob = LocalDate.parse(member.dateOfBirth)
            val today = LocalDate.now()
            ChildUi(member, dob, ImmunizationEngine.ageText(dob, today), today,
                ImmunizationEngine.plan(dob, records.associate { it.scheduleId to LocalDate.parse(it.administeredDate) }, today))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openChild() { _screen.value = Screen.CHILD }

    /** Growth entries for the selected child, oldest first. */
    val growth: StateFlow<List<Measurement>> = home.map { it.selected }.distinctUntilChanged().flatMapLatest { member ->
        if (member == null) flowOf(emptyList()) else repo.measurements(member.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMeasurement(date: LocalDate, weightKg: Double?, heightCm: Double?, headCm: Double?) {
        val member = home.value.selected ?: return
        when {
            weightKg == null && heightCm == null && headCm == null -> _message.value = "Enter at least one measurement."
            weightKg != null && weightKg !in 0.5..150.0 -> _message.value = "That weight doesn't look right. Enter it in kg."
            heightCm != null && heightCm !in 20.0..220.0 -> _message.value = "That height doesn't look right. Enter it in cm."
            headCm != null && headCm !in 20.0..70.0 -> _message.value = "That head size doesn't look right. Enter it in cm."
            else -> viewModelScope.launch { repo.addMeasurement(member.id, date, weightKg, heightCm, headCm) }
        }
    }

    fun deleteMeasurement(id: Long) { viewModelScope.launch { repo.deleteMeasurement(id) } }

    private val _visit = MutableStateFlow<PrepSheet?>(null)
    val visit = _visit.asStateFlow()
    private var visitReturn = Screen.HOME

    /** Builds the question sheet for the selected person from what is saved. Rules only, so it works with no model loaded. */
    fun openVisit() {
        val member = home.value.selected ?: run { _message.value = "Add a family member first."; return }
        visitReturn = _screen.value.takeIf { it == Screen.CHILD } ?: Screen.HOME
        _visit.value = null
        _screen.value = Screen.VISIT
        viewModelScope.launch {
            val data = buildLockerData(member)
            val isChild = member.isChildWithDob()
            _visit.value = VisitPrep.build(
                PrepInput(
                    person = member.name, isChild = isChild, dob = member.dateOfBirth?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    today = LocalDate.now(), medicines = data.medicines, labs = data.labs, immunization = if (isChild) data.immunization else null,
                    growth = repo.measurementsNow(member.id).map { GrowthPoint(LocalDate.parse(it.date), it.weightKg, it.heightCm, it.headCm) },
                ),
            )
        }
    }

    fun markVaccine(scheduleId: String, date: LocalDate) {
        val member = home.value.selected ?: return
        viewModelScope.launch { repo.markVaccine(member.id, scheduleId, date) }
    }

    fun unmarkVaccine(scheduleId: String) {
        val member = home.value.selected ?: return
        viewModelScope.launch { repo.unmarkVaccine(member.id, scheduleId) }
    }

    fun setVaccineDate(key: Int, date: LocalDate) = _draft.update { d -> d?.copy(vaccines = d.vaccines.map { if (it.key == key) it.copy(date = date) else it }) }
    fun removeVaccine(key: Int) = _draft.update { d -> d?.copy(vaccines = d.vaccines.filterNot { it.key == key }) }

    // ---- Ask the Locker

    private val _ask = MutableStateFlow(AskUi())
    val ask = _ask.asStateFlow()
    private var askMemberId: Long? = null
    private var askReturn = Screen.HOME
    private var nextQaId = 1L
    private var autoLoadTried = false
    val voiceAvailable get() = kApp.voice.isAvailable()

    /** Opens the chat for the selected member. [initialQuestion] is asked straight away (from the trend and child screens). */
    fun openAsk(initialQuestion: String? = null) {
        val member = home.value.selected ?: run { _message.value = "Add a family member first."; return }
        askReturn = _screen.value.takeIf { it in setOf(Screen.TREND, Screen.CHILD, Screen.REPORT) } ?: Screen.HOME
        if (askMemberId != member.id) { askMemberId = member.id; _ask.value = AskUi(memberName = member.name) }
        _screen.value = Screen.ASK
        viewModelScope.launch {
            val data = buildLockerData(member)
            val tips = buildList {
                if (data.medicines.isNotEmpty()) add("What medicines does ${member.name} take?")
                data.labs.lastOrNull()?.let { add("What was ${member.name}'s latest ${it.testName}?") }
                if (data.immunization != null) add("Which vaccines are due for ${member.name}?")
                if (data.medicines.isNotEmpty()) add("What does ${member.name} take in the morning?")
            }
            _ask.update { it.copy(memberName = member.name, suggestions = tips) }
        }
        initialQuestion?.let { ask(it) }
    }

    fun ask(question: String) {
        val q = question.trim()
        val member = home.value.selected ?: return
        if (q.isEmpty()) return
        val id = nextQaId++
        _ask.update { it.copy(items = it.items + QaItem(id, q, "", emptyList(), thinking = true, basis = ""), partial = "") }
        viewModelScope.launch {
            fun finish(answer: String, sources: List<Source>, basis: String) = _ask.update { a ->
                a.copy(items = a.items.map { if (it.id == id) it.copy(answer = answer, sources = sources, thinking = false, basis = basis) else it })
            }
            val data = buildLockerData(member)
            val retrieved = Retrieval.retrieve(q, data)
            if (retrieved.advice) { finish(AnswerRules.refusal(member.name), emptyList(), "Not medical advice"); return@launch }
            if (retrieved.facts.isEmpty()) { finish(retrieved.directAnswer, emptyList(), "Nothing matching in the stored records"); return@launch }

            ensureModelLoaded()
            val backend = kApp.llm.activeBackend
            if (backend == null) { finish(retrieved.directAnswer, retrieved.sources, "From the stored records · load the AI model in AI setup for fuller answers"); return@launch }

            val today = data.today.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            val language = AppLanguage.fromCode(member.preferredLanguage).voiceName
            try {
                val result = kApp.llm.generate(AnswerRules.systemPrompt(member.name, language, today), AnswerRules.userPrompt(retrieved, q)) { partial ->
                    _ask.update { a -> a.copy(items = a.items.map { if (it.id == id) it.copy(answer = partial) else it }) }
                }
                val text = result.text.trim()
                if (AnswerRules.isGrounded(text, retrieved.factsText, q, today)) finish(text, retrieved.sources, "On-device AI ($backend) from the stored records")
                else finish(retrieved.directAnswer, retrieved.sources, "From the stored records (the AI's wording didn't match them, so it isn't shown)")
            } catch (t: Throwable) {
                finish(retrieved.directAnswer, retrieved.sources, "From the stored records")
            }
        }
    }

    /** Loads a model that is already on the phone the first time it is needed, so Ask works without visiting AI setup. */
    private suspend fun ensureModelLoaded() {
        if (kApp.llm.activeBackend != null || autoLoadTried) return
        autoLoadTried = true
        val models = kApp.modelStore.list()
        val model = models.firstOrNull { it.name.contains("qualcomm", ignoreCase = true) } ?: models.firstOrNull() ?: return
        _ask.update { it.copy(status = "Loading the on-device AI. The first time takes a while…") }
        kApp.llm.load(model.absolutePath, listOf(LlmBackend.NPU, LlmBackend.GPU, LlmBackend.CPU))
        _ask.update { it.copy(status = null) }
    }

    fun startListening() {
        val member = home.value.selected ?: return
        val lang = AppLanguage.fromCode(member.preferredLanguage)
        kApp.speaker.stop()
        _ask.update { it.copy(listening = true, partial = "") }
        kApp.voice.start(
            lang,
            onPartial = { p -> _ask.update { it.copy(partial = p) } },
            onResult = { text -> _ask.update { it.copy(listening = false, partial = "") }; ask(text) },
            onError = { msg -> _ask.update { it.copy(listening = false, partial = "") }; _message.value = msg },
        )
    }

    fun stopListening() { kApp.voice.stop(); _ask.update { it.copy(listening = false, partial = "") } }

    fun speakAnswer(item: QaItem) {
        val member = home.value.selected ?: return
        val lang = AppLanguage.fromCode(member.preferredLanguage)
        if (speaking.value) { kApp.speaker.stop(); return }
        kApp.speaker.speak(item.answer, lang) { result ->
            if (result == SpeakResult.VOICE_MISSING) _message.value = "The ${lang.voiceName} voice isn't installed on this phone."
        }
    }

    private suspend fun buildLockerData(member: FamilyMember): LockerData {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        val docs = repo.documentsNow(member.id).associateBy { it.id }
        val meds = repo.medicinesNow(member.id).map { m ->
            val stamp = docs[m.documentId]?.captureDate ?: m.startDate
            MedRecord(
                name = m.name, strength = m.strength, times = m.timesCsv.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it) },
                sos = m.frequencyCode == FrequencyCode.SOS.name, meal = MealTiming.valueOf(m.mealTiming), durationDays = m.durationDays,
                startDate = LocalDate.parse(m.startDate), source = Source(SourceKind.PRESCRIPTION, "Prescription · ${LocalDate.parse(stamp).format(fmt)}", m.documentId),
                supply = m.refill(LocalDateTime.now())?.let { e -> SupplyInfo(RefillText.phrase(e), if (e.status == RefillStatus.COURSE_ENDS) null else RefillText.left(e), e.countedAt.toLocalDate(), e.needsReminder) },
            )
        }
        val labs = repo.labsNow(member.id).map { l ->
            val date = LocalDate.parse(l.date)
            LabRecord(
                testName = l.testName, key = TestNames.key(l.testName), value = l.value, unit = l.unit, low = l.printedRangeLow, high = l.printedRangeHigh,
                standardReference = l.rangeSource == "standard", date = date, source = Source(SourceKind.LAB_REPORT, "Lab report · ${date.format(fmt)}", l.documentId),
            )
        }
        val plan = if (member.isChildWithDob()) {
            val dob = LocalDate.parse(member.dateOfBirth)
            ImmunizationEngine.plan(dob, repo.immunizationsNow(member.id).associate { it.scheduleId to LocalDate.parse(it.administeredDate) }, today)
        } else null
        return LockerData(member.name, today, meds, labs, plan)
    }

    fun openReport(documentId: Long) { reportDoc.value = documentId; _screen.value = Screen.REPORT }

    fun openTrend(memberId: Long, key: String) {
        trendTarget.value = memberId to key
        _aiSummary.value = null
        _screen.value = Screen.TREND
        writeAiSummary()
    }

    /** Back navigation for every screen; the trend screen returns to the report it was opened from. */
    fun back() {
        when (_screen.value) {
            Screen.CONFIRM -> discard()
            Screen.ELDER -> { kApp.speaker.stop(); _screen.value = Screen.HOME }
            Screen.TREND -> _screen.value = if (reportDoc.value != null) Screen.REPORT else Screen.HOME
            Screen.CHILD -> _screen.value = Screen.HOME
            Screen.VISIT -> _screen.value = visitReturn
            Screen.ASK -> { stopListening(); kApp.speaker.stop(); _screen.value = askReturn }
            else -> _screen.value = Screen.HOME
        }
    }

    /**
     * Shows the rule-based sentence immediately. If the on-device model is loaded it rewrites it from the same computed
     * facts, and its text is used only if it states the latest value; otherwise the template stays.
     */
    private fun writeAiSummary() {
        viewModelScope.launch {
            val t = trend.first { it != null } ?: return@launch
            if (kApp.llm.activeBackend == null || t.points.size < 2) return@launch
            _aiBusy.value = true
            try {
                val (system, user) = Prompts.trendSummary(TrendSummary.facts(t.person, t.testName, t.points, t.unit, t.low, t.high))
                val text = kApp.llm.generate(system, user).text.trim()
                if (TrendSummary.isFaithful(text, t.points)) _aiSummary.value = text
            } catch (_: Throwable) {
                // Keep the rule-based sentence.
            } finally {
                _aiBusy.value = false
            }
        }
    }

    private fun LabValueEntity.toRow(): LabRow {
        val status = if (rangeSource == "none") RangeStatus.NO_RANGE else RangeCheck.status(value, printedRangeLow, printedRangeHigh)
        return LabRow(
            testName = testName, key = TestNames.key(testName),
            valueText = listOfNotNull(formatNumber(value), unit).joinToString(" "),
            status = status, rangeLabel = describeRange(printedRangeLow, printedRangeHigh, unit), usedFallback = rangeSource == "standard",
        )
    }

    private fun buildTrend(person: String, labs: List<LabValueEntity>): TrendUi {
        val latest = labs.last()
        val points = labs.map { TrendPoint(LocalDate.parse(it.date), it.value) }
        return TrendUi(
            person = person, testName = latest.testName, unit = latest.unit, points = points,
            low = latest.printedRangeLow, high = latest.printedRangeHigh, usedFallback = latest.rangeSource == "standard",
            summary = TrendSummary.template(person, latest.testName.lowercase(), points, latest.unit, latest.printedRangeLow, latest.printedRangeHigh),
        )
    }

    fun select(id: Long) { selectedId.value = id }

    /** Share-in entry point. With one family member the photo goes straight in; with several, we ask whose it is. */
    fun onSharedImage(uri: Uri) {
        viewModelScope.launch {
            val members = repo.members().first()
            when {
                members.isEmpty() -> _message.value = "Add a family member first, then share the photo again."
                members.size == 1 -> { selectedId.value = members[0].id; processImage(uri) }
                else -> _pendingShare.value = uri
            }
        }
    }

    fun chooseShareTarget(memberId: Long) {
        val uri = _pendingShare.value ?: return
        _pendingShare.value = null
        selectedId.value = memberId
        processImage(uri)
    }

    fun cancelShare() { _pendingShare.value = null }

    fun setLanguage(lang: AppLanguage) {
        val member = home.value.selected ?: return
        kApp.speaker.stop()
        viewModelScope.launch { repo.setLanguage(member.id, lang.code) }
    }

    /** Reads out the doses still to be taken today. Tapping again while speaking stops it. */
    fun speakToday() {
        val ui = home.value
        val member = ui.selected ?: return
        val speaker = kApp.speaker
        if (speaking.value) { speaker.stop(); return }
        val lang = AppLanguage.fromCode(member.preferredLanguage)
        val script = com.kutumbam.app.speech.ElderScript.build(lang, member.name, ui.doses.filterNot { it.taken }.map { ScriptDose(it.time, it.name, it.meal, it.units) })
        speaker.speak(script, lang) { result ->
            when (result) {
                SpeakResult.STARTED -> Unit
                SpeakResult.VOICE_MISSING -> _message.value = "The ${lang.voiceName} voice isn't installed on this phone. Install it in Settings > System > Languages > Text-to-speech, or pick another language."
                SpeakResult.ENGINE_UNAVAILABLE -> _message.value = "Text-to-speech isn't available on this phone."
            }
        }
    }

    fun stopSpeaking() = kApp.speaker.stop()
    fun show(screen: Screen) { _screen.value = screen }
    fun clearMessage() { _message.value = null }
    fun say(text: String) { _message.value = text }

    fun addMember(name: String, relation: String, dob: String?, selfOperates: Boolean) = viewModelScope.launch {
        val id = repo.addMember(FamilyMember(name = name.trim(), relation = relation, dateOfBirth = dob, selfOperatesPhone = selfOperates))
        selectedId.value = id
    }

    fun toggleDose(row: DoseRow) = viewModelScope.launch {
        repo.setDose(row.medicineId, LocalDate.now(), row.time.toString(), !row.taken)
    }

    /** Photo -> OCR -> rule-based parser -> draft for the confirm screen. Nothing is stored yet except the image copy. */
    fun processImage(uri: Uri) {
        viewModelScope.launch {
            // Read members directly: on a cold start via share-in, the Home state may not have loaded yet.
            val members = repo.members().first()
            val member = members.firstOrNull { it.id == selectedId.value } ?: members.firstOrNull()
            if (member == null) { _message.value = "Add a family member first, then share the photo again."; return@launch }
            _busy.value = "Reading document…"
            try {
                val file = withContext(Dispatchers.IO) { copyIntoStorage(uri) }
                val ocr = kApp.ocr.recognize(Uri.fromFile(file))
                val doc = DocumentParser.parse(ocr.text)
                if (doc.medicines.isEmpty() && doc.labValues.isEmpty() && doc.vaccinations.isEmpty()) {
                    _message.value = "Couldn't find any medicines, lab values or vaccines. Try a clearer, flatter photo."
                } else {
                    _draft.value = Draft(
                        memberId = member.id, memberName = member.name, imagePath = file.absolutePath, rawText = ocr.text,
                        type = doc.type, date = doc.date,
                        meds = doc.medicines.mapIndexed { i, m ->
                            EditableMed(
                                key = i, name = m.name, strength = m.strength.orEmpty(), form = m.form,
                                frequency = m.frequency?.code ?: FrequencyCode.OD,
                                times = m.frequency?.times ?: FrequencyParser.defaultTimes(FrequencyCode.OD),
                                meal = m.meal, durationDays = m.durationDays?.toString().orEmpty(),
                                quantity = m.quantity?.toString().orEmpty(),
                                units = m.frequency?.units?.takeIf { u -> u.size == (m.frequency?.times?.size ?: 0) }.orEmpty(),
                            )
                        },
                        existing = repo.medicinesNow(member.id).filter { courseActive(it, LocalDate.now()) }.map { MedRef(it.name, it.strength) },
                        labs = doc.labValues,
                        vaccines = doc.vaccinations.mapIndexed { i, v ->
                            EditableVaccine(i, v.scheduleId, v.label, UipSchedule.byId(v.scheduleId)?.milestone?.ageLabel.orEmpty(), v.date)
                        },
                    )
                    _screen.value = Screen.CONFIRM
                }
            } catch (t: Throwable) {
                _message.value = "Couldn't read that image: ${t.message}"
            } finally {
                _busy.value = null
            }
        }
    }

    fun updateMed(key: Int, change: (EditableMed) -> EditableMed) = _draft.update { d ->
        d?.copy(meds = d.meds.map { if (it.key == key) change(it) else it })
    }

    fun setFrequency(key: Int, code: FrequencyCode) =
        updateMed(key) { val t = FrequencyParser.defaultTimes(code); it.copy(frequency = code, times = t, units = List(t.size) { 1.0 }) }

    /** Changing a time keeps its tablet count attached, then re-sorts the day. */
    fun setTime(key: Int, index: Int, time: LocalTime) =
        updateMed(key) { m ->
            val pairs = m.times.zip(m.unitsFull()).toMutableList().also { it[index] = time to it[index].second }.sortedBy { it.first }
            m.copy(times = pairs.map { it.first }, units = pairs.map { it.second })
        }

    fun setUnits(key: Int, index: Int, units: Double) =
        updateMed(key) { m -> m.copy(units = m.unitsFull().toMutableList().also { it[index] = units }) }

    /** The family counted the tablets they have now (after a refill, or the first time). */
    fun setSupply(medicineId: Long, count: Int) {
        viewModelScope.launch { repo.setStock(medicineId, count); _message.value = "Saved. The refill estimate now starts from $count tablets." }
    }

    /** Re-arm every reminder from stored data (app start, after edits). */
    fun rearmReminders() { viewModelScope.launch(Dispatchers.Default) { ReminderScheduler.scheduleAll(getApplication()) } }

    fun removeMed(key: Int) = _draft.update { d -> d?.copy(meds = d.meds.filterNot { it.key == key }) }
    fun removeLab(index: Int) = _draft.update { d -> d?.copy(labs = d.labs.filterIndexed { i, _ -> i != index }) }

    fun discard() { _draft.value = null; _screen.value = Screen.HOME }

    fun save() {
        val d = _draft.value ?: return
        viewModelScope.launch {
            val docId = repo.saveConfirmed(
                memberId = d.memberId, type = d.type.name, imagePath = d.imagePath, rawText = d.rawText, documentDate = d.date,
                medicines = d.meds.filter { it.name.isNotBlank() }.map {
                    ConfirmedMedicine(
                        name = it.name.trim(), strength = it.strength.trim().ifEmpty { null }, form = it.form,
                        frequencyCode = it.frequency.name, times = it.times, meal = it.meal.name, durationDays = it.durationDays.toIntOrNull(),
                        quantity = it.quantity.toIntOrNull()?.takeIf { q -> q > 0 },
                        units = it.unitsFull(),
                    )
                },
                labs = d.labs.map { ConfirmedLab(it.testName, it.value, it.unit, it.rangeLow, it.rangeHigh, it.rangeText) },
                vaccinations = d.vaccines.mapNotNull { v -> v.date?.let { ConfirmedVaccine(v.scheduleId, it) } },
            )
            _draft.value = null
            _message.value = "Saved to ${d.memberName}'s locker."
            when {
                d.vaccines.isNotEmpty() && d.meds.isEmpty() && d.labs.isEmpty() -> _screen.value = Screen.CHILD
                d.labs.isNotEmpty() && d.meds.isEmpty() -> openReport(docId)
                else -> _screen.value = Screen.HOME
            }
            ReminderScheduler.scheduleAll(getApplication())
        }
    }

    private fun copyIntoStorage(uri: Uri): File {
        val dir = File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }
        val file = File(dir, "doc_${System.currentTimeMillis()}.jpg")
        getApplication<Application>().contentResolver.openInputStream(uri)!!.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
        return file
    }

    private fun courseActive(m: MedicineEntity, today: LocalDate): Boolean {
        if (m.frequencyCode == FrequencyCode.SOS.name) return false
        val start = LocalDate.parse(m.startDate)
        return !today.isBefore(start) && (m.durationDays == null || today.isBefore(start.plusDays(m.durationDays.toLong())))
    }

    private fun trim(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

fun mealText(meal: String): String = when (MealTiming.valueOf(meal)) {
    MealTiming.BEFORE_FOOD -> "Before food"
    MealTiming.AFTER_FOOD -> "After food"
    MealTiming.WITH_FOOD -> "With food"
    MealTiming.EMPTY_STOMACH -> "On an empty stomach"
    MealTiming.UNSPECIFIED -> ""
}

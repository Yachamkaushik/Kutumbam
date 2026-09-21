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
import com.kutumbam.app.parse.DocumentParser
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

enum class Screen { HOME, CONFIRM, ELDER, REPORT, TREND, DEV }

data class DoseRow(
    val medicineId: Long,
    val time: LocalTime,
    val name: String,
    val instruction: String,
    val taken: Boolean,
    val meal: MealTiming = MealTiming.UNSPECIFIED,
) {
    val timeText: String get() = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}

data class AlertInfo(val text: String, val documentId: Long)

data class HomeUi(
    val members: List<FamilyMember> = emptyList(),
    val selected: FamilyMember? = null,
    val doses: List<DoseRow> = emptyList(),
    val alert: AlertInfo? = null,
    val reports: List<ReportSummary> = emptyList(),
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
    val editing: Boolean = false,
)

data class Draft(
    val memberId: Long,
    val memberName: String,
    val imagePath: String?,
    val rawText: String,
    val type: DocumentType,
    val date: LocalDate?,
    val meds: List<EditableMed>,
    val labs: List<ParsedLabValue>,
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

    val home: StateFlow<HomeUi> = combine(repo.members(), selectedId) { members, sel ->
        members to (members.firstOrNull { it.id == sel } ?: members.firstOrNull())
    }.flatMapLatest { (members, member) ->
        if (member == null) flowOf(HomeUi(members))
        else {
            val today = LocalDate.now()
            combine(repo.medicines(member.id), repo.doseLogs(today), repo.latestFlagged(member.id), repo.reportSummaries(member.id)) { meds, logs, flagged, reports ->
                val taken = logs.map { Triple(it.medicineId, it.time, it.status) }.filter { it.third == "taken" }.map { it.first to it.second }.toSet()
                val doses = meds.filter { courseActive(it, today) }.flatMap { m ->
                    m.timesCsv.split(",").filter { it.isNotBlank() }.map { t ->
                        DoseRow(m.id, LocalTime.parse(t), listOfNotNull(m.name, m.strength).joinToString(" "), mealText(m.mealTiming), (m.id to t) in taken, MealTiming.valueOf(m.mealTiming))
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
                HomeUi(members, member, doses, alert, reports)
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
        val script = com.kutumbam.app.speech.ElderScript.build(lang, member.name, ui.doses.filterNot { it.taken }.map { ScriptDose(it.time, it.name, it.meal) })
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
                if (doc.medicines.isEmpty() && doc.labValues.isEmpty()) {
                    _message.value = "Couldn't find any medicines or lab values. Try a clearer, flatter photo."
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
                            )
                        },
                        labs = doc.labValues,
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
        updateMed(key) { it.copy(frequency = code, times = FrequencyParser.defaultTimes(code)) }

    fun setTime(key: Int, index: Int, time: LocalTime) =
        updateMed(key) { m -> m.copy(times = m.times.toMutableList().also { it[index] = time }.sorted()) }

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
                    )
                },
                labs = d.labs.map { ConfirmedLab(it.testName, it.value, it.unit, it.rangeLow, it.rangeHigh, it.rangeText) },
            )
            _draft.value = null
            _message.value = "Saved to ${d.memberName}'s locker."
            if (d.labs.isNotEmpty() && d.meds.isEmpty()) openReport(docId) else _screen.value = Screen.HOME
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

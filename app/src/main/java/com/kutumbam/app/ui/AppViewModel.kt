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
import com.kutumbam.app.parse.DocumentParser
import com.kutumbam.app.parse.DocumentType
import com.kutumbam.app.parse.FrequencyCode
import com.kutumbam.app.parse.FrequencyParser
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.parse.ParsedLabValue
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

enum class Screen { HOME, CONFIRM, DEV }

data class DoseRow(
    val medicineId: Long,
    val time: LocalTime,
    val name: String,
    val instruction: String,
    val taken: Boolean,
) {
    val timeText: String get() = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}

data class HomeUi(
    val members: List<FamilyMember> = emptyList(),
    val selected: FamilyMember? = null,
    val doses: List<DoseRow> = emptyList(),
    val alert: String? = null,
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
            combine(repo.medicines(member.id), repo.doseLogs(today), repo.latestFlagged(member.id)) { meds, logs, flagged ->
                val taken = logs.map { Triple(it.medicineId, it.time, it.status) }.filter { it.third == "taken" }.map { it.first to it.second }.toSet()
                val doses = meds.filter { courseActive(it, today) }.flatMap { m ->
                    m.timesCsv.split(",").filter { it.isNotBlank() }.map { t ->
                        DoseRow(m.id, LocalTime.parse(t), listOfNotNull(m.name, m.strength).joinToString(" "), mealText(m.mealTiming), (m.id to t) in taken)
                    }
                }.sortedBy { it.time }
                val alert = flagged?.let {
                    "${member.name}'s last ${it.testName} (${trim(it.value)}${it.unit?.let { u -> " $u" } ?: ""}) was outside the range " +
                        "printed on the report. Please talk to a doctor about it."
                }
                HomeUi(members, member, doses, alert)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun select(id: Long) { selectedId.value = id }
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

    fun removeMed(key: Int) = _draft.update { d -> d?.copy(meds = d.meds.filterNot { it.key == key }) }
    fun removeLab(index: Int) = _draft.update { d -> d?.copy(labs = d.labs.filterIndexed { i, _ -> i != index }) }

    fun discard() { _draft.value = null; _screen.value = Screen.HOME }

    fun save() {
        val d = _draft.value ?: return
        viewModelScope.launch {
            repo.saveConfirmed(
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
            _screen.value = Screen.HOME
            _message.value = "Saved to ${d.memberName}'s locker."
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

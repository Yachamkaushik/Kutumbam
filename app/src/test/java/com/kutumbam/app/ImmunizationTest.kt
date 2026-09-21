package com.kutumbam.app

import com.kutumbam.app.parse.DocumentDates
import com.kutumbam.app.parse.DocumentType
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.Milestone
import com.kutumbam.app.parse.UipSchedule
import com.kutumbam.app.parse.VaccinationCardParser
import com.kutumbam.app.parse.VaccineDigest
import com.kutumbam.app.parse.VaccineStatus
import com.kutumbam.app.parse.DocumentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImmunizationTest {
    private val dob = LocalDate.of(2026, 7, 20)

    private fun status(plan: List<com.kutumbam.app.parse.MilestoneState>, m: Milestone) = plan.first { it.milestone == m }.status

    @Test fun everyVaccineHasAUniqueIdAndAMilestone() {
        assertEquals(UipSchedule.vaccines.size, UipSchedule.vaccines.map { it.id }.toSet().size)
        Milestone.entries.forEach { m -> assertTrue("no vaccines at $m", UipSchedule.vaccines.any { it.milestone == m }) }
    }

    @Test fun dueDatesAreFixedOffsetsFromBirth() {
        assertEquals(dob, Milestone.BIRTH.dueDate(dob))
        assertEquals(LocalDate.of(2026, 8, 31), Milestone.WEEK_6.dueDate(dob))
        assertEquals(LocalDate.of(2026, 9, 28), Milestone.WEEK_10.dueDate(dob))
        assertEquals(LocalDate.of(2027, 4, 20), Milestone.MONTH_9.dueDate(dob))
    }

    @Test fun statusesFollowTheDatesAndWhatIsDone() {
        // Nine weeks old on 21 Sep 2026: birth and 6-week doses are past their windows, 10 weeks is next week.
        val today = LocalDate.of(2026, 9, 21)
        val none = ImmunizationEngine.plan(dob, emptyMap(), today)
        assertEquals(VaccineStatus.OVERDUE, status(none, Milestone.BIRTH))
        assertEquals(VaccineStatus.DUE, status(none, Milestone.WEEK_6))
        assertEquals(VaccineStatus.UPCOMING, status(none, Milestone.WEEK_10))

        val given = mapOf("BCG" to dob.plusDays(1), "OPV-0" to dob.plusDays(1), "HEPB-0" to dob.plusDays(1))
        val some = ImmunizationEngine.plan(dob, given, today)
        assertEquals(VaccineStatus.DONE, status(some, Milestone.BIRTH))
        assertEquals(VaccineStatus.DUE, status(some, Milestone.WEEK_6))
    }

    @Test fun aMilestoneIsOverdueIfAnyDoseIs() {
        val today = LocalDate.of(2026, 9, 21)
        val plan = ImmunizationEngine.plan(dob, mapOf("BCG" to dob, "OPV-0" to dob), today)
        assertEquals(VaccineStatus.OVERDUE, status(plan, Milestone.BIRTH)) // Hep B birth dose still missing
    }

    @Test fun relativeTextReadsNaturally() {
        val today = LocalDate.of(2026, 9, 21)
        val plan = ImmunizationEngine.plan(dob, emptyMap(), today)
        assertEquals("due in 7 days", ImmunizationEngine.relativeText(plan.first { it.milestone == Milestone.WEEK_10 }, today))
        assertEquals("due now", ImmunizationEngine.relativeText(plan.first { it.milestone == Milestone.WEEK_6 }, today))
        assertTrue(ImmunizationEngine.relativeText(plan.first { it.milestone == Milestone.BIRTH }, today).startsWith("overdue by"))
    }

    @Test fun farFutureDosesAreDescribedInYears() {
        val today = LocalDate.of(2026, 9, 21)
        val plan = ImmunizationEngine.plan(dob, emptyMap(), today)
        assertEquals("due in 9 years", ImmunizationEngine.relativeText(plan.first { it.milestone == Milestone.YEAR_10 }, today))
        assertEquals("due in 14 months", ImmunizationEngine.relativeText(plan.first { it.milestone == Milestone.MONTH_16 }, today))
    }

    @Test fun pendingTitleListsOnlyOutstandingDoses() {
        val today = LocalDate.of(2026, 9, 21)
        val given = mapOf("OPV-1" to LocalDate.of(2026, 8, 31), "PENTA-1" to LocalDate.of(2026, 8, 31))
        val ms = ImmunizationEngine.plan(dob, given, today).first { it.milestone == Milestone.WEEK_6 }
        assertEquals("Rotavirus-1, fIPV-1, PCV-1", ms.pendingTitle)
        assertTrue(ms.title.contains("OPV-1"))
    }

    @Test fun ageText() {
        assertEquals("5 days old", ImmunizationEngine.ageText(dob, dob.plusDays(5)))
        assertEquals("9 weeks old", ImmunizationEngine.ageText(dob, LocalDate.of(2026, 9, 21)))
        assertEquals("5 months old", ImmunizationEngine.ageText(dob, LocalDate.of(2026, 12, 25)))
        assertEquals("2 years old", ImmunizationEngine.ageText(LocalDate.of(2024, 2, 14), LocalDate.of(2026, 9, 21)))
    }

    @Test fun digestSaysWhatIsSoonAndWhatIsOverdue() {
        val today = LocalDate.of(2026, 9, 26) // two days before the 10-week milestone
        val plan = ImmunizationEngine.plan(dob, emptyMap(), today)
        val text = VaccineDigest.build(plan, today)!!
        assertTrue(text.contains("Due soon:") && text.contains("OPV-2") && text.contains("Pentavalent-2"))
        assertTrue(text.contains("Overdue:") && text.contains("BCG"))
    }

    @Test fun digestStaysQuietWhenNothingIsNear() {
        val today = dob.plusDays(3)
        val plan = ImmunizationEngine.plan(dob, mapOf("BCG" to dob, "OPV-0" to dob, "HEPB-0" to dob), today)
        assertNull(VaccineDigest.build(plan, today))
    }

    private val card = """
        CHILD IMMUNIZATION CARD
        Name: Aarav     DOB: 20/07/2026
        Vaccine   Date given
        BCG   21/07/2026
        OPV-0   21/07/2026
        Hepatitis B birth dose   21/07/2026
        OPV-1   31/08/2026
        Pentavalent 1   31/08/2026
        Rotavirus 1   31 Aug 2026
        PCV 1
    """.trimIndent()

    @Test fun cardRowsBecomeDosesWithDates() {
        val found = VaccinationCardParser.parse(card).associateBy { it.scheduleId }
        assertEquals(LocalDate.of(2026, 7, 21), found.getValue("BCG").date)
        assertEquals(LocalDate.of(2026, 7, 21), found.getValue("OPV-0").date)
        assertEquals(LocalDate.of(2026, 7, 21), found.getValue("HEPB-0").date)
        assertEquals(LocalDate.of(2026, 8, 31), found.getValue("OPV-1").date)
        assertEquals(LocalDate.of(2026, 8, 31), found.getValue("PENTA-1").date)
        assertEquals(LocalDate.of(2026, 8, 31), found.getValue("ROTA-1").date)
        assertNull(found.getValue("PCV-1").date) // listed without a date: the confirm screen asks for one
    }

    @Test fun aliasesMapToTheSameDose() {
        fun ids(s: String) = VaccinationCardParser.parse(s).map { it.scheduleId }
        assertEquals(listOf("MR-1"), ids("MMR-1  20/04/2027"))
        assertEquals(listOf("MR-1"), ids("Measles 1  20/04/2027"))
        assertEquals(listOf("FIPV-2"), ids("IPV 2  01/11/2026"))
        assertEquals(listOf("PENTA-3"), ids("DPT+HepB+Hib 3  02/11/2026"))
        assertEquals(listOf("DPT-B1"), ids("DPT booster  20/11/2027"))
        assertEquals(listOf("DPT-B2"), ids("DPT booster 2  20/07/2031"))
        assertEquals(listOf("OPV-B"), ids("OPV booster  20/11/2027"))
    }

    @Test fun onlyRealDosesAreRead() {
        assertTrue(VaccinationCardParser.parse("Name: Aarav\nDOB: 20/07/2026\nBatch no 4521\nGrocery").isEmpty())
    }

    @Test fun aCardIsRecognisedAndParsedEndToEnd() {
        val doc = DocumentParser.parse(card)
        assertEquals(DocumentType.VACCINATION_CARD, doc.type)
        assertEquals(7, doc.vaccinations.size)
        assertTrue(doc.medicines.isEmpty() && doc.labValues.isEmpty())
        assertNotNull(DocumentDates.findInLine("BCG 21/07/2026"))
    }
}

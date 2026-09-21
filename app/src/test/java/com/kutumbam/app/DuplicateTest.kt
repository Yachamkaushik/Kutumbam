package com.kutumbam.app

import com.kutumbam.app.parse.DuplicateCheck
import com.kutumbam.app.parse.MedRef
import com.kutumbam.app.parse.MedicineSalts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateTest {
    @Test fun brandsResolveToTheirIngredient() {
        assertEquals(setOf("metformin"), MedicineSalts.ingredients("Glycomet 500"))
        assertEquals(setOf("paracetamol"), MedicineSalts.ingredients("Dolo 650"))
        assertEquals(setOf("pantoprazole"), MedicineSalts.ingredients("Pan 40"))
        assertEquals(setOf("pantoprazole", "domperidone"), MedicineSalts.ingredients("Pan D"))
        assertEquals(setOf("metformin"), MedicineSalts.ingredients("Metformin Hydrochloride SR"))
    }

    @Test fun brandAndGenericAreTheSameMedicine() {
        val w = DuplicateCheck.find(listOf(MedRef("Metformin", "500 mg"), MedRef("Glycomet", "500 mg", isNew = true)))
        assertEquals(1, w.size)
        assertTrue(w[0].sameStrength)
        assertTrue(w[0].text.contains("both contain metformin"))
    }

    @Test fun combinationsOverlapTheirParts() {
        val w = DuplicateCheck.find(listOf(MedRef("Telma AM", "40/5 mg"), MedRef("Amlodipine", "5 mg", isNew = true)))
        assertEquals(setOf("amlodipine"), w.single().shared)
        assertFalse(w.single().sameStrength)
        assertTrue(w.single().text.contains("at different strengths"))
        assertEquals(1, DuplicateCheck.find(listOf(MedRef("Dolo 650", null), MedRef("Combiflam", null, isNew = true))).size)
    }

    @Test fun unrelatedMedicinesAreNotFlagged() {
        assertTrue(DuplicateCheck.find(listOf(MedRef("Metformin", "500 mg"), MedRef("Amlodipine", "5 mg"), MedRef("Pantoprazole", "40 mg"))).isEmpty())
        // "Pan" must not match names that merely start with the same letters.
        assertTrue(DuplicateCheck.find(listOf(MedRef("Pan 40", null), MedRef("Pancreatin", null))).isEmpty())
    }

    @Test fun sameUnlistedNameTwiceIsStillCaught() {
        val w = DuplicateCheck.find(listOf(MedRef("Xyzol 10", null), MedRef("Xyzol", null, isNew = true)))
        assertEquals(1, w.size)
        assertTrue(w[0].text.contains("are the same medicine"))
    }

    @Test fun onlyPairsInvolvingANewMedicineAreReported() {
        val items = listOf(MedRef("Metformin", null), MedRef("Glycomet", null), MedRef("Amlodipine", null, isNew = true))
        assertTrue(DuplicateCheck.find(items).isEmpty())
        assertEquals(1, DuplicateCheck.find(items.dropLast(1)).size)
    }
}

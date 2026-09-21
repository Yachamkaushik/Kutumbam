package com.kutumbam.app.parse

/**
 * Finds two medicines that share an active ingredient, such as Dolo 650 and Paracetamol 500 or Telma AM and Amlodipine,
 * using a small bundled list of common Indian brands. It only points the overlap out; it never says which to stop.
 */
object MedicineSalts {
    private fun m(vararg pairs: Pair<String, String>) = pairs.associate { it.first to it.second.split("+").toSet() }

    /** Brand or generic name (lower case, no strength) to its ingredients. Names not listed are matched by their own name only. */
    private val KNOWN: Map<String, Set<String>> = m(
        // diabetes
        "metformin" to "metformin", "glycomet" to "metformin", "glucophage" to "metformin", "obimet" to "metformin", "gluformin" to "metformin",
        "glimepiride" to "glimepiride", "amaryl" to "glimepiride", "glimestar" to "glimepiride",
        "glycomet gp" to "metformin+glimepiride", "amaryl m" to "glimepiride+metformin", "glimestar m" to "glimepiride+metformin",
        "gliclazide" to "gliclazide", "diamicron" to "gliclazide", "glizid" to "gliclazide",
        "sitagliptin" to "sitagliptin", "januvia" to "sitagliptin", "istavel" to "sitagliptin", "zita" to "sitagliptin",
        "vildagliptin" to "vildagliptin", "galvus" to "vildagliptin", "jalra" to "vildagliptin",
        "empagliflozin" to "empagliflozin", "jardiance" to "empagliflozin",
        "dapagliflozin" to "dapagliflozin", "forxiga" to "dapagliflozin", "oxra" to "dapagliflozin",
        "pioglitazone" to "pioglitazone", "pioz" to "pioglitazone",
        // blood pressure and heart
        "amlodipine" to "amlodipine", "amlong" to "amlodipine", "norvasc" to "amlodipine", "amlopress" to "amlodipine", "stamlo" to "amlodipine", "amlogard" to "amlodipine",
        "telmisartan" to "telmisartan", "telma" to "telmisartan", "telmikind" to "telmisartan", "telsartan" to "telmisartan",
        "telma am" to "telmisartan+amlodipine", "telmikind am" to "telmisartan+amlodipine",
        "telma h" to "telmisartan+hydrochlorothiazide", "telmikind h" to "telmisartan+hydrochlorothiazide",
        "losartan" to "losartan", "losar" to "losartan", "repace" to "losartan", "losacar" to "losartan", "covance" to "losartan",
        "losar h" to "losartan+hydrochlorothiazide", "repace h" to "losartan+hydrochlorothiazide",
        "olmesartan" to "olmesartan", "olmezest" to "olmesartan", "olmat" to "olmesartan",
        "atenolol" to "atenolol", "aten" to "atenolol", "tenormin" to "atenolol",
        "metoprolol" to "metoprolol", "metolar" to "metoprolol", "betaloc" to "metoprolol",
        "bisoprolol" to "bisoprolol", "concor" to "bisoprolol", "carvedilol" to "carvedilol", "carca" to "carvedilol",
        "ramipril" to "ramipril", "cardace" to "ramipril", "ramace" to "ramipril", "enalapril" to "enalapril", "envas" to "enalapril",
        "hydrochlorothiazide" to "hydrochlorothiazide", "hctz" to "hydrochlorothiazide",
        "furosemide" to "furosemide", "lasix" to "furosemide", "torsemide" to "torsemide", "dytor" to "torsemide",
        "spironolactone" to "spironolactone", "aldactone" to "spironolactone",
        "atorvastatin" to "atorvastatin", "atorva" to "atorvastatin", "lipitor" to "atorvastatin", "storvas" to "atorvastatin", "atocor" to "atorvastatin", "tonact" to "atorvastatin",
        "rosuvastatin" to "rosuvastatin", "rosuvas" to "rosuvastatin", "crestor" to "rosuvastatin", "rozavel" to "rosuvastatin", "rosulip" to "rosuvastatin",
        "aspirin" to "aspirin", "ecosprin" to "aspirin", "disprin" to "aspirin", "loprin" to "aspirin", "ecosprin av" to "aspirin+atorvastatin",
        "clopidogrel" to "clopidogrel", "plavix" to "clopidogrel", "clopilet" to "clopidogrel", "deplatt" to "clopidogrel",
        "warfarin" to "warfarin", "warf" to "warfarin", "coumadin" to "warfarin",
        "isosorbide mononitrate" to "isosorbide mononitrate", "monotrate" to "isosorbide mononitrate", "ismo" to "isosorbide mononitrate",
        "digoxin" to "digoxin", "lanoxin" to "digoxin", "ticagrelor" to "ticagrelor", "brilinta" to "ticagrelor",
        // stomach
        "pantoprazole" to "pantoprazole", "pan" to "pantoprazole", "pantop" to "pantoprazole", "pantocid" to "pantoprazole", "pantodac" to "pantoprazole",
        "pan d" to "pantoprazole+domperidone", "pantop d" to "pantoprazole+domperidone",
        "omeprazole" to "omeprazole", "omez" to "omeprazole", "rabeprazole" to "rabeprazole", "razo" to "rabeprazole", "rabicip" to "rabeprazole",
        "razo d" to "rabeprazole+domperidone", "rabicip d" to "rabeprazole+domperidone",
        "esomeprazole" to "esomeprazole", "nexpro" to "esomeprazole", "esoz" to "esomeprazole",
        "ranitidine" to "ranitidine", "zinetac" to "ranitidine", "rantac" to "ranitidine", "famotidine" to "famotidine", "famocid" to "famotidine",
        "domperidone" to "domperidone", "domstal" to "domperidone", "ondansetron" to "ondansetron", "emeset" to "ondansetron", "ondem" to "ondansetron",
        "ursodeoxycholic acid" to "ursodeoxycholic acid", "udiliv" to "ursodeoxycholic acid",
        // pain and fever
        "paracetamol" to "paracetamol", "acetaminophen" to "paracetamol", "dolo" to "paracetamol", "crocin" to "paracetamol", "calpol" to "paracetamol", "pacimol" to "paracetamol",
        "ibuprofen" to "ibuprofen", "brufen" to "ibuprofen", "combiflam" to "ibuprofen+paracetamol",
        "diclofenac" to "diclofenac", "voveran" to "diclofenac", "voltaren" to "diclofenac",
        "aceclofenac" to "aceclofenac", "zerodol" to "aceclofenac", "zerodol p" to "aceclofenac+paracetamol", "zerodol sp" to "aceclofenac+paracetamol+serratiopeptidase",
        "naproxen" to "naproxen", "naprosyn" to "naproxen", "etoricoxib" to "etoricoxib", "nucoxia" to "etoricoxib", "arcoxia" to "etoricoxib",
        // thyroid
        "levothyroxine" to "levothyroxine", "thyronorm" to "levothyroxine", "eltroxin" to "levothyroxine", "thyrox" to "levothyroxine", "euthyrox" to "levothyroxine",
        // allergy and breathing
        "cetirizine" to "cetirizine", "cetzine" to "cetirizine", "okacet" to "cetirizine", "zyrtec" to "cetirizine", "alerid" to "cetirizine",
        "levocetirizine" to "levocetirizine", "levocet" to "levocetirizine", "xyzal" to "levocetirizine", "vozet" to "levocetirizine",
        "montelukast" to "montelukast", "montair" to "montelukast", "singulair" to "montelukast", "montek" to "montelukast",
        "montair lc" to "montelukast+levocetirizine", "montek lc" to "montelukast+levocetirizine",
        "fexofenadine" to "fexofenadine", "allegra" to "fexofenadine", "fexova" to "fexofenadine",
        "salbutamol" to "salbutamol", "asthalin" to "salbutamol", "ventolin" to "salbutamol", "levosalbutamol" to "levosalbutamol", "levolin" to "levosalbutamol",
        "budesonide" to "budesonide", "budecort" to "budesonide", "pulmicort" to "budesonide",
        // antibiotics
        "azithromycin" to "azithromycin", "azithral" to "azithromycin", "azee" to "azithromycin", "zithromax" to "azithromycin",
        "amoxicillin" to "amoxicillin", "mox" to "amoxicillin", "novamox" to "amoxicillin", "amoxil" to "amoxicillin",
        "augmentin" to "amoxicillin+clavulanic acid", "clavam" to "amoxicillin+clavulanic acid", "moxclav" to "amoxicillin+clavulanic acid",
        "cefixime" to "cefixime", "zifi" to "cefixime", "cefuroxime" to "cefuroxime", "zinnat" to "cefuroxime", "cefakind" to "cefuroxime",
        "ofloxacin" to "ofloxacin", "zenflox" to "ofloxacin", "oflox" to "ofloxacin", "ciprofloxacin" to "ciprofloxacin", "ciplox" to "ciprofloxacin", "cifran" to "ciprofloxacin",
        "levofloxacin" to "levofloxacin", "levoflox" to "levofloxacin", "doxycycline" to "doxycycline", "metronidazole" to "metronidazole", "flagyl" to "metronidazole", "metrogyl" to "metronidazole",
        // vitamins and minerals
        "calcium" to "calcium", "shelcal" to "calcium+cholecalciferol",
        "cholecalciferol" to "cholecalciferol", "vitamin d3" to "cholecalciferol", "calcirol" to "cholecalciferol", "d rise" to "cholecalciferol", "uprise d3" to "cholecalciferol",
        "methylcobalamin" to "methylcobalamin", "mecobalamin" to "methylcobalamin", "mecobalin" to "methylcobalamin",
        "neurobion" to "thiamine+pyridoxine+cyanocobalamin", "folic acid" to "folic acid", "folvite" to "folic acid",
        // nerves, mind, sleep
        "gabapentin" to "gabapentin", "gabapin" to "gabapentin", "neurontin" to "gabapentin", "pregabalin" to "pregabalin", "lyrica" to "pregabalin",
        "escitalopram" to "escitalopram", "nexito" to "escitalopram", "cipralex" to "escitalopram", "sertraline" to "sertraline", "serta" to "sertraline", "zoloft" to "sertraline",
        "clonazepam" to "clonazepam", "rivotril" to "clonazepam", "clonotril" to "clonazepam", "alprazolam" to "alprazolam", "alprax" to "alprazolam",
        "levetiracetam" to "levetiracetam", "levipil" to "levetiracetam", "keppra" to "levetiracetam", "carbamazepine" to "carbamazepine", "tegretol" to "carbamazepine",
        // steroids, gout, other
        "prednisolone" to "prednisolone", "wysolone" to "prednisolone", "omnacortil" to "prednisolone", "deflazacort" to "deflazacort", "defcort" to "deflazacort",
        "allopurinol" to "allopurinol", "zyloric" to "allopurinol", "febuxostat" to "febuxostat", "febutaz" to "febuxostat",
        "tamsulosin" to "tamsulosin", "urimax" to "tamsulosin", "finasteride" to "finasteride", "finast" to "finasteride",
        "hydroxychloroquine" to "hydroxychloroquine", "hcqs" to "hydroxychloroquine", "albendazole" to "albendazole", "zentel" to "albendazole",
    )

    /** Words printed after a brand that are not part of its name. */
    private val NOISE = setOf("tab", "tablet", "tablets", "cap", "capsule", "capsules", "sr", "xr", "er", "mr", "cr", "xl", "mg", "mcg", "ml", "gm", "g", "iu", "forte")

    fun tokens(name: String): List<String> =
        name.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() && it !in NOISE }

    /** The ingredients behind [name]. Unlisted names stand for themselves, so the same unknown name twice is still caught. */
    fun ingredients(name: String): Set<String> {
        val t = tokens(name)
        if (t.isEmpty()) return emptySet()
        if (t.size >= 2) KNOWN[t[0] + " " + t[1]]?.let { return it }
        if (t.size >= 3) KNOWN[t[0] + " " + t[1] + " " + t[2]]?.let { return it }
        KNOWN[t[0]]?.let { return it }
        return setOf("~" + t[0])
    }

    fun isListed(name: String): Boolean = ingredients(name).none { it.startsWith("~") }
}

data class MedRef(val name: String, val strength: String?, val isNew: Boolean = false) {
    val label: String get() = listOfNotNull(name, strength).joinToString(" ")
}

data class DuplicateWarning(val first: MedRef, val second: MedRef, val shared: Set<String>, val sameStrength: Boolean) {
    /** Plain wording that points out the overlap and sends the question to the people who can answer it. */
    val text: String get() {
        val what = shared.filterNot { it.startsWith("~") }.sorted().joinToString(" and ")
        val both = if (what.isEmpty()) "are the same medicine" else "both contain $what"
        val strength = if (sameStrength) "" else " at different strengths"
        return "${first.label} and ${second.label} $both$strength. Ask the doctor or pharmacist whether you are meant to take both."
    }
}

object DuplicateCheck {
    /** Pairs that share an ingredient. When any item is new, only pairs that involve a new item are reported. */
    fun find(items: List<MedRef>): List<DuplicateWarning> {
        val onlyNew = items.any { it.isNew }
        val out = mutableListOf<DuplicateWarning>()
        for (i in items.indices) for (j in i + 1 until items.size) {
            val a = items[i]; val b = items[j]
            if (onlyNew && !a.isNew && !b.isNew) continue
            val shared = MedicineSalts.ingredients(a.name) intersect MedicineSalts.ingredients(b.name)
            if (shared.isEmpty()) continue
            val sa = a.strength?.replace(" ", "")?.lowercase()
            val sb = b.strength?.replace(" ", "")?.lowercase()
            out += DuplicateWarning(a, b, shared, sameStrength = sa != null && sa == sb)
        }
        return out
    }
}

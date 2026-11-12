package com.joshuadiablito.mythos.dictionary

/**
 * A verb's English meaning, conjugated to match a form: Wiktionary says
 * "mange" is the "third-person singular present indicative of manger", and
 * "manger" means "to eat", so "mange" is "he/she eats". Ported from EnglishVerb.swift.
 */
data class EnglishVerb(
    /** The verb itself: "eat", "wake", "be". */
    val base: String,
    /** Anything after it: "up" in "to wake up", "called" in "to be called". */
    val rest: String,
) {
    enum class Tense {
        PRESENT, PAST, IMPERFECT, FUTURE, FUTURE_CONTINUOUS, CONDITIONAL,
        PRESENT_SUBJUNCTIVE, PAST_SUBJUNCTIVE, PERFECT, PLUPERFECT, IMPERATIVE
    }

    /** "he/she eats", "we were eating", "they will eat", "eat!". Null if the
     *  combination makes no sense in English. */
    fun phrase(person: String, number: String, tense: Tense): String? {
        val subject = pronoun(person, number)
        val thirdSingular = person == "third-person" && number == "singular"
        val firstSingular = person == "first-person" && number == "singular"
        val plural = number == "plural" || person == "second-person"
        val verb: String = when (tense) {
            Tense.PRESENT -> if (base == "be") (if (firstSingular) "am" else if (thirdSingular) "is" else "are")
                else if (thirdSingular) thirdPerson(base) else base
            Tense.PAST -> if (base == "be") (if (plural) "were" else "was") else forms(base).first
            // "I was knowing" isn't English: stative verbs keep the simple past.
            Tense.IMPERFECT -> when {
                base == "be" -> if (plural) "were" else "was"
                base in stative -> forms(base).first
                else -> "${if (plural) "were" else "was"} ${ing(base)}"
            }
            Tense.FUTURE -> "will $base"
            Tense.FUTURE_CONTINUOUS -> if (base in stative || base == "be") "will $base" else "will be ${ing(base)}"
            Tense.CONDITIONAL -> "would $base"
            Tense.PRESENT_SUBJUNCTIVE -> return "(that) $subject ${join(base)}"
            Tense.PAST_SUBJUNCTIVE -> return "(if) $subject ${join(if (base == "be") "were" else forms(base).first)}"
            Tense.PERFECT -> "${if (thirdSingular) "has" else "have"} ${forms(base).second}"
            Tense.PLUPERFECT -> "had ${forms(base).second}"
            Tense.IMPERATIVE -> return when {
                person == "second-person" -> "${join(base)}!"
                person == "first-person" && number == "plural" -> "let's ${join(base)}!"
                person == "third-person" -> "${join(base)}! (polite)"
                else -> null
            }
        }
        return "$subject ${join(verb)}"
    }

    /** The English for an untabled form: "to eat", "eating", "eaten". */
    fun phrase(formLabel: String): String? {
        val l = formLabel.lowercase()
        if ("infinitive" in l) return "to ${join(base)}"
        if ("gerund" in l || ("participle" in l && "present" in l)) return join(ing(base))
        if ("participle" in l && ("past" in l || "perfect" in l)) return join(forms(base).second)
        return null
    }

    private fun join(verb: String) = if (rest.isEmpty()) verb else "$verb $rest"

    companion object {
        private val parens = Regex("\\([^)]*\\)")
        private val whitespace = Regex("\\s+")

        private fun words(s: String) = s.split(whitespace).filter { it.isNotEmpty() }

        /** From a gloss like "to eat", "(transitive) to look for, search" or
         *  "to wake (someone) up". Null unless it reads as an English infinitive. */
        fun fromGloss(gloss: String): EnglishVerb? {
            var g = gloss.replace(parens, "")
            g = g.split(',', ';', ':').first()
            val w = words(g)
            if (w.size < 2 || w[0].lowercase() != "to" || !w[1].all { it.isLetter() }) return null
            return EnglishVerb(w[1].lowercase(), w.drop(2).joinToString(" "))
        }

        /** Every verb a gloss offers: "to wake, wake up" → wake, wake up. */
        fun alternatives(gloss: String): List<EnglishVerb> {
            val first = fromGloss(gloss) ?: return emptyList()
            val cleaned = gloss.replace(parens, "")
            val verbs = mutableListOf(first)
            for (part in cleaned.split(',', ';').drop(1)) {
                var w = words(part)
                if (w.firstOrNull()?.lowercase() == "to") w = w.drop(1)
                val head = w.firstOrNull() ?: continue
                if (!head.all { it.isLetter() }) continue
                val verb = EnglishVerb(head.lowercase(), w.drop(1).joinToString(" "))
                if (verb !in verbs) verbs.add(verb)
            }
            return verbs
        }

        /** The English for a verb entry: its first meaning that is an infinitive.
         *  With a `hint` (the story's gloss, "wakes up"), the meaning that
         *  matches it, from any sense: "wake up" rather than "wake". */
        fun fromEntry(entry: Entry, hint: String? = null): EnglishVerb? {
            val verbs = mutableListOf<EnglishVerb>()
            for (verb in entry.senses.flatMap { alternatives(it.gloss) }) if (verb !in verbs) verbs.add(verb)
            if (entry.pos != "verb" || verbs.isEmpty()) return null
            val hintWords = (hint ?: "").lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.toSet()
            val fits = verbs.filter { verb ->
                verb.rest.isNotEmpty() &&
                    verb.rest.lowercase().split(" ").filter { it.isNotEmpty() }.all { it in hintWords } &&
                    hintWords.any { it.startsWith(verb.base.take(3)) }
            }
            return fits.maxByOrNull { it.rest.length } ?: verbs[0]
        }

        // Who and when

        /** The tense a form's (or table's) Wiktionary tags describe, if English has one like it. */
        fun tense(tags: Iterable<String>): Tense? {
            val t = tags.toSet()
            if ("negative" in t || "dependent" in t || "infinitive" in t || "participle" in t) return null
            if ("imperative" in t) return Tense.IMPERATIVE
            if ("conditional" in t) return Tense.CONDITIONAL
            if ("pluperfect" in t) return Tense.PLUPERFECT
            if ("perfect" in t && "future" !in t) return Tense.PERFECT
            // A form that is both indicative and subjunctive ("mange") reads best as indicative.
            if ("indicative" !in t && ("subjunctive" in t || "subjunctive-i" in t || "subjunctive-ii" in t)) {
                val past = "imperfect" in t || "past" in t || "preterite" in t ||
                    "imperfect-se" in t || "imperfect-ra" in t || "subjunctive-ii" in t
                return if (past) Tense.PAST_SUBJUNCTIVE else Tense.PRESENT_SUBJUNCTIVE
            }
            if ("future" in t) return if ("imperfective" in t) Tense.FUTURE_CONTINUOUS else Tense.FUTURE
            if ("imperfect" in t || ("past" in t && "imperfective" in t)) return Tense.IMPERFECT
            if ("preterite" in t || "past" in t || "historic" in t) return Tense.PAST
            if ("present" in t) return Tense.PRESENT
            return null
        }

        /** The subjects a form's tags name: "first/third-person singular" → I, he/she. */
        fun subjects(tags: Iterable<String>): List<Pair<String, String>> {
            val t = tags.toSet()
            val persons = listOf("first-person", "second-person", "third-person").filter { it in t }
            val numbers = listOf("singular", "plural").filter { it in t }
            return numbers.flatMap { n -> persons.map { it to n } }
        }

        fun pronoun(person: String, number: String): String = when (person to number) {
            "first-person" to "singular" -> "I"
            "second-person" to "singular" -> "you"
            "third-person" to "singular" -> "he/she"
            "first-person" to "plural" -> "we"
            "second-person" to "plural" -> "you all"
            else -> "they"
        }

        // English morphology

        /** Verbs that describe a state, which English doesn't put in the continuous. */
        val stative = setOf(
            "be", "have", "know", "want", "like", "love", "hate", "need", "believe", "seem", "own", "understand",
            "prefer", "remember", "mean", "belong", "contain", "cost", "deserve", "exist", "matter", "owe",
            "possess", "recognize", "recognise", "suppose", "doubt", "wish", "realize", "realise", "consist",
        )

        private const val VOWELS = "aeiou"

        fun thirdPerson(v: String): String {
            when (v) {
                "have" -> return "has"
                "do" -> return "does"
                "go" -> return "goes"
            }
            if (listOf("s", "sh", "ch", "x", "z", "o").any { v.endsWith(it) }) return v + "es"
            if (v.endsWith("y") && v.length >= 2 && v[v.length - 2] !in VOWELS) return v.dropLast(1) + "ies"
            return v + "s"
        }

        fun ing(v: String): String {
            if (v == "be" || v == "see" || v == "flee") return v + "ing"
            if (v.endsWith("ie")) return v.dropLast(2) + "ying"
            if (v.endsWith("e") && !v.endsWith("ee") && !v.endsWith("ye") && !v.endsWith("oe") && v.length > 2) return v.dropLast(1) + "ing"
            if (doublesFinalConsonant(v)) return v + v.last() + "ing"
            return v + "ing"
        }

        /** Simple past and past participle: irregular verbs from a table (with
         *  prefixes: "understand", "overcome"), the rest by rule. */
        fun forms(v: String): Pair<String, String> {
            irregulars[v]?.let { return it }
            for (prefix in listOf("under", "over", "with", "out", "mis", "re", "un", "up", "fore", "be")) {
                if (!v.startsWith(prefix)) continue
                irregulars[v.drop(prefix.length)]?.let { return (prefix + it.first) to (prefix + it.second) }
            }
            val regular = when {
                v.endsWith("e") -> v + "d"
                v.endsWith("y") && v.length >= 2 && v[v.length - 2] !in VOWELS -> v.dropLast(1) + "ied"
                doublesFinalConsonant(v) -> v + v.last() + "ed"
                else -> v + "ed"
            }
            return regular to regular
        }

        /** stop → stopping, plan → planned: one-syllable words ending consonant–vowel–consonant. */
        private fun doublesFinalConsonant(v: String): Boolean {
            if (v.length < 3) return false
            val last = v[v.length - 1]; val mid = v[v.length - 2]; val first = v[v.length - 3]
            val syllables = v.split(Regex("[^aeiou]+")).count { it.isNotEmpty() }
            return syllables == 1 && last !in VOWELS && last !in "wxy" && mid in VOWELS && first !in VOWELS
        }

        private val irregulars: Map<String, Pair<String, String>> = (
            "arise arose arisen|awake awoke awoken|be was been|bear bore borne|beat beat beaten|become became become|" +
            "begin began begun|bend bent bent|bet bet bet|bind bound bound|bite bit bitten|bleed bled bled|blow blew blown|" +
            "break broke broken|breed bred bred|bring brought brought|build built built|burst burst burst|buy bought bought|" +
            "catch caught caught|choose chose chosen|cling clung clung|come came come|cost cost cost|creep crept crept|" +
            "cut cut cut|deal dealt dealt|dig dug dug|do did done|draw drew drawn|drink drank drunk|drive drove driven|" +
            "eat ate eaten|fall fell fallen|feed fed fed|feel felt felt|fight fought fought|find found found|flee fled fled|" +
            "fly flew flown|forbid forbade forbidden|forget forgot forgotten|forgive forgave forgiven|freeze froze frozen|" +
            "get got got|give gave given|go went gone|grind ground ground|grow grew grown|hang hung hung|have had had|" +
            "hear heard heard|hide hid hidden|hit hit hit|hold held held|hurt hurt hurt|keep kept kept|kneel knelt knelt|" +
            "know knew known|lay laid laid|lead led led|leap leapt leapt|leave left left|lend lent lent|let let let|" +
            "lie lay lain|light lit lit|lose lost lost|make made made|mean meant meant|meet met met|pay paid paid|" +
            "put put put|quit quit quit|read read read|ride rode ridden|ring rang rung|rise rose risen|run ran run|" +
            "say said said|see saw seen|seek sought sought|sell sold sold|send sent sent|set set set|sew sewed sewn|" +
            "shake shook shaken|shine shone shone|shoot shot shot|show showed shown|shrink shrank shrunk|shut shut shut|" +
            "sing sang sung|sink sank sunk|sit sat sat|sleep slept slept|slide slid slid|speak spoke spoken|speed sped sped|" +
            "spend spent spent|spin spun spun|spit spat spat|split split split|spread spread spread|spring sprang sprung|" +
            "stand stood stood|steal stole stolen|stick stuck stuck|sting stung stung|stink stank stunk|strike struck struck|" +
            "swear swore sworn|sweep swept swept|swim swam swum|swing swung swung|take took taken|teach taught taught|" +
            "tear tore torn|tell told told|think thought thought|throw threw thrown|wake woke woken|wear wore worn|" +
            "weep wept wept|win won won|wind wound wound|write wrote written|dream dreamt dreamt|smell smelt smelt|" +
            "spell spelt spelt|burn burnt burnt|learn learnt learnt|lean leant leant|spoil spoilt spoilt"
        ).split("|").mapNotNull { e ->
            val w = e.split(" ")
            if (w.size == 3) w[0] to (w[1] to w[2]) else null
        }.toMap()
    }
}

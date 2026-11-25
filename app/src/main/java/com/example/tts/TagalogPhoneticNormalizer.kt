package com.example.tts

import com.example.model.PronunciationEntry

object TagalogPhoneticNormalizer {

    private val ACRONYM_MAP = mapOf(
        "OFW" to "oh ef double-u",
        "GCASH" to "ji-kash",
        "ATM" to "ey ti em",
        "SSS" to "es es es",
        "BIR" to "bi ay ar",
        "BSP" to "bi es pi",
        "DOH" to "di oh eyts",
        "BPO" to "bi pi oh",
        "ROI" to "ar oh ay",
        "CCTV" to "si si ti vi",
        "PHP" to "piso",
        "USD" to "dolyar",
        "AI" to "ey ay",
        "CEO" to "si i oh",
        "WFH" to "work from home"
    )

    private val LOANWORD_MAP = mapOf(
        "budget" to "badyet",
        "schedule" to "iskedyul",
        "business" to "bisnes",
        "investment" to "inbestment",
        "scam" to "iskam",
        "crypto" to "kripto",
        "salary" to "salari",
        "income" to "inkam",
        "million" to "milyon",
        "billion" to "bilyon",
        "percent" to "porsyento",
        "focus" to "pokus",
        "challenge" to "tsalendj",
        "youtube" to "yutyub",
        "tiktok" to "tik-tok",
        "reels" to "rils",
        "payday" to "peydey",
        "doomscrolling" to "dum-skroling",
        "mindset" to "mayndset",
        "hustle" to "hasol"
    )

    fun normalize(
        raw: String,
        customDictionary: List<PronunciationEntry> = emptyList()
    ): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""

        val customPesoUnit = customDictionary
            .firstOrNull { it.original.trim() == "₱" }
            ?.spoken?.trim()?.ifBlank { null }
            ?: "piso"
        val customPercentUnit = customDictionary
            .firstOrNull { it.original.trim() == "%" }
            ?.spoken?.trim()?.ifBlank { null }
            ?: "porsyento"

        // 1. Expand Philippine Peso currency: ₱1,500.50 or ₱500
        val pesoRegex = Regex("₱\\s*([0-9,]+)(?:\\.(\\d{1,2}))?")
        text = pesoRegex.replace(text) { match ->
            val wholeStr = match.groupValues[1].replace(",", "")
            val centsStr = match.groupValues[2]
            val whole = wholeStr.toLongOrNull() ?: 0L
            val wholeWords = numberToTagalog(whole)
            if (centsStr.isNotEmpty() && (centsStr.toIntOrNull() ?: 0) > 0) {
                val cents = centsStr.padEnd(2, '0').toLongOrNull() ?: 0L
                "$wholeWords $customPesoUnit at ${numberToTagalog(cents)} sentimo"
            } else {
                "$wholeWords $customPesoUnit"
            }
        }

        // 2. Expand percentages: 72% -> pitumpu't dalawang porsyento
        val percentRegex = Regex("(\\d+)\\s*%")
        text = percentRegex.replace(text) { match ->
            val num = match.groupValues[1].toLongOrNull() ?: 0L
            "${numberToTagalog(num)} $customPercentUnit"
        }

        // 3. Apply editable custom dictionary entries BEFORE integer/loanword passes so custom words win
        val sortedCustom = customDictionary
            .filter { it.original.isNotBlank() && it.spoken.isNotBlank() }
            .sortedByDescending { it.original.length }

        for (entry in sortedCustom) {
            val orig = entry.original.trim()
            val spoken = entry.spoken.trim()
            if (orig == "₱") {
                text = text.replace("₱", " $customPesoUnit ")
                continue
            }
            if (orig == "%") {
                text = text.replace("%", " $customPercentUnit ")
                continue
            }
            val isWordLike = orig.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }
            val pattern = if (isWordLike) {
                Regex("\\b${Regex.escape(orig)}\\b", RegexOption.IGNORE_CASE)
            } else {
                Regex(Regex.escape(orig), RegexOption.IGNORE_CASE)
            }
            text = pattern.replace(text) { " $spoken " }
        }

        // 4. Expand standalone integers (up to 999,999,999)
        val numberRegex = Regex("\\b\\d{1,3}(?:,\\d{3})*\\b|\\b\\d{1,9}\\b")
        text = numberRegex.replace(text) { match ->
            val clean = match.value.replace(",", "")
            val num = clean.toLongOrNull()
            if (num != null && num in 0..999_999_999L) {
                numberToTagalog(num)
            } else {
                match.value
            }
        }

        // 5. Expand built-in acronyms (unless overridden or explicitly used as spoken target in customDictionary)
        val protectedWords = sortedCustom
            .flatMap { listOf(it.original.trim().lowercase(), it.spoken.trim().lowercase()) }
            .toSet()
        for ((acronym, spoken) in ACRONYM_MAP) {
            if (acronym.lowercase() in protectedWords) continue
            val regex = Regex("\\b${Regex.escape(acronym)}\\b", RegexOption.IGNORE_CASE)
            text = regex.replace(text, spoken)
        }

        // 6. Smooth tricky English loanwords into natural Tagalog phonetics
        for ((loan, phonetic) in LOANWORD_MAP) {
            if (loan.lowercase() in protectedWords) continue
            val regex = Regex("\\b${Regex.escape(loan)}\\b", RegexOption.IGNORE_CASE)
            text = regex.replace(text, phonetic)
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    fun numberToTagalog(n: Long): String {
        if (n == 0L) return "sero"
        if (n < 0L) return "negatibo ${numberToTagalog(-n)}"

        val ones = arrayOf(
            "", "isa", "dalawa", "tatlo", "apat", "lima",
            "anim", "pito", "walo", "siyam"
        )
        val teens = arrayOf(
            "sampu", "labing-isa", "labindalawa", "labintatlo", "labing-apat",
            "labinlima", "labing-anim", "labimpito", "labingwalo", "labinsiyam"
        )
        val tens = arrayOf(
            "", "sampu", "dalawampu", "tatlumpu", "apatnapu",
            "limampu", "animnapu", "pitumpu", "walumpu", "siyamnapu"
        )

        fun under1000(num: Int): String {
            if (num == 0) return ""
            val sb = StringBuilder()
            val h = num / 100
            val rem = num % 100
            if (h > 0) {
                val hWord = when (h) {
                    1 -> "isang daan"
                    4 -> "apat na raan"
                    6 -> "anim na raan"
                    7 -> "pitong daan"
                    8 -> "walong daan"
                    9 -> "siyam na raan"
                    else -> "${ones[h]}ng daan"
                }
                sb.append(hWord)
                if (rem > 0) sb.append(" at ")
            }
            when {
                rem in 1..9 -> sb.append(ones[rem])
                rem in 10..19 -> sb.append(teens[rem - 10])
                rem >= 20 -> {
                    val t = rem / 10
                    val o = rem % 10
                    sb.append(tens[t])
                    if (o > 0) {
                        sb.append("'t ").append(ones[o])
                    }
                }
            }
            return sb.toString()
        }

        val millions = (n / 1_000_000L).toInt()
        val thousands = ((n % 1_000_000L) / 1_000L).toInt()
        val rest = (n % 1_000L).toInt()

        val parts = mutableListOf<String>()
        if (millions > 0) {
            val mPrefix = if (millions == 1) "isang" else under1000(millions)
            parts.add("$mPrefix milyon")
        }
        if (thousands > 0) {
            val kPrefix = if (thousands == 1) "isang" else under1000(thousands)
            parts.add("$kPrefix libo")
        }
        if (rest > 0) {
            parts.add(under1000(rest))
        }
        return parts.joinToString(" ")
    }
}

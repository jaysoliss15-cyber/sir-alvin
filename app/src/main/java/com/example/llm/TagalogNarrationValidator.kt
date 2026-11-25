package com.example.llm

object TagalogNarrationValidator {

    private val STAGE_BRACKETS_REGEX = Regex("\\[[^\\]]{1,40}\\]|\\([^)]{1,30}\\)")
    private val SPEAKER_PREFIX_REGEX = Regex("^(?:narrator|speaker|voiceover|tagapagsalaysay|scene\\s*\\d+)\\s*:\\s*", RegexOption.IGNORE_CASE)

    private val NON_LATIN_GARBAGE_REGEX = Regex("[\\u0400-\\u04FF\\u4E00-\\u9FFF\\u3040-\\u30FF\\uAC00-\\uD7AF\\u0600-\\u06FF\\uFFFD]")
    private val SIX_CONSONANTS_REGEX = Regex("[bcdfghjklmnpqrstvwxyz]{6,}", RegexOption.IGNORE_CASE)
    private val FOUR_REPEATED_CHARS_REGEX = Regex("(.)\\1{3,}", RegexOption.IGNORE_CASE)
    private val LEET_MIXED_TOKEN_REGEX = Regex("^(?=.*[a-zA-Z]{2,})(?=.*\\d)[a-zA-Z0-9]+$")
    private val KNOWN_ALPHANUM_TOKENS = setOf("24h", "24hrs", "100k", "50k", "10k", "1st", "2nd", "3rd", "4th", "5g", "4g", "mp4", "b2b", "b2c")
    private val ALLOWED_CONSONANT_ACRONYMS = setOf(
        "OFW", "GCASH", "CCTV", "SSS", "BIR", "BSP", "DOH", "BPO", "ROI", "PHP",
        "USD", "CEO", "WFH", "ATM", "PNG", "MP4", "AI", "TV", "PH", "PWD", "LTO", "DPWH", "NBI"
    )

    private val LITERAL_IDIOM_OR_META_PATTERNS = listOf(
        "as an ai",
        "here is the json",
        "itlog sa isang basket",
        "mga itlog sa iisang basket",
        "insert pause",
        "tagalog narration here"
    )

    private val TAGALOG_MARKERS = setOf(
        "ang", "ng", "nang", "sa", "na", "mga", "ay", "si", "ni", "ko", "mo", "ka",
        "ba", "po", "kung", "bakit", "paano", "hindi", "pero", "dahil", "kapag", "para",
        "ito", "iyan", "yan", "iyon", "yun", "yung", "may", "mayroon", "meron", "wala",
        "walang", "isang", "isa", "dalawa", "tatlo", "pag", "kaya", "kasi", "lang",
        "din", "rin", "naman", "talaga", "pala", "muna", "sabi", "alam", "pera", "sahod",
        "sweldo", "ipon", "buhay", "araw", "bahay", "tao", "taon", "piso", "gusto",
        "dapat", "huwag", "wag", "bawal", "mas", "laging", "mismo", "tulad", "gaya",
        "hanggang", "noon", "ngayon", "bukas", "bago", "habang", "tungkol", "natin",
        "namin", "nila", "kayo", "kami", "tayo", "siya", "sila", "ako", "ikaw",
        "atin", "amin", "kanila", "dito", "rito", "doon", "roon", "diyan", "riyan",
        "lahat", "bawat", "marami", "konti", "sobra", "kulang", "tama", "mali", "una",
        "huli", "sikreto", "diskarte", "negosyo", "trabaho", "utang", "yaman", "oras",
        "gabi", "umaga", "kaibigan", "anak", "nanay", "tatay", "pamilya", "totoo",
        "akalain", "akala", "grabe", "malaki", "maliit", "mabilis", "mabagal"
    )

    fun cleanForTts(raw: String): String {
        var s = raw.trim()
        s = SPEAKER_PREFIX_REGEX.replace(s, "")
        s = STAGE_BRACKETS_REGEX.replace(s, " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun isValidTagalogNarration(raw: String): Boolean {
        return try {
            validateOrThrow(raw)
            true
        } catch (_: MalformedSceneJsonException) {
            false
        }
    }

    fun validateOrThrow(raw: String) {
        val cleaned = cleanForTts(raw)
        if (cleaned.isEmpty()) {
            throw MalformedSceneJsonException("Narration is empty after sanitization")
        }
        if (NON_LATIN_GARBAGE_REGEX.containsMatchIn(cleaned)) {
            throw MalformedSceneJsonException("Narration contains unsupported non-Latin or replacement characters")
        }

        val lower = cleaned.lowercase()
        for (bad in LITERAL_IDIOM_OR_META_PATTERNS) {
            if (lower.contains(bad)) {
                throw MalformedSceneJsonException("Narration contains forbidden literal/meta phrase: $bad")
            }
        }

        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 3) {
            throw MalformedSceneJsonException("Narration is too short (${words.size} words)")
        }

        var tagalogHits = 0
        for (token in words) {
            val lettersOnly = token.replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$"), "")
            if (lettersOnly.isEmpty()) continue

            val upper = lettersOnly.uppercase()
            val tokLower = lettersOnly.lowercase()

            if (FOUR_REPEATED_CHARS_REGEX.containsMatchIn(tokLower)) {
                throw MalformedSceneJsonException("Narration contains repeated-character gibberish token: $token")
            }
            if (SIX_CONSONANTS_REGEX.containsMatchIn(tokLower) && upper !in ALLOWED_CONSONANT_ACRONYMS) {
                throw MalformedSceneJsonException("Narration contains unpronounceable consonant-cluster gibberish: $token")
            }
            if (LEET_MIXED_TOKEN_REGEX.matches(tokLower) && tokLower !in KNOWN_ALPHANUM_TOKENS) {
                throw MalformedSceneJsonException("Narration contains corrupted alphanumeric gibberish token: $token")
            }
            val alphaPart = tokLower.filter { it in 'a'..'z' }
            if (alphaPart.length >= 5 &&
                alphaPart.none { it in "aeiouy" } &&
                upper !in ALLOWED_CONSONANT_ACRONYMS
            ) {
                throw MalformedSceneJsonException("Narration contains vowel-less gibberish word: $token")
            }

            // Split hyphenated Tagalog words (e.g., nag-iipon, pag-asa) for marker check
            val subParts = tokLower.split('-', '\'')
            for (part in subParts) {
                if (part in TAGALOG_MARKERS ||
                    part.startsWith("nag") ||
                    part.startsWith("mag") ||
                    part.startsWith("nakaka") ||
                    part.startsWith("pinaka")
                ) {
                    tagalogHits++
                    break
                }
            }
        }

        if (words.size >= 5 && tagalogHits == 0) {
            throw MalformedSceneJsonException("Narration lacks natural Tagalog words/particles: $cleaned")
        }
    }
}

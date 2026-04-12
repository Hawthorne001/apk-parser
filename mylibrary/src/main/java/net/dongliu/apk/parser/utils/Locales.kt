package net.dongliu.apk.parser.utils

import java.util.Locale

/**
 * @author dongliu
 */
object Locales {
    /**
     * when do localize, any locale will match this
     */
    @JvmField
    val any = Locale("", "")

    /**
     * How much the given locale match the expected locale.
     */
    @JvmStatic
    fun match(locale: Locale?, targetLocale: Locale): Int {
        if (locale == null) {
            return -1
        }
        val lang1 = normalizeLanguage(locale.language)
        val lang2 = normalizeLanguage(targetLocale.language)
        val languageMatch = lang1 == lang2
        if (languageMatch) {
            if (locale.country == targetLocale.country) {
                return 4
            }
            if (targetLocale.country.isEmpty()) {
                return 3
            }
            // Pseudolocale check: en-XA, ar-XB, etc.
            // These should have lower priority than the default locale (1) if they don't match exactly.
            if (targetLocale.country == "XA" || targetLocale.country == "XB") {
                return 0
            }
            return 2
        }
        return if (targetLocale.language.isEmpty()) 1 else 0
    }

    /**
     * Find the best matching score for a list of locales.
     * Higher is better.
     */
    @JvmStatic
    fun matchScore(locales: List<Locale>, targetLocale: Locale): Long {
        if (locales.isEmpty()) return match(null, targetLocale).toLong()
        
        for (i in 0 until locales.size) {
            val level = match(locales.get(i), targetLocale)
            if (level >= 1) {
                // Primary weight: Position in the user's preference list.
                // Secondary weight: Match perfection level.
                // Tertiary weight: Country code alphabetical tie-breaker.
                
                var score = (locales.size - i).toLong() * 10000 + level * 1000
                
                val country = targetLocale.country
                if (country.length == 2) {
                    // Alphabetical tie-breaker: favor earlier country codes (AU > CA).
                    score += (90 - country[0].code) * 30 + (90 - country[1].code)
                } else if (targetLocale.language.isNotEmpty()){
                    score += 900 // Prefer language-only (Level 3) over mismatched country (Level 2)
                }
                return score
            }
        }
        
        return 0
    }

    private fun normalizeLanguage(language: String): String {
        return when (language) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> language
        }
    }
}

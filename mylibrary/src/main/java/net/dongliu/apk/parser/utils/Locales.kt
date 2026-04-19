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
     * Returns a score:
     * 100: Exact match (Language + Country)
     * 90: Exact match (Language only, both empty country)
     * 70: Language match, but target has empty country (parent match)
     * 0: No match (or default configuration, which is handled by Step 2 fallback to null)
     */
    @JvmStatic
    fun match(locale: Locale?, targetLocale: Locale): Int {
        if (locale == null) {
            // If no locale provided, only match the default (empty) configuration
            return if (targetLocale.language.isEmpty()) 10 else 0
        }
        
        val lang1 = normalizeLanguage(locale.language)
        val lang2 = normalizeLanguage(targetLocale.language)
        
        if (lang1 != lang2) {
            return 0
        }

        // Language matches. Now check country.
        val country1 = locale.country
        val country2 = targetLocale.country

        // Pseudolocale check: ignore them
        if (country2 == "XA" || country2 == "XB") {
            return 0
        }

        if (country1 == country2) {
            // Both are empty OR both are same country
            return if (country1.isEmpty()) 90 else 100
        }
        
        if (country2.isEmpty()) {
            // Target is a parent (language-only). This is a good match for any country in same language.
            return 70
        }

        // Country mismatch (e.g., en_US vs en_GB). User wants fallback to DEFAULT in this case.
        return 0
    }

    @JvmStatic
    fun isParent(target: Locale, candidate: Locale): Boolean {
        if (candidate.language.isEmpty()) return true
        if (normalizeLanguage(candidate.language) != normalizeLanguage(target.language)) return false
        return candidate.country.isEmpty() || candidate.country == target.country
    }

    @JvmStatic
    fun normalizeLanguage(language: String): String {
        return when (language) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> language
        }
    }
}

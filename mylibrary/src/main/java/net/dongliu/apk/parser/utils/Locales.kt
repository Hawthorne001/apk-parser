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
     * 80: Regional variant match (e.g., en_IL matching en_GB)
     * 70: Language-only match (target has generic language)
     * 50: Language match, country mismatch
     * 10: Default configuration (target is empty)
     * 0: No match
     */
    @JvmStatic
    fun match(locale: Locale?, targetLocale: Locale): Int {
        if (locale == null) {
            return if (targetLocale.language.isEmpty()) 10 else 0
        }
        val lang1 = normalizeLanguage(locale.language)
        val lang2 = normalizeLanguage(targetLocale.language)
        
        if (lang2.isEmpty()) {
            return 10 // Default configuration
        }
        
        if (lang1 != lang2) {
            return 0
        }

        val country1 = locale.country
        val country2 = targetLocale.country

        // Pseudolocale check: ignore them as they shouldn't match real user locales
        if (country2 == "XA" || country2 == "XB") {
            return 0
        }

        if (country1 == country2) {
            return if (country1.isEmpty()) 90 else 100
        }
        
        if (country2.isEmpty()) {
            return 70 // Language-only match (app has generic language)
        }

        // English variant matching
        if (lang1 == "en") {
            if (isInternationalEnglish(country1) && isInternationalEnglish(country2)) {
                return 80
            }
            if (isNorthAmericanEnglish(country1) && isNorthAmericanEnglish(country2)) {
                return 80
            }
        }

        return 50 // Language match, country mismatch
    }

    private fun isNorthAmericanEnglish(country: String): Boolean {
        return when (country) {
            "US", "CA", "AS", "GU", "MP", "PR", "VI" -> true
            else -> false
        }
    }

    private fun isInternationalEnglish(country: String): Boolean {
        return when (country) {
            "001", "150", "GB", "AU", "NZ", "IE", "IL", "IN", "ZA", "SG", "HK", "MT", "MY", "PK" -> true
            else -> false
        }
    }

    /**
     * Check if 'candidate' is a valid fallback for 'target'.
     * e.g., 'en' is a parent of 'en_AU', and '' is a parent of 'en'.
     */
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

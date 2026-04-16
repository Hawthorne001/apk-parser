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
     * Returns a level of matching:
     * 4: Exact match (Language + Country)
     * 3: Language-only match (app has generic language)
     * 2: Language match, country mismatch
     * 1: Default configuration (target is empty)
     * 0: No match
     */
    @JvmStatic
    fun match(locale: Locale?, targetLocale: Locale): Int {
        if (locale == null) {
            return 0
        }
        val lang1 = normalizeLanguage(locale.language)
        val lang2 = normalizeLanguage(targetLocale.language)
        
        if (lang2.isEmpty()) {
            return 1 // Default configuration matches everything at level 1
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

        if (country1 == country2 && country1.isNotEmpty()) {
            return 4 // Exact match
        }
        
        if (country2.isEmpty()) {
            return 3 // Language-only match (app has generic language)
        }

        return 2 // Language match, country mismatch
    }

    /**
     * Find the best matching score for a list of locales.
     * Higher is better.
     */
    @JvmStatic
    fun matchScore(locales: List<Locale>, targetLocale: Locale): Long {
        if (locales.isEmpty()) {
            return if (targetLocale.language.isEmpty()) 1L else 0L
        }
        
        for (i in 0 until locales.size) {
            val level = match(locales.get(i), targetLocale)
            if (level >= 1) {
                // Primary weight: Position in user's preference list.
                // Secondary weight: Specificity of match (exact > language-only > country-mismatch > default).
                var score = (locales.size - i).toLong() * 1000000L + level * 10000L
                
                // Tertiary weight: Country tie-breakers for common languages.
                val country1 = locales.get(i).country
                val country2 = targetLocale.country
                if (country2.length == 2) {
                    if (country1 == country2) score += 9000
                    else if (isInternationalEnglish(country1) && isInternationalEnglish(country2)) {
                        if (country2 == "GB") score += 8500
                        else if (country2 == "US") score += 8000
                        else score += (90 - country2[0].code).toLong() * 20 + (90 - country2[1].code).toLong()
                    } else if (country2 == "GB") score += 5000
                    else if (country2 == "US") score += 4000
                    else score += (90 - country2[0].code).toLong() * 10 + (90 - country2[1].code).toLong()
                } else if (targetLocale.language.isNotEmpty()) {
                    score += 7000
                }
                return score
            }
        }
        
        return 0L
    }

    private fun isInternationalEnglish(country: String): Boolean {
        return when (country) {
            "001", "150", "GB", "AU", "NZ", "IE", "IL", "IN", "ZA", "SG", "HK", "MT", "MY", "PK" -> true
            else -> false
        }
    }

    /**
     * Get the best matching locale from the app's supported locales for the given user preference list.
     */
    @JvmStatic
    fun getBestMatch(locales: List<Locale>, appLocales: Set<Locale>): Locale {
        var bestLocale = any
        var maxScore: Long = -1
        for (appLocale in appLocales) {
            val score = matchScore(locales, appLocale)
            if (score > maxScore) {
                maxScore = score
                bestLocale = appLocale
            }
        }
        return bestLocale
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

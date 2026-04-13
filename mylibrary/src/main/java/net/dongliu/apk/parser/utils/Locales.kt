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
        
        if (lang2.isEmpty()) {
            if (lang1 == "en") return 2 // For English, Default is a language match
            return 1 // Default matches everything else at level 1
        }
        
        if (lang1 != lang2) {
            return 0
        }

        val country1 = locale.country
        val country2 = targetLocale.country

        if (country1 == country2) {
            return 4 // Exact match
        }
        
        if (country2.isEmpty()) {
            return 3 // Language match
        }

        // Pseudolocale check
        if (country2 == "XA" || country2 == "XB") {
            return 0
        }

        return 2 // Language match, country mismatch
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
                var score = (locales.size - i).toLong() * 10000 + level * 1000
                
                val country1 = locales.get(i).country
                val country2 = targetLocale.country
                if (country2.length == 2) {
                    // Tie-breaker within the same locale and level.
                    if (country1 == country2) score += 995
                    else if (isInternationalEnglish(country1) && isInternationalEnglish(country2)) {
                        // International English tie-breaker: prefer GB as standard.
                        if (country2 == "GB") score += 990
                        else if (country2 == "US") score += 985
                        else {
                            // Alphabetical
                            score += (90 - country2[0].code) * 2 + (90 - country2[1].code) / 10
                        }
                    } else if (country2 == "GB") score += 800
                    else if (country2 == "US") score += 750
                    else {
                        score += (90 - country2[0].code) * 5 + (90 - country2[1].code)
                    }
                } else if (targetLocale.language.isNotEmpty()) {
                    score += 980
                } else if (normalizeLanguage(locales.get(i).language) == "en") {
                    // For English users, prefer Default over "cousins" that are not international English matches
                    score += 970
                }
                return score
            }
        }
        
        return 0
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
        if (candidate.language != target.language) return false
        return candidate.country.isEmpty() || candidate.country == target.country
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

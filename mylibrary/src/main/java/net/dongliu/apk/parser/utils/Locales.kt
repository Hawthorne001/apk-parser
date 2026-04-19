package net.dongliu.apk.parser.utils

import java.util.Locale

/**
 * Mimics Android's Resource resolution logic for Locales.
 */
object Locales {
    const val PERFECT_SCORE = Integer.MAX_VALUE

    /**
     * Scores a candidate locale against the desired device locale.
     * Higher is better.
     * 0 means completely incompatible.
     */
    @JvmStatic
    fun match(deviceLocale: Locale?, candidate: Locale): Int {
        // Default/Empty configuration is the lowest possible positive match
        if (candidate.language.isEmpty()) {
            return 1
        }
        val candidateLanguage = normalizeLanguage(candidate.language)
        // 1. Handle Null Device Locale
        if (deviceLocale == null) {
            // Only the empty/default configuration is a valid match if no locale requested
            return if (candidateLanguage.isEmpty()) 1 else 0
        }
        val deviceLanguage = normalizeLanguage(deviceLocale.language)
        if (deviceLanguage != candidateLanguage)
            return 0
        val deviceCountry = deviceLocale.country
        val candidateCountry = candidate.country
        // 1. Perfect Match
        if (deviceCountry == candidateCountry && !deviceCountry.isEmpty())
            return PERFECT_SCORE
        // 2. Language-only match (e.g., candidate is 'en')
        if (candidateCountry.isEmpty())
            return 50
        // 3. Representative Fallback Match (The en-GB / en-US logic)
        // Android uses an internal table. en-GB is the representative for many
        // International English regions (like IL, AU, etc.)
        if (isRepresentativeMatch(deviceLanguage, deviceCountry, candidateCountry)) {
            return 40
        }
        // 4. Siblings (en-US vs en-GB) - Android usually prefers Default over a mismatched country
        // unless it's a representative.
        return 0
    }

    /**
     * Simplified version of Android's representative table.
     */
    private fun isRepresentativeMatch(lang: String, deviceCountry: String, candidateCountry: String): Boolean {
        if (lang == "en") {
            // en-GB is the representative for most of the world except North America
            val britishRegions = setOf("IL", "GB", "AU", "NZ", "IE", "ZA", "IN", "HK", "MT", "SG")
            if (candidateCountry == "GB" && britishRegions.contains(deviceCountry)) return true

            // en-US is the representative for Americas
            val americanRegions = setOf("US", "CA", "PH", "LR")
            if (candidateCountry == "US" && americanRegions.contains(deviceCountry)) return true
        }

        if (lang == "zh") {
            // zh-HK and zh-MO often fallback to zh-TW (Traditional)
            if (candidateCountry == "TW" && (deviceCountry == "HK" || deviceCountry == "MO")) return true
        }

        return false
    }

    @JvmStatic
    fun normalizeLanguage(language: String): String {
        return when (language) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> language.lowercase()
        }
    }
}

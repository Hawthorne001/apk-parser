package net.dongliu.apk.parser.utils

import androidx.core.text.ICUCompat
import java.util.Locale

/**
 * Mimics Android's Resource resolution logic for Locales.
 */
object Locales {
    const val PERFECT_SCORE = Integer.MAX_VALUE

    private fun isPseudoLocale(locale: Locale): Boolean {
        return (locale.language == "en" && locale.country == "XA") ||
                (locale.language == "ar" && locale.country == "XB")
    }

    /**
     * Scores a candidate locale against the desired device locale.
     * Higher is better.
     * 0 means completely incompatible.
     */
    @JvmStatic
    fun match(deviceLocale: Locale?, candidate: Locale): Int {
        // 1. Perfect Match (including pseudo-locales if they match exactly)
        if (deviceLocale != null && deviceLocale == candidate) {
            return PERFECT_SCORE
        }

        // Default/Empty configuration is the lowest possible positive match
        if (candidate.language.isEmpty()) {
            return 1
        }

        // If languages are different, they definitely don't match
        val candidateLanguage = normalizeLanguage(candidate.language)
        val deviceLanguage = if (deviceLocale != null) normalizeLanguage(deviceLocale.language) else ""

        if (deviceLanguage != candidateLanguage) {
            return 0
        }

        // Languages match!

        // 2. Pseudo-locale handling: If they are not equal (checked above), and one is pseudo, it's a mismatch
        if (isPseudoLocale(candidate) || (deviceLocale != null && isPseudoLocale(deviceLocale))) {
            return 0
        }

        // 3. Script matching
        val deviceScript = if (deviceLocale != null) getScript(deviceLocale) else ""
        val candidateScript = getScript(candidate)
        if (deviceScript != candidateScript && deviceScript.isNotEmpty() && candidateScript.isNotEmpty()) {
            return 0
        }

        // Script matches (or at least one is missing).

        val candidateCountry = candidate.country
        val deviceCountry = deviceLocale?.country ?: ""

        // 4. Candidate has no country (general language match)
        if (candidateCountry.isEmpty()) {
            return 50
        }

        // 5. Representative Fallback Match (e.g., en-GB for en-AU)
        if (deviceLocale != null && isRepresentativeMatch(deviceLanguage, deviceCountry, candidateCountry)) {
            return 40
        }

        // 6. Sibling (same language, different country, neither is a representative)
        // We return 0 now to prefer the default configuration over a wrong regional variant.
        return 0
    }

    private fun getScript(locale: Locale): String {
        return try {
            ICUCompat.maximizeAndGetScript(locale) ?: ""
        } catch (e: Throwable) {
            ""
        }
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

package com.lb.apkparserdemo

import android.app.Application
import androidx.core.text.ICUCompat
import net.dongliu.apk.parser.utils.LocaleScriptResolver
import net.dongliu.apk.parser.utils.Locales
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Locales.scriptResolver = object : LocaleScriptResolver {
            override fun getScript(locale: Locale): String {
                return ICUCompat.maximizeAndGetScript(locale) ?: ""
            }
        }
    }
}

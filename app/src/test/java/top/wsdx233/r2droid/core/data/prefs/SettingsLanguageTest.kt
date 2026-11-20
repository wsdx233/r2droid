package top.wsdx233.r2droid.core.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsLanguageTest {
    @Test
    fun preservesBrazilianPortugueseRegion() {
        val locale = SettingsManager.appLocale("pt-BR")
        assertEquals("pt", locale?.language)
        assertEquals("BR", locale?.country)
    }

    @Test
    fun restoresLegacyLanguagePreferences() {
        assertEquals("zh-CN", SettingsManager.appLocale("zh")?.toLanguageTag())
        assertEquals("en", SettingsManager.appLocale("en")?.toLanguageTag())
        assertEquals("ru", SettingsManager.appLocale("ru")?.toLanguageTag())
        assertNull(SettingsManager.appLocale("system"))
        assertNull(SettingsManager.appLocale(null))
    }
}

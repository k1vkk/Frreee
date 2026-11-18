package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CryptoPrefsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NEXORA VPN", appName)
    }

    @Test
    fun `arabic localization resolves 10 screen strings cleanly`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
        }
        val arContext = baseContext.createConfigurationContext(cfg)
        assertEquals("الرئيسية", arContext.getString(R.string.nav_home))
        assertTrue(arContext.getString(R.string.ping_test_title).contains("Ping"))
        assertTrue(arContext.getString(R.string.support_title).isNotBlank())
        assertTrue(arContext.getString(R.string.conn_history_title).isNotBlank())
    }

    @Test
    fun `crypto prefs helper round trips secret safely`() {
        assertEquals("", CryptoPrefsHelper.encrypt(""))
        assertEquals("", CryptoPrefsHelper.decrypt(""))
        val sample = "jwt-sample-token-value"
        val enc = CryptoPrefsHelper.encrypt(sample)
        assertEquals(sample, CryptoPrefsHelper.decrypt(enc))
    }
}

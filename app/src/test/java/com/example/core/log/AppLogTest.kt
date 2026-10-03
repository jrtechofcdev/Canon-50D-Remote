package com.example.core.log

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLogTest {

    private fun waitFor(text: String): String {
        repeat(40) {
            val content = AppLog.todayFile().takeIf { it.exists() }?.readText().orEmpty()
            if (content.contains(text)) return content
            Thread.sleep(50)
        }
        return AppLog.todayFile().takeIf { it.exists() }?.readText().orEmpty()
    }

    @Test
    fun linesGoToTodaysFileAndIntoTheBundle() {
        AppLog.init(ApplicationProvider.getApplicationContext<Context>())
        AppLog.configure(true, null)
        AppLog.i("TESTE", "foto IMG_0001 salva")
        AppLog.e("TESTE", "falhou de propósito", IllegalStateException("sem memória"))
        val content = waitFor("falhou de propósito")
        assertTrue(content.contains("I/TESTE: foto IMG_0001 salva"))
        assertTrue(content.contains("E/TESTE: falhou de propósito"))
        assertTrue(content.contains("IllegalStateException: sem memória"))
        val bundle = AppLog.bundle().readText()
        assertTrue(bundle.contains("foto IMG_0001 salva"))
    }

    @Test
    fun offKeepsOnlyErrors() {
        AppLog.init(ApplicationProvider.getApplicationContext<Context>())
        AppLog.configure(false, null)
        AppLog.i("TESTE", "rotina que não deve aparecer")
        AppLog.e("TESTE", "erro que sempre aparece")
        val content = waitFor("erro que sempre aparece")
        assertTrue(content.contains("erro que sempre aparece"))
        assertFalse(content.contains("rotina que não deve aparecer"))
        AppLog.configure(true, null)
    }

    @Test
    fun memoryLineIsReadable() {
        assertTrue(AppLog.memory().matches(Regex("memória \\d+/\\d+ MB, nativa \\d+ MB")))
    }
}

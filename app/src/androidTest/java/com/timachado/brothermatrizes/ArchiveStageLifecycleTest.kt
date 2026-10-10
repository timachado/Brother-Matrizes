package com.timachado.brothermatrizes

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the v1.0.0 ZIP import failure:
 * inventory changed null -> ZIP; the old DisposableEffect read the new
 * inventory and deleted its staged source immediately after scanning.
 */
@RunWith(AndroidJUnit4::class)
class ArchiveStageLifecycleTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test fun zipRemainsStagedUntilExtractorScreenIsClosed() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val shared = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val zipFile = File(shared, "brother-archive-regression.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            for (name in listOf("Rosa-13x18.JEF", "Rosa-16x26.PES", "Rosa-20x30.DST")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(1, 2, 3, 4))
                zip.closeEntry()
            }
        }
        val before = ctx.cacheDir.listFiles().orEmpty()
            .filter { it.name.startsWith("archive-") && it.name.endsWith(".bin") }
            .map { it.absolutePath }.toSet()
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", zipFile)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ActivityScenario.launch<MainActivity>(intent).use {
                composeRule.waitUntil(timeoutMillis = 25_000) {
                    composeRule.onAllNodesWithText(
                        "ZIP • 3 matriz(es) • 0 fonte(s) • 0 arquivo(s) ignorado(s)"
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()
                composeRule.onNodeWithText("Extrator Inteligente").assertIsDisplayed()
                val staged = ctx.cacheDir.listFiles().orEmpty().filter {
                    it.name.startsWith("archive-") && it.name.endsWith(".bin") &&
                        it.absolutePath !in before
                }
                assertTrue("ZIP must exist after scan and recomposition", staged.size == 1)
                val stagedZip = staged.single()
                assertTrue("Source must remain readable while item list is displayed",
                    stagedZip.isFile && stagedZip.length() > 0L)

                composeRule.onNodeWithText("‹ Voltar").performClick()
                composeRule.waitUntil(timeoutMillis = 8_000) { !stagedZip.exists() }
                assertFalse("ZIP source must be cleaned when leaving extractor", stagedZip.exists())
            }
        } finally {
            zipFile.delete()
        }
    }
}

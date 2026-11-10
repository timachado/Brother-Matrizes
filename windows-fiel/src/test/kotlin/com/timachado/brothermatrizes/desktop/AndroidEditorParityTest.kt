package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.MatrixEditor
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifica identidade da interface Editor e motor Kotlin oficial.
 * Não homologa o teclado Windows nem a máquina de bordar real.
 */
class AndroidEditorParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `Editor Desktop preserva tela Android exceto BackHandler de Activity`() {
        val source = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/EditorScreen.kt"
        )).replace("\r\n", "\n")
        val imported = Files.readString(root.resolve(
            "build/generated/android-editor/kotlin/com/timachado/brothermatrizes/EditorScreen.kt"
        )).replace("\r\n", "\n")
        val expected = source
            .replace("import androidx.activity.compose.BackHandler\n", "")
            .replace("    BackHandler {\n        requestBack()\n    }\n\n", "")
        assertEquals(expected, imported)
        assertTrue(imported.contains("showExitDialog"))
        assertTrue(imported.contains("Alterações não aplicadas"))
        assertTrue(imported.contains("onApply(preview)"))

        val officialActivity = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/MainActivity.kt"
        )).replace("\r\n", "\n")
        val officialHoop = officialActivity.substringAfter(
            "internal fun recommendedHoopFor("
        ).substringBefore("\nprivate sealed interface Screen").trimEnd()
        val extracted = Files.readString(root.resolve(
            "build/generated/android-editor/kotlin/com/timachado/brothermatrizes/RecommendedHoopFor.kt"
        )).replace("\r\n", "\n")
        assertTrue(extracted.contains("internal fun recommendedHoopFor(" + officialHoop))
        val host = Files.readString(root.resolve(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt"
        ))
        assertTrue(host.contains("onEdit = { detail = ActiveDetail.EDITOR }"))
        assertTrue(host.contains("activeDesign = edited"))
    }

    @Test
    fun `edição de pontos ocorre em memória e não altera a matriz de origem`() {
        val source = TextLayoutGenerator.generate(TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "Maria",
                heightMm = 12f,
                style = TextStitchStyle.RUNNING
            )
        )).getOrThrow()
        val originalPoints = source.points.toList()
        val originalWidth = source.bounds.widthMm
        val edited = MatrixEditor.apply(source, EditTransform(
            scale = 1.25f,
            rotationDegrees = 18f,
            offsetXUnits = 23,
            offsetYUnits = -17,
            mirrorHorizontal = true,
            threadColors = source.threadColors
        ))
        assertEquals(originalPoints, source.points)
        assertEquals(source.points.size, edited.points.size)
        assertNotEquals(originalPoints, edited.points)
        assertTrue(edited.isModified)
        assertTrue(edited.bounds.widthMm > 0f)
        assertTrue(edited.bounds.heightMm > 0f)
        assertTrue(originalWidth > 0f)
        assertTrue(MatrixConverter.convert(edited, "PES").getOrThrow().bytes.size > 100)
    }
}

package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gate: formatos FLP, DST, PES e JEF do Windows devem continuar executando
 * os parsers e o codec da mesma revisão Android, e não forks antigos.
 */
class AndroidFileCodecSourceParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `codec FLP v11 Desktop preserva literalmente os metodos ProjectCodec Android`() {
        val original = Files.readString(
            root.resolve("../app/src/main/java/com/timachado/brothermatrizes/core/project/ProjectStore.kt")
        ).replace("\r\n", "\n")
        val expected = original.substringBefore("\nobject ProjectStore {")
            .replace("import android.content.Context\n", "")
            .replace(
                "import com.timachado.brothermatrizes.core.storage.AtomicFileWriter\n", ""
            ) + "\n"
        val generated = Files.readString(
            root.resolve(
                "build/generated/project-codec/kotlin/" +
                    "com/timachado/brothermatrizes/core/project/ProjectCodec.kt"
            )
        )
        assertEquals(expected, generated)
        assertTrue(generated.contains("private const val VERSION ="))
        assertTrue(generated.contains("11"))
        assertFalse(generated.contains("object ProjectStore {"))
    }

    @Test
    fun `importadores de arquivos usam codigo original Android sem URI`() {
        val original = Files.readString(
            root.resolve(
                "../app/src/main/java/com/timachado/brothermatrizes/" +
                    "core/embroidery/EmbroideryLoader.kt"
            )
        ).replace("\r\n", "\n")
        val expected = original.substring(0, original.indexOf("    fun load("))
            .replace("import android.content.ContentResolver\n", "")
            .replace("import android.net.Uri\n", "")
            .replace("import android.provider.OpenableColumns\n", "") +
            original.substring(
                original.indexOf("    fun loadBytes("),
                original.indexOf("    private class FileTooLargeException")
            ) + "}\n"
        val generated = Files.readString(
            root.resolve(
                "build/generated/android-embroidery-loader/kotlin/" +
                    "com/timachado/brothermatrizes/core/embroidery/EmbroideryLoader.kt"
            )
        )
        assertEquals(expected, generated)
        assertTrue(generated.contains("fun loadBytes("))
        assertTrue(generated.contains("DstParser.parse("))
        assertTrue(generated.contains("EmbroideryIoParser"))
        assertFalse(generated.contains("import android."))
    }
}

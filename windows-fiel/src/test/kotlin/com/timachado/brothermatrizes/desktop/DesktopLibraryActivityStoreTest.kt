package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopLibraryActivityStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Preferências de teste em diretório isolado: nunca acessar AppData real. */
class DesktopLibraryActivityStoreTest {
    @Test
    fun `favoritos e recentes persistem após reconstruir a Store`() {
        val root = Files.createTempDirectory("bm-favorites-recent-")
        try {
            val settings = root.resolve("library.tsv")
            val one = root.resolve("cliente-a.flp").toString()
            val two = root.resolve("cliente-b.flp").toString()
            val store = DesktopLibraryActivityStore(settings)
            assertEquals(DesktopLibraryActivityStore.State(), store.load())
            store.save(DesktopLibraryActivityStore.State(
                favorites = setOf(one, two),
                recents = listOf(two, one),
                selectedFolder = root.toString()
            ))
            val restored = DesktopLibraryActivityStore(settings).load()
            assertEquals(setOf(one, two), restored.favorites)
            assertEquals(listOf(two, one), restored.recents)
            assertEquals(root.toString(), restored.selectedFolder)
            assertTrue(Files.readString(settings).startsWith("BROTHER_MATRIZES_LIBRARY_ACTIVITY_V1"))
            // O armazenamento guarda IDs, não dados ou matrizes.
            assertTrue(!Files.exists(root.resolve("cliente-a.flp")))
        } finally {
            Files.list(root).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `corrupção e links simbólicos não são sobrescritos`() {
        val root = Files.createTempDirectory("bm-settings-corrupt-")
        try {
            val settings = root.resolve("library.tsv")
            Files.writeString(settings, "INVALID VERSION")
            val store = DesktopLibraryActivityStore(settings)
            assertFailsWith<IllegalArgumentException> { store.load() }
            // Uma leitura inválida é tratada pela UI como bloqueio de persistência.
            Files.deleteIfExists(settings)
            val link = root.resolve("settings-link.tsv")
            val created = runCatching {
                Files.createSymbolicLink(link, root.resolve("nao-existe"))
            }.isSuccess
            if (created) {
                assertFailsWith<IllegalArgumentException> {
                    DesktopLibraryActivityStore(link).save(DesktopLibraryActivityStore.State())
                }
            }
        } finally {
            Files.list(root).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `não permite salvar identificadores relativos`() {
        val root = Files.createTempDirectory("bm-settings-invalid-")
        try {
            val store = DesktopLibraryActivityStore(root.resolve("prefs.tsv"))
            assertFailsWith<IllegalArgumentException> {
                store.save(DesktopLibraryActivityStore.State(
                    favorites = setOf("../outro-projeto.flp")
                ))
            }
        } finally {
            Files.list(root).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(root)
        }
    }
}

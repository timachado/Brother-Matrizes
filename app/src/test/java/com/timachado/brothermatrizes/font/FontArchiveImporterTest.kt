package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FontArchiveImporterTest {
    @Test fun acceptsOnlyFontEntriesAndUsesBaseName() {
        assertEquals("Bordado.ttf", FontArchiveImporter.permittedEntryName("folder/Bordado.ttf"))
        assertEquals("Floral.otf", FontArchiveImporter.permittedEntryName("fonts\\Floral.otf"))
        assertNull(FontArchiveImporter.permittedEntryName("../readme.exe"))
        assertNull(FontArchiveImporter.permittedEntryName("scripts/danger.sh"))
        assertNull(FontArchiveImporter.permittedEntryName("image.png"))
    }

    @Test fun rejectsInvalidAndControlCharacters() {
        assertNull(FontArchiveImporter.permittedEntryName("../.."))
        assertNull(FontArchiveImporter.permittedEntryName("hello\u0000.ttf"))
        assertNull(FontArchiveImporter.permittedEntryName("   "))
    }
}

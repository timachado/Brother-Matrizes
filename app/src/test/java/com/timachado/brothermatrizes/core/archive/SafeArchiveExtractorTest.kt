package com.timachado.brothermatrizes.core.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SafeArchiveExtractorTest {
    @Test fun magicSignaturesDoNotTrustFileExtensions() {
        assertEquals(ArchiveFormat.ZIP, SafeArchiveExtractor.identify(
            byteArrayOf(0x50,0x4b,0x03,0x04)))
        assertEquals(ArchiveFormat.RAR, SafeArchiveExtractor.identify(
            byteArrayOf(0x52,0x61,0x72,0x21,0x1a,0x07,0x00)))
        assertEquals(ArchiveFormat.RAR, SafeArchiveExtractor.identify(
            byteArrayOf(0x52,0x61,0x72,0x21,0x1a,0x07,0x01,0x00)))
        assertEquals(ArchiveFormat.SEVEN_Z, SafeArchiveExtractor.identify(
            byteArrayOf(0x37,0x7a,0xbc.toByte(),0xaf.toByte(),0x27,0x1c)))
    }

    @Test(expected = IllegalStateException::class)
    fun invalidSignatureRejected() {
        SafeArchiveExtractor.identify(byteArrayOf(1,2,3,4))
    }

    @Test fun pathsAndDepthsAreValidatedBeforeImport() {
        assertNull(SafeArchiveExtractor.validName("../file.pes"))
        assertNull(SafeArchiveExtractor.validName("/root/file.jef"))
        assertNull(SafeArchiveExtractor.validName("C:\\folder\\file.dst"))
        assertNull(SafeArchiveExtractor.validName("inside/../fake.pes"))
        assertNull(SafeArchiveExtractor.validName("inside//fake.pes"))
        assertNull(SafeArchiveExtractor.validName("x/./file.dst"))
        assertNull(SafeArchiveExtractor.validName("evil\u0000.pes"))
        assertNotNull(SafeArchiveExtractor.validName("folder/motivo.pes"))
    }

    @Test fun onlyExistingEmbroideryFormatsAndFontsAreListed() {
        assertEquals(ArchiveCategory.MATRIX,
            SafeArchiveExtractor.describe(0,"folder/Coração.PES",120)?.category)
        assertEquals(ArchiveCategory.FONT,
            SafeArchiveExtractor.describe(1,"fonts/Rosa.otf",100)?.category)
        assertEquals("DST", SafeArchiveExtractor.describe(3,"flor.dst",50)?.extension)
        assertNull(SafeArchiveExtractor.describe(2,"malware.exe",100))
        assertNull(SafeArchiveExtractor.describe(4,"file.xxx",100))
        assertNull(SafeArchiveExtractor.describe(5,"too-big.pes",25L*1024*1024))
        assertNull(SafeArchiveExtractor.describe(6,"zip-bomb.dst",1000000,1))
    }
}

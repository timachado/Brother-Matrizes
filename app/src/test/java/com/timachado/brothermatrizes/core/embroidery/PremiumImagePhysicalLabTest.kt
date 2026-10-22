package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Debug QA only. Does not certify physical compatibility. */
class PremiumImagePhysicalLabTest {
    private fun ring(w:Int=64,h:Int=64):IntArray = IntArray(w*h) { index ->
        val x=index%w;val y=index/w
        if(x in 10..54 && y in 10..54 &&
            !(x in 26..38 && y in 26..38)) 0xffab6428.toInt() else 0
    }

    @Test fun generatedTransparentRingCanBeSimulatedBeforeExport() {
        val pixels=ring()
        val before=pixels.copyOf()
        val design=PremiumImagePhysicalLab.propose(
            64,64,pixels,60f,HoopProfile.H100X100
        ).getOrThrow()
        assertTrue(design.stitchCount>80)
        assertEquals(2,design.jumpCount)
        assertEquals(0,design.colorChanges)
        assertTrue(design.points.any{it.command==StitchCommand.TRIM})
        assertEquals(StitchCommand.END,design.points.last().command)
        assertTrue(HoopValidator.validate(design,HoopProfile.H100X100).fits)
        assertTrue(design.sourceBytes.isEmpty())
        val encoded=PremiumImagePhysicalLab.pesForBench(design)
        assertTrue("PES readback: " +
            encoded.exceptionOrNull()?.stackTraceToString(),encoded.isSuccess)
        val pes=encoded.getOrThrow()
        assertTrue(pes.fileName.endsWith(".pes"))
        assertTrue(pes.bytes.size>=512)
        assertTrue(pes.bytes.copyOfRange(0,4).toString(Charsets.US_ASCII)=="#PES")
        assertTrue(pixels.contentEquals(before))
    }

    @Test fun photographsAndOutOfHoopSamplesAreRejected() {
        assertTrue(PremiumImagePhysicalLab.propose(
            64,64,IntArray(64*64){0xffaabbcc.toInt()},60f,HoopProfile.H100X100
        ).isFailure)
        assertTrue(PremiumImagePhysicalLab.propose(
            64,64,ring(),120f,HoopProfile.H100X100
        ).isFailure)
        assertFalse(PremiumImagePhysicalLab.propose(
            64,64,ring(),60f,HoopProfile.H100X100
        ).isFailure)
    }
    @Test fun diagonallyTouchingInkIslandsAreTwoClosedOutlinesNotOneBridge() {
        val w=64
        val h=64
        val pixels=IntArray(w*h) { index ->
            val x=index%w
            val y=index/w
            if ((x in 8..24 && y in 8..24) ||
                (x in 25..42 && y in 25..42)
            ) 0xffeab85d.toInt() else 0
        }
        val design=PremiumImagePhysicalLab.propose(
            w,h,pixels,65f,HoopProfile.H100X100
        ).getOrThrow()
        assertEquals(
            "Dois elementos só tocam no canto; não costurar entre eles",
            2,design.jumpCount
        )
        assertTrue(design.points.any { it.command==StitchCommand.TRIM })
        assertTrue(design.stitchCount>60)
        assertTrue(HoopValidator.validate(design,HoopProfile.H100X100).fits)
    }

    @Test fun multipleTextLikeDisconnectedContoursCanBeSimulatedBeyondOldEightLoopLimit() {
        val w=160
        val h=100
        val pixels=IntArray(w*h) { index ->
            val x=index%w
            val y=index/w
            val column=(x-9)/12
            if(x>=9 && column in 0..11 &&
                x in (9+column*12)..(15+column*12) &&
                y in 30..63
            ) 0xffeeac51.toInt() else 0
        }
        val design=PremiumImagePhysicalLab.propose(
            w,h,pixels,92f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(12,design.jumpCount)
        assertTrue(design.points.count {
            it.command==StitchCommand.TRIM
        }>=11)
        assertTrue(design.stitchCount>150)
    }


}

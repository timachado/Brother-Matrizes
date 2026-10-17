package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterQaReadinessReportTest {
    private val hoop=HoopProfile.H130X180
    private fun p(x:Int,y:Int,type:StitchCommand)=
        EmbroideryPoint(x,y,type,0)

    private fun small(
        format:String="QA_AUTO_STITCH",
        repeated:Boolean=false
    ):EmbroideryDesign {
        val path=if(repeated) buildList {
            repeat(3) {
                add(p(0,0,StitchCommand.JUMP))
                add(p(25,0,StitchCommand.STITCH))
            }
        } else listOf(
            p(0,0,StitchCommand.JUMP),
            p(15,0,StitchCommand.STITCH),
            p(30,0,StitchCommand.STITCH)
        )
        val commands=path+path.last().copy(command=StitchCommand.END)
        return EmbroideryDesign(
            fileName="simulação.qa",format=format,
            label="QA technical report",
            points=commands,
            bounds=EmbroideryBounds(
                0,if(repeated)25 else 30,0,0
            ),
            stitchCount=commands.count { it.command==StitchCommand.STITCH },
            jumpCount=commands.count { it.command==StitchCommand.JUMP },
            colorChanges=0,endFound=true,
            sourceBytes=ByteArray(0),
            threadColors=listOf(0xff1c3a58.toInt()),
            isModified=true,hoopProfile=hoop
        )
    }

    @Test fun staticGreenReportIsStillMissingMachineAndImageEvidence() {
        val report=RasterQaReadinessReportBuilder.collect(
            small(),hoop
        ).getOrThrow()
        assertFalse(report.exportPermitted)
        assertFalse(report.machineCertified)
        assertFalse(report.hasStaticFailures)
        assertTrue(report.missingEvidence)
        assertEquals(2,report.stitchCount)
        assertNotNull(report.threadLengthMm)
        assertNull(report.sourceCoverage)
        assertEquals(RasterQaGateStatus.PASS,
            report.gates.first { it.label.contains("agulhadas") }.status)
        assertEquals(RasterQaGateStatus.MISSING,
            report.gates.first { it.label.contains("roundtrip") }.status)
        assertTrue(report.toMarkdown().contains("NÃO HOMOLOGADO"))
        assertTrue(report.toMarkdown().contains("Exportação:** BLOQUEADA"))
        assertTrue(report.toMarkdown().contains("não autoriza envio"))
    }

    @Test fun repeatedNeedlePenetrationIsAFailedStaticGate() {
        val report=RasterQaReadinessReportBuilder.collect(
            small(repeated=true),hoop
        ).getOrThrow()
        assertTrue(report.hasStaticFailures)
        assertEquals(RasterQaGateStatus.FAIL,
            report.gates.first { it.label.contains("agulhadas") }.status)
        assertTrue(report.toMarkdown().contains("BLOQUEADO EM QA"))
        assertFalse(report.exportPermitted)
    }

    @Test fun importedPesAndWrongHoopAreNeverAuditedAsImageQa() {
        assertTrue(RasterQaReadinessReportBuilder.collect(
            small(format="PES"),hoop
        ).isFailure)
        assertTrue(RasterQaReadinessReportBuilder.collect(
            small(),HoopProfile.H100X100
        ).isFailure)
    }

    @Test fun fullSyntheticLogoGetsMeasuredButStillCannotExport() {
        val w=160;val h=100
        val blue=0xff153853.toInt()
        val gold=0xffc99737.toInt()
        val src=RasterImagePixels(w,h,IntArray(w*h){ i->
            val x=i%w;val y=i/w
            when {
                x in 14..95 && y in 24..32 -> blue
                x in 19..103 && y in 63..71 -> gold
                else -> 0xffffffff.toInt()
            }
        },"duas faixas sintéticas")
        val design=RasterAutomaticLogoPreview.create(
            src,90f,hoop
        ).getOrThrow()
        val report=RasterQaReadinessReportBuilder.collect(
            design,hoop,src,90f
        ).getOrThrow()
        assertFalse(report.hasStaticFailures)
        assertTrue(report.sourceCoverage!!>.60)
        assertTrue(report.sourceWrongColorFraction!!<.12)
        assertEquals(RasterQaGateStatus.PASS,
            report.gates.first { it.label.contains("Fidelidade global") }.status)
        assertTrue(report.missingEvidence)
        assertFalse(report.exportPermitted)
        assertFalse(report.machineCertified)
    }

    @Test fun swappedThreadColorsAreReportedAsFidelityFailure() {
        val w=160;val h=100
        val blue=0xff153853.toInt()
        val gold=0xffc99737.toInt()
        val src=RasterImagePixels(w,h,IntArray(w*h){ i->
            val x=i%w;val y=i/w
            when {
                x in 14..95 && y in 24..32 -> blue
                x in 19..103 && y in 63..71 -> gold
                else -> 0xffffffff.toInt()
            }
        },"cor QA trocada")
        val original=RasterAutomaticLogoPreview.create(
            src,90f,hoop
        ).getOrThrow()
        val swapped=original.copy(threadColors=original.threadColors.reversed())
        val report=RasterQaReadinessReportBuilder.collect(
            swapped,hoop,src,90f
        ).getOrThrow()
        assertTrue(report.hasStaticFailures)
        assertEquals(RasterQaGateStatus.FAIL,
            report.gates.first { it.label.contains("Fidelidade global") }.status)
        assertFalse(report.exportPermitted)
    }

    @Test fun partialSourceMetadataIsRejected() {
        assertTrue(RasterQaReadinessReportBuilder.collect(
            small(),hoop,sourceWidthMm=90f
        ).isFailure)
    }
}

package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterQaStructuralAuditReceiptTest {
    private fun raster():RasterImagePixels {
        val width=96
        val height=80
        val argb=IntArray(width*height)
        for(y in 12..37)for(x in 10..26)
            argb[y*width+x]=0xffbd6326.toInt()
        for(y in 12..37)for(x in 35..51)
            argb[y*width+x]=0xffd9d9d9.toInt()
        for(y in 23..25)for(x in 27..34)
            argb[y*width+x]=0xd8bd6326.toInt()
        return RasterImagePixels(width,height,argb,"duas regiões QA")
    }

    @Test fun receiptContainsRealSourceFingerprintAndSpatialAudit() {
        val image=raster()
        val original=image.argb.copyOf()
        val report=RasterAlphaThresholdSweep.inspect(image).getOrThrow()
        val receipt=RasterQaStructuralAuditReceipt.generate(report).getOrThrow()
        val topology=RasterSourceTopologyDiagnostics.inspect(image).getOrThrow()
        assertTrue(receipt.startsWith("QA-ABERTURA-DE-VAZADOS-V3 | "))
        assertTrue(receipt.contains("fingerprint=${topology.fingerprint}"))
        assertTrue(receipt.contains("Auditoria espacial alfa QA"))
        assertTrue(receipt.contains("conectores-frágeis="))
        assertTrue(receipt.contains("intervalos-com-mudança-de-vazados="))
        assertTrue(receipt.contains("Localização de vazados perdidos QA"))
        assertTrue(receipt.contains("vazados-com-rota-de-abertura="))
        assertTrue(receipt.contains("160→180"))
        assertTrue(receipt.contains("208→224"))
        assertTrue(receipt.contains("não é PES"))
        assertTrue(image.argb.contentEquals(original))
        assertFalse(report.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun oldHistogramCannotPretendToBeTheNewReceipt() {
        val report=RasterAlphaThresholdSweep.inspect(raster()).getOrThrow()
        assertTrue(RasterQaStructuralAuditReceipt.generate(
            report.copy(structuralAudit=null)
        ).isFailure)
        assertTrue(RasterQaStructuralAuditReceipt.generate(
            report.copy(counterLeakEvidence=null)
        ).isFailure)
        assertFalse(report.summary().startsWith(
            RasterQaStructuralAuditReceipt.BUILD_MARKER
        ))
    }

    @Test fun mismatchedPhotoOrIncompleteIntervalsAreRejected() {
        val report=RasterAlphaThresholdSweep.inspect(raster()).getOrThrow()
        val audit=report.structuralAudit!!
        val changed=RasterImagePixels(report.source.width,report.source.height,
            report.source.argb.copyOf(), "outra importação")
        assertTrue(RasterQaStructuralAuditReceipt.generate(
            report.copy(structuralAudit=audit.copy(source=changed))
        ).isFailure)
        assertTrue(RasterQaStructuralAuditReceipt.generate(
            report.copy(structuralAudit=audit.copy(
                intervals=audit.intervals.drop(1)
            ))
        ).isFailure)
    }
}

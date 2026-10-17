package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PesReadbackEvidenceTest {
    private fun p(x:Int,y:Int,command:StitchCommand) =
        EmbroideryPoint(x,y,command,0)

    /** Synthetic production-like PES input, NOT an image-derived matrix. */
    private fun releasedPath():EmbroideryDesign {
        val commands=listOf(
            p(5,7,StitchCommand.JUMP),
            p(15,10,StitchCommand.STITCH),
            p(55,10,StitchCommand.STITCH),
            p(55,45,StitchCommand.STITCH),
            p(25,45,StitchCommand.STITCH),
            p(25,45,StitchCommand.END)
        )
        return EmbroideryDesign(
            fileName="SAMPLE-NOT-A-MACHINE-APPROVAL.pes",
            format="PES",label="PES QA readback only",
            points=commands,bounds=EmbroideryBounds(5,55,7,45),
            stitchCount=4,jumpCount=1,colorChanges=0,
            endFound=true,sourceBytes=ByteArray(0),
            threadColors=listOf(0xff1b3c5a.toInt()),
            isModified=true,hoopProfile=HoopProfile.H130X180,
            sourceYAxisDown=true
        )
    }

    @Test fun productionPesWriterCanBeReadBackWithoutMirroringOrMissingStitches() {
        val src=releasedPath()
        // Only existing commercial MatrixConverter can produce the data.
        // This test does not expose a converter from QA image designs.
        val file=MatrixConverter.convert(
            src,"PES","readback-unit-test"
        ).getOrThrow()
        val review=PesReadbackEvidence.inspect(
            src,file.fileName,file.bytes
        )
        assertTrue(
            review.exceptionOrNull()?.message ?: "Releitura recusada sem mensagem",
            review.isSuccess
        )
        val result=review.getOrThrow()
        assertEquals(src.stitchCount,result.originalStitches)
        assertTrue(result.reopenedStitches>=src.stitchCount)
        assertTrue(result.fileBytes>=512)
        assertTrue(result.geometryPreserved)
        assertTrue(result.orderedStitchSequencePreserved)
        assertFalse(result.externalDecoderVerified)
        assertFalse(result.machineCertified)
        assertFalse(result.authorizesImagePesExport)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun productionPesWithSamePositionsInWrongOrderIsRejected() {
        val source=releasedPath()
        val altered=source.points.toMutableList()
        val a=altered[2]
        altered[2]=altered[3]
        altered[3]=a
        val reordered=source.copy(points=altered)
        val encoded=MatrixConverter.convert(
            reordered,"PES","reordered-sewing-path"
        ).getOrThrow()
        // Both pictures contain the same coordinate set: only the new
        // monotonic STITCH order gate is expected to reject this stream.
        assertEquals(
            source.points.filter { it.command==StitchCommand.STITCH }
                .map { it.xUnits to it.yUnits }.toSet(),
            reordered.points.filter { it.command==StitchCommand.STITCH }
                .map { it.xUnits to it.yUnits }.toSet()
        )
        assertTrue(PesReadbackEvidence.inspect(
            source,encoded.fileName,encoded.bytes
        ).isFailure)
    }

    @Test fun qaImageDesignCannotBePromotedIntoPesReadbackOrExport() {
        val src=releasedPath()
        val file=MatrixConverter.convert(src,"PES").getOrThrow()
        assertTrue(PesReadbackEvidence.inspect(
            src.copy(format="QA_AUTO_STITCH"),file.fileName,file.bytes
        ).isFailure)
        assertTrue(PesReadbackEvidence.inspect(
            src.copy(format="QA_SATIN_REVIEW"),file.fileName,file.bytes
        ).isFailure)
        assertTrue(PesReadbackEvidence.inspect(
            src.copy(format="QA_TATAMI_REVIEW"),file.fileName,file.bytes
        ).isFailure)
    }

    @Test fun brokenContainerIsNeverCountedAsSuccessfulReimport() {
        val src=releasedPath()
        val bytes=ByteArray(1024)
        assertTrue(PesReadbackEvidence.inspect(
            src,"broken.pes",bytes
        ).isFailure)
        val good=MatrixConverter.convert(src,"PES").getOrThrow()
        assertTrue(PesReadbackEvidence.inspect(
            src,"renamed.dst",good.bytes
        ).isFailure)
    }

    @Test fun changedOriginalGeometryCannotClaimMatchingReadback() {
        val src=releasedPath()
        val saved=MatrixConverter.convert(src,"PES").getOrThrow()
        val changed=src.copy(
            points=src.points.map {
                if(it.command==StitchCommand.STITCH &&
                    it.xUnits==55) it.copy(xUnits=90) else it
            },
            bounds=EmbroideryBounds(5,90,7,45)
        )
        assertTrue(PesReadbackEvidence.inspect(
            changed,saved.fileName,saved.bytes
        ).isFailure)
    }
}

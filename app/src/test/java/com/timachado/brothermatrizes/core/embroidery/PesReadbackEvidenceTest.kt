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
        assertTrue(result.stitchOrderPreserved)
        assertFalse(result.externalDecoderVerified)
        assertFalse(result.machineCertified)
        assertFalse(result.authorizesImagePesExport)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun sameStitchPositionsInWrongOrderAreRejected() {
        val original=releasedPath()
        val encoded=MatrixConverter.convert(
            original,"PES","ordered-source"
        ).getOrThrow()
        // The bounding box and the SET of needle positions are unchanged.
        // Previously a set-only comparison would accept this altered order.
        val shuffled=original.points.toMutableList()
        val temp=shuffled[2]
        shuffled[2]=shuffled[3]
        shuffled[3]=temp
        val forged=original.copy(points=shuffled)
        assertEquals(original.stitchCount,forged.stitchCount)
        assertEquals(original.bounds,forged.bounds)
        assertTrue(PesReadbackEvidence.inspect(
            forged,encoded.fileName,encoded.bytes
        ).isFailure)
    }

    @Test fun theReadbackFunctionNeverAcceptsImageQaEvenWhenPESHeaderIsValid() {
        val source=releasedPath()
        val encoded=MatrixConverter.convert(
            source,"PES","format-separation"
        ).getOrThrow()
        val qa=source.copy(
            format="QA_AUTO_STITCH",
            sourceBytes=ByteArray(0)
        )
        assertTrue(PesReadbackEvidence.inspect(
            qa,encoded.fileName,encoded.bytes
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
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

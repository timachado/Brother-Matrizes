package com.timachado.brothermatrizes.core.project

import android.content.Context
import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MachineFinishingInfo
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.storage.AtomicFileWriter
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

data class SavedProjectSummary(
    val id: String,
    val title: String,
    val fileName: String,
    val format: String,
    val stitchCount: Int,
    val colorCount: Int,
    val widthMm: Float,
    val heightMm: Float,
    val updatedAtMillis: Long
)

object ProjectCodec {
    private const val MAGIC =
        "BROTHER_MATRIZES_PROJECT"

    private const val VERSION =
        3

    private const val MAX_POINTS =
        2_000_000

    private const val MAX_COLORS =
        512

    fun encode(
        design: EmbroideryDesign
    ): ByteArray {
        val buffer =
            ByteArrayOutputStream()

        encodeTo(
            design =
                design,
            sink =
                buffer
        )

        return buffer
            .toByteArray()
    }

    fun encodeTo(
        design: EmbroideryDesign,
        sink: OutputStream
    ) {
        EmbroideryIntegrity
            .normalize(
                design
            )
            .getOrElse {
                    error ->
                throw IllegalArgumentException(
                    "Projeto inválido: " +
                        (
                            error.message
                                ?: "integridade não confirmada"
                            ),
                    error
                )
            }

        val output =
            DataOutputStream(
                sink
            )

        output.writeUTF(
            MAGIC
        )

        output.writeInt(
            VERSION
        )

        output.writeUTF(
            design.fileName
                .take(
                    500
                )
        )

        output.writeUTF(
            design.format
                .take(
                    32
                )
        )

        writeNullableString(
            output,
            design.label
        )

        output.writeInt(
            design.threadColors
                .size
        )

        design.threadColors
            .forEach {
                output.writeInt(
                    it
                )
            }

        writeNullableString(
            output,
            design.hoopProfile
                ?.name
        )

        writeNullableString(
            output,
            design.fabricProfile
                ?.name
        )

        val finishing =
            design.machineFinishing

        output.writeBoolean(
            finishing !=
                null
        )

        if (
            finishing !=
                null
        ) {
            output.writeBoolean(
                finishing
                    .tieInEnabled
            )

            output.writeBoolean(
                finishing
                    .tieOffEnabled
            )

            output.writeBoolean(
                finishing
                    .autoTrimLongJumps
            )

            output.writeFloat(
                finishing
                    .trimJumpThresholdMm
            )

            output.writeBoolean(
                finishing
                    .optimizeTravel
            )
        }

        output.writeBoolean(
            design.sourceYAxisDown
        )

        require(
            design.points
                .size <=
                MAX_POINTS
        ) {
            "Projeto grande demais para salvar."
        }

        output.writeInt(
            design.points
                .size
        )

        design.points
            .forEach {
                    point ->
                output.writeInt(
                    point.xUnits
                )

                output.writeInt(
                    point.yUnits
                )

                output.writeUTF(
                    point.command
                        .name
                )

                output.writeInt(
                    point.colorIndex
                )
            }

        require(
            design.guidePoints
                .size <=
                MAX_POINTS
        ) {
            "Guia vetorial grande demais para salvar."
        }

        output.writeInt(
            design.guidePoints
                .size
        )

        design.guidePoints
            .forEach {
                    point ->
                output.writeInt(
                    point.xUnits
                )

                output.writeInt(
                    point.yUnits
                )

                output.writeUTF(
                    point.command
                        .name
                )

                output.writeInt(
                    point.colorIndex
                )
            }

        output.flush()
    }

    fun decode(
        bytes: ByteArray
    ): EmbroideryDesign =
        ByteArrayInputStream(
            bytes
        ).use {
            decode(
                it
            )
        }

    fun decode(
        source: InputStream
    ): EmbroideryDesign {
        return DataInputStream(
            if (
                source is
                    BufferedInputStream
            ) {
                source
            } else {
                BufferedInputStream(
                    source,
                    64 * 1024
                )
            }
        ).use {
                input ->
            val magic =
                input.readUTF()

            require(
                magic ==
                    MAGIC ||
                    magic ==
                    LEGACY_MAGIC
            ) {
                "Arquivo de projeto inválido."
            }

            val version =
                input.readInt()

            require(
                version in
                    1..VERSION
            ) {
                "Versão de projeto ainda não suportada."
            }

            val fileName =
                input.readUTF()

            val format =
                input.readUTF()
                    .uppercase(
                        Locale.ROOT
                    )

            val label =
                readNullableString(
                    input
                )

            val colorCount =
                input.readInt()

            require(
                colorCount in
                    0..MAX_COLORS
            ) {
                "Paleta inválida."
            }

            val colors =
                ArrayList<Int>(
                    colorCount
                ).apply {
                    repeat(
                        colorCount
                    ) {
                        add(
                            input.readInt()
                        )
                    }
                }

            val hoop =
                readNullableString(
                    input
                )?.let {
                        name ->
                    runCatching {
                        HoopProfile
                            .valueOf(
                                name
                            )
                    }.getOrNull()
                }

            val fabric =
                readNullableString(
                    input
                )?.let {
                        name ->
                    runCatching {
                        FabricProfile
                            .valueOf(
                                name
                            )
                    }.getOrNull()
                }

            val finishing =
                if (
                    input.readBoolean()
                ) {
                    MachineFinishingInfo(
                        tieInEnabled =
                            input.readBoolean(),
                        tieOffEnabled =
                            input.readBoolean(),
                        autoTrimLongJumps =
                            input.readBoolean(),
                        trimJumpThresholdMm =
                            input.readFloat(),
                        optimizeTravel =
                            input.readBoolean()
                    )
                } else {
                    null
                }

            val sourceYAxisDown =
                if (
                    version >=
                        3
                ) {
                    input.readBoolean()
                } else {
                    false
                }

            val pointCount =
                input.readInt()

            require(
                pointCount in
                    1..MAX_POINTS
            ) {
                "Quantidade de pontos inválida."
            }

            val points =
                ArrayList<EmbroideryPoint>(
                    pointCount
                )

            repeat(
                pointCount
            ) {
                points +=
                    EmbroideryPoint(
                        xUnits =
                            input.readInt(),
                        yUnits =
                            input.readInt(),
                        command =
                            StitchCommand
                                .valueOf(
                                    input.readUTF()
                                ),
                        colorIndex =
                            input.readInt()
                    )
            }

            val guidePoints =
                if (
                    version >=
                        2
                ) {
                    val guideCount =
                        input.readInt()

                    require(
                        guideCount in
                            0..MAX_POINTS
                    ) {
                        "Quantidade de pontos do guia inválida."
                    }

                    ArrayList<EmbroideryPoint>(
                        guideCount
                    ).apply {
                        repeat(
                            guideCount
                        ) {
                            add(
                                EmbroideryPoint(
                                    xUnits =
                                        input.readInt(),
                                    yUnits =
                                        input.readInt(),
                                    command =
                                        StitchCommand
                                            .valueOf(
                                                input.readUTF()
                                            ),
                                    colorIndex =
                                        input.readInt()
                                )
                            )
                        }
                    }
                } else {
                    emptyList()
                }

            EmbroideryIntegrity
                .normalize(
                    EmbroideryDesign(
                        fileName =
                            fileName,
                        format =
                            format,
                        label =
                            label,
                        points =
                            points,
                        bounds =
                            EmbroideryBounds(
                                0,
                                0,
                                0,
                                0
                            ),
                        stitchCount =
                            0,
                        jumpCount =
                            0,
                        colorChanges =
                            0,
                        endFound =
                            false,
                        sourceBytes =
                            ByteArray(0),
                        guidePoints =
                            guidePoints,
                        threadColors =
                            colors,
                        sourceYAxisDown =
                            sourceYAxisDown,
                        isModified =
                            true,
                        hoopProfile =
                            hoop,
                        fabricProfile =
                            fabric,
                        machineFinishing =
                            finishing
                    )
                )
                .getOrElse {
                        error ->
                    throw IllegalArgumentException(
                        "Projeto corrompido: " +
                            (
                                error.message
                                    ?: "integridade não confirmada"
                                ),
                        error
                    )
                }
                .design
        }
    }

    private fun writeNullableString(
        output: DataOutputStream,
        value: String?
    ) {
        output.writeBoolean(
            value !=
                null
        )

        if (
            value !=
                null
        ) {
            output.writeUTF(
                value.take(
                    2000
                )
            )
        }
    }

    private fun readNullableString(
        input: DataInputStream
    ): String? =
        if (
            input.readBoolean()
        ) {
            input.readUTF()
        } else {
            null
        }
}

object ProjectStore {
    private const val EXTENSION =
        ".flp"

    fun save(
        context: Context,
        design: EmbroideryDesign
    ): Result<SavedProjectSummary> =
        runCatching {
            val directory =
                projectDirectory(
                    context
                )

            if (
                !directory.exists()
            ) {
                check(
                    directory.mkdirs()
                ) {
                    "Não foi possível criar a biblioteca local."
                }
            }

            val id =
                projectId(
                    design
                )

            val file =
                File(
                    directory,
                    id +
                        EXTENSION
                )

            AtomicFileWriter.write(
                target =
                    file
            ) {
                    output ->
                ProjectCodec.encodeTo(
                    design =
                        design,
                    sink =
                        output
                )
            }

            file.setLastModified(
                System.currentTimeMillis()
            )

            summary(
                id =
                    id,
                design =
                    design,
                updatedAtMillis =
                    file.lastModified()
            )
        }

    fun list(
        context: Context
    ): Result<List<SavedProjectSummary>> =
        runCatching {
            val directory =
                projectDirectory(
                    context
                )

            if (
                !directory.exists()
            ) {
                return@runCatching emptyList()
            }

            directory
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile &&
                        it.extension
                            .equals(
                                "flp",
                                ignoreCase =
                                    true
                            )
                }
                .mapNotNull {
                        file ->
                    runCatching {
                        val design =
                            file.inputStream()
                                .buffered(
                                    64 * 1024
                                )
                                .use {
                                    ProjectCodec
                                        .decode(
                                            it
                                        )
                                }

                        summary(
                            id =
                                file.nameWithoutExtension,
                            design =
                                design,
                            updatedAtMillis =
                                file.lastModified()
                        )
                    }.getOrNull()
                }
                .sortedByDescending {
                    it.updatedAtMillis
                }
        }

    fun load(
        context: Context,
        id: String
    ): Result<EmbroideryDesign> =
        runCatching {
            require(
                id.matches(
                    Regex(
                        "[a-f0-9]{24}"
                    )
                )
            ) {
                "Projeto inválido."
            }

            val file =
                File(
                    projectDirectory(
                        context
                    ),
                    id +
                        EXTENSION
                )

            require(
                file.exists()
            ) {
                "Projeto não encontrado."
            }

            file.inputStream()
                .buffered(
                    64 * 1024
                )
                .use {
                    ProjectCodec.decode(
                        it
                    )
                }
        }

    fun delete(
        context: Context,
        id: String
    ): Result<Unit> =
        runCatching {
            require(
                id.matches(
                    Regex(
                        "[a-f0-9]{24}"
                    )
                )
            ) {
                "Projeto inválido."
            }

            val file =
                File(
                    projectDirectory(
                        context
                    ),
                    id +
                        EXTENSION
                )

            if (
                file.exists()
            ) {
                check(
                    file.delete()
                ) {
                    "Não foi possível excluir o projeto."
                }
            }
        }

    private fun projectDirectory(
        context: Context
    ): File =
        File(
            context.filesDir,
            "brother_matrizes-projects"
        )

    private fun projectId(
        design: EmbroideryDesign
    ): String {
        val key =
            design.fileName
                .lowercase(
                    Locale.ROOT
                ) +
                "|" +
                (
                    design.label
                        ?: ""
                    )
                    .trim()
                    .lowercase(
                        Locale.ROOT
                    )

        return MessageDigest
            .getInstance(
                "SHA-256"
            )
            .digest(
                key.toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString(
                separator = ""
            ) {
                "%02x".format(
                    it
                )
            }
            .take(
                24
            )
    }

    private fun summary(
        id: String,
        design: EmbroideryDesign,
        updatedAtMillis: Long
    ): SavedProjectSummary =
        SavedProjectSummary(
            id = id,
            title =
                design.label
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: design.fileName
                        .substringBeforeLast(
                            '.'
                        ),
            fileName =
                design.fileName,
            format =
                design.format,
            stitchCount =
                design.stitchCount,
            colorCount =
                design.colorCount,
            widthMm =
                design.bounds
                    .widthMm,
            heightMm =
                design.bounds
                    .heightMm,
            updatedAtMillis =
                updatedAtMillis
        )
}

package com.timachado.brothermatrizes.core.project

import android.content.Context
import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MachineFinishingInfo
import com.timachado.brothermatrizes.core.embroidery.NamePersonalizationLayer
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
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
        9

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

        /*
         * v4: camada editável reconstituível sem gravar um segundo conjunto
         * completo de pontos. A composição preserva todo o prefixo da matriz
         * original; salvamos apenas seu END, metadados e opções tipográficas.
         * Projetos legados v1..v3 continuam legíveis.
         */
        val layer = design.editableName
        output.writeBoolean(layer != null)
        if (layer != null) {
            val base = layer.original
            require(base.editableName == null) {
                "Camadas de nome aninhadas não são suportadas."
            }
            require(base.points.size >= 2 && base.points.last().command == StitchCommand.END) {
                "Matriz-base de nome inválida."
            }
            require(
                design.points.size > base.points.size &&
                    design.points.subList(0, base.points.size - 1) ==
                    base.points.dropLast(1)
            ) {
                "O desenho de base mudou. Salve uma nova matriz sem camada editável."
            }
            require(base.threadColors.size in 0..MAX_COLORS) {
                "A paleta original é grande demais."
            }
            output.writeInt(base.points.size)
            val originalEnd = base.points.last()
            output.writeInt(originalEnd.xUnits)
            output.writeInt(originalEnd.yUnits)
            output.writeInt(originalEnd.colorIndex)
            output.writeUTF(base.fileName.take(500))
            writeNullableString(output, base.label)
            output.writeInt(base.threadColors.size)
            base.threadColors.forEach(output::writeInt)
            writeNullableString(output, base.hoopProfile?.name)
            writeNullableString(output, base.fabricProfile?.name)
            val fontOptions = layer.options
            output.writeUTF(fontOptions.text.take(24))
            output.writeUTF(fontOptions.font.name)
            output.writeFloat(fontOptions.heightMm)
            output.writeFloat(fontOptions.spacingMm)
            output.writeFloat(fontOptions.satinDensityMm)
            output.writeInt(fontOptions.color)
            output.writeInt(layer.centerXUnits)
            output.writeInt(layer.centerYUnits)
            // v5: rotação da camada de texto, sem alterar a matriz-base.
            output.writeFloat(fontOptions.rotationDegrees)
            // v6: preservar fonte da Biblioteca de Fontes por ID.
            writeNullableString(output, fontOptions.importedFontId)
            // v7: preserva independentemente a largura e a altura da pinça.
            output.writeFloat(fontOptions.widthScale)
            output.writeFloat(fontOptions.heightScale)
            // v8: expansão horizontal dos glifos, independente da palavra.
            output.writeFloat(fontOptions.horizontalGlyphScale)
        }
        // v9: a posição é física dentro do bastidor, não apenas o
        // deslocamento temporário da câmera. Persiste inclusive sem nome.
        output.writeBoolean(design.hoopPositionLocked)
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
                    MAGIC
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

            val editableLayer = if (version >= 4 && input.readBoolean()) {
                val baseCount = input.readInt()
                require(baseCount in 2..points.size) {
                    "Prefixo de nome inválido."
                }
                val endPoint = EmbroideryPoint(
                    xUnits = input.readInt(),
                    yUnits = input.readInt(),
                    command = StitchCommand.END,
                    colorIndex = input.readInt()
                )
                val originalFileName = input.readUTF()
                val originalLabel = readNullableString(input)
                val originalColorCount = input.readInt()
                require(originalColorCount in 0..MAX_COLORS) {
                    "Paleta de nome inválida."
                }
                val originalColors = List(originalColorCount) { input.readInt() }
                val originalHoop = readNullableString(input)?.let {
                    runCatching { HoopProfile.valueOf(it) }.getOrNull()
                }
                val originalFabric = readNullableString(input)?.let {
                    runCatching { FabricProfile.valueOf(it) }.getOrNull()
                }
                val name = input.readUTF()
                require(name.length <= 24) { "Nome editável grande demais." }
                val font = EmbroideryFontPreset.valueOf(input.readUTF())
                val height = input.readFloat()
                val spacing = input.readFloat()
                val density = input.readFloat()
                val nameColor = input.readInt()
                val posX = input.readInt()
                val posY = input.readInt()
                val rotation = if (version >= 5) input.readFloat() else 0f
                val importedFontId = if (version >= 6) readNullableString(input) else null
                val widthScale = if (version >= 7) input.readFloat() else 1f
                val heightScale = if (version >= 7) input.readFloat() else 1f
                val horizontalGlyphScale =
                    if (version >= 8) input.readFloat() else 1f
                require(importedFontId == null ||
                    (importedFontId.length <= 160 &&
                        (importedFontId.endsWith(".ttf", true) ||
                            importedFontId.endsWith(".otf", true)))) {
                    "Identificador de fonte importada inválido."
                }
                require(
                    height.isFinite() && height in 4f..60f &&
                        spacing.isFinite() && spacing in 0f..8f &&
                        density.isFinite() && density in .3f..1.2f &&
                        rotation.isFinite() && rotation in -180f..180f &&
                        widthScale.isFinite() && widthScale in .5f..2.5f &&
                        heightScale.isFinite() && heightScale in .5f..2.5f &&
                        horizontalGlyphScale.isFinite() &&
                        horizontalGlyphScale in .7f..1.4f
                ) { "Parâmetros de nome corrompidos." }
                val originalDesign = EmbroideryIntegrity.normalize(
                    EmbroideryDesign(
                        fileName = originalFileName,
                        format = format,
                        label = originalLabel,
                        points = points.take(baseCount - 1) + endPoint,
                        bounds = EmbroideryBounds(0, 0, 0, 0),
                        stitchCount = 0,
                        jumpCount = 0,
                        colorChanges = 0,
                        endFound = true,
                        sourceBytes = ByteArray(0),
                        guidePoints = guidePoints,
                        threadColors = originalColors,
                        sourceYAxisDown = sourceYAxisDown,
                        isModified = true,
                        hoopProfile = originalHoop,
                        fabricProfile = originalFabric,
                        machineFinishing = finishing
                    )
                ).getOrThrow().design
                NamePersonalizationLayer(
                    original = originalDesign,
                    options = TextMatrixOptions(
                        text = name,
                        heightMm = height,
                        spacingMm = spacing,
                        satinDensityMm = density,
                        rotationDegrees = rotation,
                        widthScale = widthScale,
                        heightScale = heightScale,
                        horizontalGlyphScale = horizontalGlyphScale,
                        importedFontId = importedFontId,
                        color = nameColor,
                        font = font,
                        style = TextStitchStyle.SATIN,
                        outputFormat = format
                    ),
                    centerXUnits = posX,
                    centerYUnits = posY
                )
            } else {
                null
            }

            val lockedPlacement = if (version >= 9) input.readBoolean() else false
            val storedLayer = editableLayer?.let { layer ->
                layer.copy(original = layer.original.copy(
                    hoopPositionLocked = lockedPlacement
                ))
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
                            finishing,
                        editableName = storedLayer,
                        hoopPositionLocked = lockedPlacement
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

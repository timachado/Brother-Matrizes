package com.timachado.brothermatrizes.core.project

import android.content.Context
import com.timachado.brothermatrizes.core.embroidery.ColorLayerEdit
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.ManualSatinSection
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.MultiColorRasterOptions
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterLayerStitchMode
import com.timachado.brothermatrizes.core.embroidery.SatinRailPair
import com.timachado.brothermatrizes.core.storage.AtomicFileWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32

/**
 * Persistência exclusiva dos projetos FONTES-ARTE editáveis da etapa 7.
 * NÃO altera o ProjectCodec (.flp) antigo nem substitui a biblioteca de PES.
 * Cada documento contém pixels ARGB originais, opções e TODAS as seções Satin.
 */
data class RasterEditProject(
    val title: String,
    val image: RasterImagePixels,
    val widthMm: Float,
    val maxColors: Int,
    val outputFormat: String,
    val hoopProfile: HoopProfile?,
    val layers: List<ColorLayerEdit>
)

data class RasterEditProjectSummary(
    val id: String,
    val title: String,
    val updatedAtMillis: Long
)

object RasterEditProjectCodec {
    private const val MAGIC = 0x42524D52 // BRMR
    private const val VERSION = 2
    const val MAX_BYTES = 4_000_000
    private const val MAX_SECTIONS = 80
    private const val MAX_RAILS = 100

    fun validate(project: RasterEditProject) {
        require(project.title.isNotBlank() && project.title.length <= 120) {
            "Use um nome de projeto de até 120 caracteres."
        }
        val image = project.image
        require(image.width in 8..512 && image.height in 8..512 &&
            image.argb.size == image.width * image.height &&
            image.label.length <= 250) {
            "Imagem inválida ou grande demais para salvar."
        }
        require(project.widthMm.isFinite() && project.widthMm in 10f..200f &&
            project.maxColors in 2..4 &&
            project.outputFormat.uppercase(Locale.ROOT) in MatrixConverter.supportedFormats) {
            "Configuração da matriz inválida."
        }
        require(project.layers.size <= project.maxColors &&
            project.layers.map { it.sourceIndex }.distinct().size == project.layers.size &&
            project.layers.all { it.sourceIndex in 0 until project.maxColors }) {
            "Camadas duplicadas ou inválidas."
        }
        for (layer in project.layers) {
            require(layer.threadColor == null || layer.threadColor in 0..0xFFFFFF) {
                "Cor de linha inválida."
            }
            require(layer.rowSpacingMm == null ||
                (layer.rowSpacingMm.isFinite() && layer.rowSpacingMm in 0.4f..1f)) {
                "Densidade da camada inválida."
            }
            require(layer.satinPullCompensationMm.isFinite() &&
                layer.satinPullCompensationMm in 0f..0.3f) {
                "Compensação Satin inválida."
            }
            require(layer.manualSections.size <= MAX_SECTIONS) {
                "Número máximo de seções ultrapassado."
            }
            for ((sectionIndex, section) in layer.manualSections.withIndex()) {
                require(!section.connectToPrevious || sectionIndex > 0) {
                    "A primeira seção não pode ativar conexão."
                }
                require(section.rails.size in 2..MAX_RAILS) {
                    "Cada seção deve ter de 2 a 100 pares de trilhos."
                }
                require(section.rails.all { r ->
                    listOf(r.ax, r.ay, r.bx, r.by).all {
                        it.isFinite() && it in 0f..1f
                    }
                }) {
                    "Há um ponto Satin fora da imagem."
                }
            }
        }
    }

    fun encode(project: RasterEditProject): ByteArray {
        validate(project)
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(project.title)
            out.writeUTF(project.image.label)
            out.writeInt(project.image.width)
            out.writeInt(project.image.height)
            for (pixel in project.image.argb) out.writeInt(pixel)
            out.writeFloat(project.widthMm)
            out.writeInt(project.maxColors)
            out.writeUTF(project.outputFormat.uppercase(Locale.ROOT))
            out.writeUTF(project.hoopProfile?.name ?: "")
            out.writeInt(project.layers.size)
            for (layer in project.layers) {
                out.writeInt(layer.sourceIndex)
                out.writeBoolean(layer.enabled)
                out.writeBoolean(layer.threadColor != null)
                if (layer.threadColor != null) out.writeInt(layer.threadColor)
                out.writeBoolean(layer.rowSpacingMm != null)
                if (layer.rowSpacingMm != null) out.writeFloat(layer.rowSpacingMm)
                out.writeUTF(layer.stitchMode.name)
                out.writeFloat(layer.satinPullCompensationMm)
                out.writeBoolean(layer.satinCenterUnderlay)
                out.writeInt(layer.manualSections.size)
                for (section in layer.manualSections) {
                    out.writeInt(section.rails.size)
                    out.writeBoolean(section.connectToPrevious)
                    for (r in section.rails) {
                        out.writeFloat(r.ax)
                        out.writeFloat(r.ay)
                        out.writeFloat(r.bx)
                        out.writeFloat(r.by)
                    }
                }
            }
        }
        val payload = raw.toByteArray()
        require(payload.size + 8 <= MAX_BYTES) {
            "Projeto muito grande para armazenamento local."
        }
        val crc = CRC32().apply { update(payload) }.value
        return ByteArrayOutputStream().also { stream ->
            stream.write(payload)
            DataOutputStream(stream).use { out ->
                out.writeLong(crc)
            }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): RasterEditProject {
        require(bytes.size in 28..MAX_BYTES) {
            "Arquivo inválido ou acima do limite permitido."
        }
        val payloadSize = bytes.size - 8
        val expected = DataInputStream(
            ByteArrayInputStream(bytes, payloadSize, 8)
        ).readLong()
        val actual = CRC32().apply { update(bytes, 0, payloadSize) }.value
        require(expected == actual) {
            "Projeto corrompido: integridade não confirmada."
        }
        return DataInputStream(ByteArrayInputStream(bytes, 0, payloadSize)).use { input ->
            require(input.readInt() == MAGIC) {
                "Arquivo não é um projeto Satin editável compatível."
            }
            val version = input.readInt()
            require(version in 1..VERSION) {
                "Versão de projeto Satin ainda não suportada."
            }
            val title = input.readUTF()
            val label = input.readUTF()
            val w = input.readInt()
            val h = input.readInt()
            require(w in 8..512 && h in 8..512) { "Dimensões inválidas." }
            val argb = IntArray(w * h) { input.readInt() }
            val widthMm = input.readFloat()
            val maxColors = input.readInt()
            val format = input.readUTF()
            val hoopName = input.readUTF()
            val hoop = if (hoopName.isEmpty()) null else
                runCatching { HoopProfile.valueOf(hoopName) }.getOrElse {
                    throw IllegalArgumentException("Bastidor não suportado.")
                }
            val count = input.readInt()
            require(count in 0..4) { "Quantidade de camadas inválida." }
            val layers = (0 until count).map {
                val sourceIndex = input.readInt()
                val enabled = input.readBoolean()
                val thread = if (input.readBoolean()) input.readInt() else null
                val spacing = if (input.readBoolean()) input.readFloat() else null
                val mode = RasterLayerStitchMode.valueOf(input.readUTF())
                val pull = input.readFloat()
                val underlay = input.readBoolean()
                val sectionCount = input.readInt()
                require(sectionCount in 0..MAX_SECTIONS) { "Número de seções inválido." }
                val sections = (0 until sectionCount).map {
                    val pairCount = input.readInt()
                    require(pairCount in 2..MAX_RAILS) { "Quantidade de pares inválida." }
                    val joinPrevious = if (version >= 2) input.readBoolean() else false
                    ManualSatinSection(
                        rails = (0 until pairCount).map {
                            SatinRailPair(
                                input.readFloat(), input.readFloat(),
                                input.readFloat(), input.readFloat()
                            )
                        },
                        connectToPrevious = joinPrevious
                    )
                }
                ColorLayerEdit(
                    sourceIndex = sourceIndex,
                    enabled = enabled,
                    threadColor = thread,
                    rowSpacingMm = spacing,
                    stitchMode = mode,
                    satinPullCompensationMm = pull,
                    satinCenterUnderlay = underlay,
                    manualSections = sections
                )
            }
            require(input.available() == 0) { "Dados excedentes no projeto." }
            RasterEditProject(
                title = title,
                image = RasterImagePixels(w, h, argb, label),
                widthMm = widthMm,
                maxColors = maxColors,
                outputFormat = format,
                hoopProfile = hoop,
                layers = layers
            ).also { validate(it) }
        }
    }

    fun readTitle(file: File): String =
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == MAGIC &&
                input.readInt() in 1..VERSION) {
                "Projeto incompatível."
            }
            input.readUTF().also {
                require(it.isNotBlank() && it.length <= 120)
            }
        }
}

/** Armazena projetos próprios em pasta separada da biblioteca de matrizes. */
object RasterEditFileStore {
    private const val EXTENSION = ".bmrail"
    private const val MAX_PROJECTS = 100

    fun save(folder: File, project: RasterEditProject, existingId: String? = null):
        Result<String> = runCatching {
        require(folder.exists() || folder.mkdirs()) {
            "Não foi possível criar a pasta dos projetos."
        }
        val existing = folder.listFiles()?.count {
            it.isFile && it.name.endsWith(EXTENSION)
        } ?: 0
        if (existingId == null) require(existing < MAX_PROJECTS) {
            "Limite de 100 projetos editáveis atingido."
        }
        val id = existingId ?: UUID.randomUUID().toString()
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Identificador inválido." }
        if (existingId != null) require(File(folder, id + EXTENSION).isFile) {
            "O projeto original não existe; salve como novo."
        }
        val data = RasterEditProjectCodec.encode(project)
        AtomicFileWriter.write(File(folder, id + EXTENSION), data)
        id
    }

    fun load(folder: File, id: String): Result<RasterEditProject> = runCatching {
        require(id.matches(Regex("[a-f0-9-]{36}"))) {
            "Identificador do projeto inválido."
        }
        val file = File(folder, id + EXTENSION)
        require(file.isFile && file.length() in 28..RasterEditProjectCodec.MAX_BYTES.toLong()) {
            "Projeto não encontrado ou inválido."
        }
        RasterEditProjectCodec.decode(file.readBytes())
    }

    fun list(folder: File): Result<List<RasterEditProjectSummary>> = runCatching {
        (folder.listFiles()?.filter {
            it.isFile && it.name.endsWith(EXTENSION) &&
                it.length() in 28..RasterEditProjectCodec.MAX_BYTES.toLong()
        } ?: emptyList()).sortedByDescending { it.lastModified() }
            .take(MAX_PROJECTS)
            .mapNotNull { file ->
                runCatching {
                    RasterEditProjectSummary(
                        id = file.name.removeSuffix(EXTENSION),
                        title = RasterEditProjectCodec.readTitle(file),
                        updatedAtMillis = file.lastModified()
                    )
                }.getOrNull()
            }
    }
}

object RasterEditProjectStore {
    private fun folder(context: Context) =
        File(context.filesDir, "brother_matrizes_editable_satin_v1")

    fun save(context: Context, project: RasterEditProject, existingId: String? = null) =
        RasterEditFileStore.save(folder(context), project, existingId)

    fun load(context: Context, id: String) =
        RasterEditFileStore.load(folder(context), id)

    fun list(context: Context) =
        RasterEditFileStore.list(folder(context))
}

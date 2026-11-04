package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Acompanhamento OPCIONAL e exclusivo da QA Windows para projetos com
 * camadas de letras. O FLP continua sendo gravado e aberto pelo ProjectCodec
 * v11 Android, sem qualquer byte proprietário acrescido ao arquivo.
 *
 * O .bmlayers v1 registra parâmetros mínimos, mais o SHA-256 dos bytes do
 * FLP gerado. Ao reabrir, a composição é regenerada e comparada pontada por
 * pontada ao FLP, antes que qualquer objeto seja tornado editável.
 *
 * Um projeto FLP isolado permanece legível, mas perde os controles por letra.
 * O hash é para associação/integridade, NÃO é assinatura/autenticação.
 */
internal data class DesktopLayerProject(
    val document: DesktopMatrixDocument,
    val session: DesktopLetterSession
)

internal object DesktopLetterProjectBundle {
    const val COMPANION_EXTENSION = ".bmlayers"
    private const val MAGIC = "BMLAYERS"
    private const val VERSION = 1
    private const val MAX_COMPANION_BYTES = 4096L
    private const val SHA256_LENGTH = 32
    private val noFollow = arrayOf(LinkOption.NOFOLLOW_LINKS)

    fun companion(flp: Path): Path {
        val fileName = flp.fileName.toString()
        val stem = if (fileName.endsWith(".flp", ignoreCase = true)) {
            fileName.dropLast(4)
        } else {
            fileName
        }
        return flp.resolveSibling(stem + COMPANION_EXTENSION)
    }

    fun saveNew(
        layout: DesktopLetterLayout,
        document: DesktopMatrixDocument,
        flp: Path
    ): Pair<Path, Path> {
        requireFlpDestination(flp)
        val sidecar = companion(flp)
        require(!Files.exists(flp, LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
            "O FLP ou o arquivo de camadas já existe. Escolha um nome novo."
        }

        val expected = DesktopLetterComposer.render(layout).current
        require(sameDesign(expected, document.current)) {
            "Esta matriz recebeu outras alterações e não corresponde às camadas. " +
                "Salve-a apenas como FLP comum."
        }
        // Prepara e valida TUDO antes de criar arquivos em disco.
        val flpBytes = DesktopProjectService.encode(document)
        val decoded = DesktopProjectService.decode(flpBytes)
        require(sameDesign(expected, decoded)) {
            "O FLP não preservou a composição de camadas."
        }
        val sidecarBytes = encode(layout, sha256(flpBytes))
        require(sidecarBytes.size.toLong() <= MAX_COMPANION_BYTES) {
            "O arquivo de camadas excedeu o limite permitido."
        }

        var madeFlp = false
        var madeSidecar = false
        try {
            Files.newOutputStream(
                flp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use { stream ->
                madeFlp = true
                stream.write(flpBytes)
            }
            Files.newOutputStream(
                sidecar, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use { stream ->
                madeSidecar = true
                stream.write(sidecarBytes)
            }
            return flp to sidecar
        } catch (failure: Exception) {
            // Apaga somente arquivos que esta tentativa criou.
            if (madeSidecar) runCatching { Files.deleteIfExists(sidecar) }
            if (madeFlp) runCatching { Files.deleteIfExists(flp) }
            throw failure
        }
    }

    fun open(flp: Path): DesktopLayerProject {
        requireFlpSource(flp)
        val sidecar = companion(flp)
        require(Files.isRegularFile(sidecar, *noFollow) &&
            !Files.isSymbolicLink(sidecar) &&
            Files.size(sidecar) in 1L..MAX_COMPANION_BYTES) {
            "Arquivo de camadas não encontrado ou fora do limite de QA."
        }
        val flpBytes = Files.readAllBytes(flp)
        val (layout, expectedHash) = decode(Files.readAllBytes(sidecar))
        require(sha256(flpBytes).contentEquals(expectedHash)) {
            "As camadas não pertencem a este FLP: SHA-256 divergente."
        }
        // Decodifica exatamente os bytes conferidos pelo SHA-256, sem reabrir
        // o caminho: outra gravação no intervalo não pode trocar o FLP validado.
        val loadedDesign = DesktopProjectService.decode(flpBytes)
        val loaded = DesktopMatrixDocument(
            original = loadedDesign, current = loadedDesign
        )
        val expected = DesktopLetterComposer.render(layout)
        require(sameDesign(expected.current, loaded.current)) {
            "As pontadas do FLP não correspondem às camadas editáveis."
        }
        return DesktopLayerProject(loaded, DesktopLetterSession.start(layout))
    }

    private fun encode(layout: DesktopLetterLayout, flpHash: ByteArray): ByteArray {
        require(flpHash.size == SHA256_LENGTH) { "Hash do FLP inválido." }
        val valid = DesktopLetterComposer.start(layout.text, layout.heightMm, layout.style)
        require(valid.objects.size == layout.objects.size) { "Objetos inconsistentes." }
        layout.objects.forEachIndexed { index, objectData ->
            require(objectData.character == valid.objects[index].character) {
                "Caracteres não correspondem à composição."
            }
            if (objectData.character != ' ') {
                DesktopLetterComposer.update(
                    layout, index,
                    objectData.moveXmm, objectData.moveYmm,
                    objectData.scale, objectData.rotationDegrees
                )
            } else {
                require(objectData == valid.objects[index]) {
                    "Espaços não podem conter transformações."
                }
            }
        }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.write(MAGIC.toByteArray(Charsets.US_ASCII))
            stream.writeInt(VERSION)
            stream.write(flpHash)
            stream.writeUTF(layout.text)
            stream.writeFloat(layout.heightMm)
            stream.writeUTF(layout.style.name)
            stream.writeInt(layout.objects.size)
            layout.objects.forEach { item ->
                stream.writeChar(item.character.code)
                stream.writeFloat(item.moveXmm)
                stream.writeFloat(item.moveYmm)
                stream.writeFloat(item.scale)
                stream.writeFloat(item.rotationDegrees)
            }
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): Pair<DesktopLetterLayout, ByteArray> {
        require(bytes.isNotEmpty() && bytes.size.toLong() <= MAX_COMPANION_BYTES) {
            "Arquivo de camadas vazio ou muito grande."
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = ByteArray(MAGIC.length)
            input.readFully(magic)
            require(String(magic, Charsets.US_ASCII) == MAGIC) {
                "Cabeçalho de camadas não reconhecido."
            }
            require(input.readInt() == VERSION) {
                "Versão de camadas não suportada."
            }
            val checksum = ByteArray(SHA256_LENGTH)
            input.readFully(checksum)
            val text = input.readUTF()
            val height = input.readFloat()
            val styleCode = input.readUTF()
            val style = TextStitchStyle.entries.singleOrNull { it.name == styleCode }
                ?: throw IllegalArgumentException("Estilo de letras não reconhecido.")
            val count = input.readInt()
            val base = DesktopLetterComposer.start(text, height, style)
            require(count == base.objects.size) { "Quantidade de objetos inválida." }
            var layout = base
            repeat(count) { index ->
                val character = input.readChar()
                val dx = input.readFloat()
                val dy = input.readFloat()
                val scale = input.readFloat()
                val rotation = input.readFloat()
                require(character == base.objects[index].character) {
                    "Identidade de letra divergente."
                }
                if (character == ' ') {
                    require(dx == 0f && dy == 0f && scale == 1f && rotation == 0f) {
                        "Espaço contém parâmetros inválidos."
                    }
                } else {
                    layout = DesktopLetterComposer.update(
                        layout, index, dx, dy, scale, rotation
                    )
                }
            }
            require(input.available() == 0) { "Dados adicionais inesperados no arquivo." }
            layout to checksum
        }
    }

    private fun requireFlpDestination(flp: Path) {
        require(flp.isAbsolute && flp.fileName.toString().endsWith(".flp", ignoreCase = true)) {
            "Escolha um caminho absoluto terminado em .flp."
        }
        require(Files.isDirectory(flp.parent, *noFollow) &&
            !Files.isSymbolicLink(flp.parent)) {
            "Pasta de destino inválida ou redirecionada."
        }
        require(Files.isWritable(flp.parent)) { "Pasta de destino sem permissão de gravação." }
    }

    private fun requireFlpSource(flp: Path) {
        require(flp.isAbsolute && flp.fileName.toString().endsWith(".flp", ignoreCase = true)) {
            "Selecione um projeto FLP com caminho absoluto."
        }
        require(Files.isDirectory(flp.parent, *noFollow) &&
            !Files.isSymbolicLink(flp.parent)) {
            "Pasta de origem inválida ou redirecionada."
        }
        require(Files.isRegularFile(flp, *noFollow) && !Files.isSymbolicLink(flp)) {
            "Arquivo FLP de origem ausente ou inválido."
        }
        require(Files.size(flp) in 1L..DesktopProjectService.MAX_PROJECT_BYTES) {
            "FLP acima do limite de tamanho permitido."
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun sameDesign(a: com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign,
                           b: com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign): Boolean =
        a.points == b.points && a.threadColors == b.threadColors &&
            a.bounds == b.bounds && a.stitchCount == b.stitchCount &&
            a.jumpCount == b.jumpCount && a.colorChanges == b.colorChanges &&
            a.endFound == b.endFound
}

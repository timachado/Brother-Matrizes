package com.timachado.brothermatrizes.desktop.platform

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import javax.swing.JFileChooser
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Native Windows adapter for the official Android CreateDrawingScreen SVG
 * import action. It only reads a local SVG chosen explicitly by the user.
 * This adapter never writes, uploads or opens a network connection.
 */
internal object DesktopSvgFilePicker {
    private const val MAX_BYTES = 2_000_000L

    data class Selected(val name: String, val bytes: ByteArray)

    fun chooseFromDialog(): Result<Selected>? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Selecionar desenho vetorial SVG — Brother Matrizes"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Desenhos SVG (*.svg)", "svg")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
        return runCatching { readVerified(chooser.selectedFile.toPath()) }
    }

    /** Separate pure-file adapter allows QA without Swing or user documents. */
    fun readVerified(candidate: Path): Selected {
        val file = candidate.toAbsolutePath().normalize()
        require(file.fileName.toString().lowercase(Locale.ROOT).endsWith(".svg")) {
            "Selecione um arquivo com extensão .svg."
        }
        require(!Files.isSymbolicLink(file) &&
            !Files.isSymbolicLink(file.parent) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "O SVG precisa ser um arquivo local regular, não um link simbólico."
        }
        require(Files.size(file) in 1..MAX_BYTES) {
            "O SVG deve ter entre 1 byte e 2 MB."
        }
        val bytes = Files.readAllBytes(file)
        require(bytes.size.toLong() in 1..MAX_BYTES) {
            "O SVG mudou durante a leitura ou excede 2 MB."
        }
        // Normaliza um cabeçalho DOCTYPE simples antes do parser vetorial,
        // sem carregar DTD ou alterar o SVG original. Entidades e subsets
        // internos seguem bloqueados.
        val safeBytes = stripInertSvgDoctype(bytes)
        require(safeBytes.size.toLong() in 1..MAX_BYTES) {
            "O SVG excede o limite de 2 MB."
        }
        try {
            validateSafeXml(safeBytes)
        } catch (invalid: Exception) {
            throw IllegalArgumentException(
                "SVG inválido ou com recursos XML não suportados. " +
                    "Exporte como SVG simples, sem entidades externas.",
                invalid
            )
        }
        return Selected(file.fileName.toString(), safeBytes)
    }

    /**
     * DOCTYPE externo não é necessário para desenhar SVG: alguns editores
     * exportam um cabeçalho SYSTEM/PUBLIC mesmo quando nenhuma entidade é
     * utilizada. Remove somente a declaração INERTE antes do parser.
     * Não abre URLs externas, não aceita subsets internos/entidades e não
     * altera o arquivo selecionado pelo usuário.
     */
    private fun stripInertSvgDoctype(bytes: ByteArray): ByteArray {
        val latin1 = String(bytes, Charsets.ISO_8859_1)
        if (!latin1.contains("<!DOCTYPE", ignoreCase = true)) return bytes

        val utf8 = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (invalid: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException(
                "Não foi possível interpretar o SVG. Verifique a codificação UTF-8.",
                invalid
            )
        }
        val encoding = Regex(
            """(?is)^\uFEFF?\s*<\?xml[^?]*encoding\s*=\s*['"]([^'"]+)['"]"""
        ).find(utf8)?.groupValues?.get(1)
        require(encoding == null || encoding.equals("utf-8", true) ||
            encoding.equals("utf8", true)) {
            "O SVG declara uma codificação diferente de UTF-8."
        }

        // Aceita cabeçalho vazio, SYSTEM ou PUBLIC. O endereço da DTD é
        // descartado como texto e NUNCA aberto ou interpretado pelo XML.
        val quotedId = """(?:"[^"'<>]+"|'[^"'<>]+')"""
        val inertHeader = Regex(
            """(?is)<!DOCTYPE\s+svg(?:\s+SYSTEM\s+$quotedId|\s+PUBLIC\s+$quotedId\s+$quotedId)?\s*>"""
        ).find(utf8)
        require(inertHeader != null &&
            !inertHeader.value.contains('[') &&
            !inertHeader.value.contains(']') &&
            !inertHeader.value.contains('&')) {
            "O SVG possui uma declaração XML incompatível com a importação segura."
        }
        val cleaned = utf8.removeRange(inertHeader.range)
        require(!cleaned.contains("<!DOCTYPE", ignoreCase = true) &&
            !cleaned.contains("<!ENTITY", ignoreCase = true)) {
            "O SVG contém entidades XML ou mais de uma declaração de tipo."
        }
        return cleaned.toByteArray(Charsets.UTF_8)
    }

    /** Mensagem de importação amigável, sem expor detalhes internos do XML. */
    fun userMessage(error: Throwable): String {
        val cause = error.message.orEmpty()
        return when {
            cause.contains("DOCTYPE", true) ||
                cause.contains("DTD", true) ||
                cause.contains("entity", true) ->
                "Este SVG usa declarações XML não suportadas. " +
                    "Salve novamente como SVG simples, sem DTD."
            cause.contains("XML", true) ||
                cause.contains("SAXParse", true) ->
                "Não foi possível interpretar o SVG. " +
                    "Exporte novamente como SVG simples."
            else -> cause.ifBlank {
                "Não foi possível importar o SVG. Verifique o arquivo."
            }.take(220)
        }
    }

    /**
     * JDK parser is configured fail-closed. SVG can contain XML entities or
     * external DTDs; the Android parser alone uses best-effort feature flags.
     * Here a missing secure feature rejects the document before Android's
     * original SimpleSvgParser receives it.
     */
    private fun validateSafeXml(bytes: ByteArray) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.isExpandEntityReferences = false
        factory.isXIncludeAware = false
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
}

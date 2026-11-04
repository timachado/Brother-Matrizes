package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.MatrixEditor
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Primeira etapa de edição POR LETRA, exclusivamente para composições NOVAS.
 * Os glifos saem do gerador oficial do Android e suas transformações passam
 * pelo MatrixEditor oficial. Não tenta separar glifos em arquivos DST/PES/JEF
 * importados; eles não preservam identidade de letras/objetos.
 *
 * A composição guarda somente parâmetros leves na memória da sessão.
 * Ao exportar FLP/PES/JEF/DST, os objetos são achatados em pontadas; o FLP
 * Android atual não possui um codec de objetos por letra.
 */
internal data class DesktopLetterObject(
    val character: Char,
    val moveXmm: Float = 0f,
    val moveYmm: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f
)

internal data class DesktopLetterLayout(
    val text: String,
    val heightMm: Float,
    val style: TextStitchStyle,
    val objects: List<DesktopLetterObject>
) {
    val editableIndices: List<Int> get() =
        objects.indices.filter { objects[it].character != ' ' }
}

internal object DesktopLetterComposer {
    const val MAX_CHARACTERS = 10
    const val MAX_POINTS = 200_000
    private const val SPACING_MM = 1.2f
    private const val MAX_SPAN_MM = 300f
    private val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑ "

    fun start(text: String, heightMm: Float, style: TextStitchStyle): DesktopLetterLayout {
        val clean = text.trim().uppercase(Locale.forLanguageTag("pt-BR"))
        require(clean.length in 1..MAX_CHARACTERS && clean.any { it != ' ' }) {
            "Use de 1 a $MAX_CHARACTERS caracteres, incluindo espaços."
        }
        require(clean.all { it in allowed }) {
            "Nesta QA, a composição individual aceita letras latinas e espaços."
        }
        require(heightMm.isFinite() && heightMm in 4f..40f) {
            "Altura das letras: permitido de 4 a 40 mm."
        }
        return DesktopLetterLayout(
            text = clean, heightMm = heightMm, style = style,
            objects = clean.map(::DesktopLetterObject)
        )
    }

    fun update(
        layout: DesktopLetterLayout,
        index: Int,
        moveXmm: Float,
        moveYmm: Float,
        scale: Float,
        rotationDegrees: Float
    ): DesktopLetterLayout {
        require(index in layout.objects.indices && layout.objects[index].character != ' ') {
            "Selecione uma letra válida antes de editar."
        }
        require(moveXmm.isFinite() && moveXmm in -100f..100f &&
            moveYmm.isFinite() && moveYmm in -100f..100f) {
            "Movimento individual: permitido de -100 a +100 mm."
        }
        require(scale.isFinite() && scale in 0.5f..2f) {
            "Escala individual: permitido de 0,50 a 2,00."
        }
        require(rotationDegrees.isFinite() && rotationDegrees in -180f..180f) {
            "Rotação individual: permitido de -180° a +180°."
        }
        return layout.copy(
            objects = layout.objects.toMutableList().also {
                it[index] = it[index].copy(
                    moveXmm = moveXmm, moveYmm = moveYmm,
                    scale = scale, rotationDegrees = rotationDegrees
                )
            }
        )
    }

    /** Combina pontadas num documento NOVO, com um só END e JUMPs de ligação. */
    fun render(layout: DesktopLetterLayout): DesktopMatrixDocument {
        require(layout.objects.size == layout.text.length &&
            layout.objects.map { it.character }.joinToString("") == layout.text) {
            "Composição de letras inválida."
        }
        // Protege também contra instâncias forjadas, não só valores da UI.
        val validated = start(layout.text, layout.heightMm, layout.style)
        require(validated.objects.size == layout.objects.size) { "Composição inconsistente." }

        val points = ArrayList<EmbroideryPoint>()
        var cursorMm = 0f
        var previous: EmbroideryDesign? = null
        for ((index, obj) in layout.objects.withIndex()) {
            if (obj.character == ' ') {
                cursorMm += layout.heightMm * 0.38f + SPACING_MM
                continue
            }
            // Reutiliza as mesmas restrições da UI para impedir contornos extremos.
            update(layout, index, obj.moveXmm, obj.moveYmm, obj.scale, obj.rotationDegrees)
            val generated = TextMatrixGenerator.generate(
                TextMatrixOptions(
                    text = obj.character.toString(),
                    heightMm = layout.heightMm,
                    style = layout.style,
                    outputFormat = "DST"
                )
            ).getOrThrow()
            val normalized = EmbroideryIntegrity.normalize(generated).getOrThrow().design
            val centerXmm = cursorMm + normalized.bounds.widthMm / 2f + obj.moveXmm
            val targetX = (centerXmm * 10f).roundToInt()
            val targetY = (obj.moveYmm * 10f).roundToInt()
            val transformed = MatrixEditor.apply(
                normalized,
                EditTransform(
                    scale = obj.scale,
                    rotationDegrees = obj.rotationDegrees,
                    offsetXUnits = targetX,
                    offsetYUnits = targetY,
                    centerAtOrigin = true
                )
            )
            val letterPoints = transformed.points.filter { it.command != StitchCommand.END }
            require(letterPoints.isNotEmpty()) { "Letra sem comandos de bordado." }
            // Garante um deslocamento sem costura entre letras independentes.
            val first = letterPoints.first()
            points += EmbroideryPoint(first.xUnits, first.yUnits, StitchCommand.JUMP, 0)
            points.addAll(letterPoints.map { it.copy(colorIndex = 0) })
            require(points.size <= MAX_POINTS) { "Composição excedeu o limite seguro de pontos." }
            cursorMm += normalized.bounds.widthMm + SPACING_MM
            previous = transformed
        }
        require(points.any { it.command == StitchCommand.STITCH }) {
            "A composição não gerou pontadas de costura."
        }
        val last = points.last()
        points += EmbroideryPoint(last.xUnits, last.yUnits, StitchCommand.END, 0)
        val source = requireNotNull(previous) { "Composição sem letras bordáveis." }
        val synthetic = source.copy(
            fileName = "letras-windows-qa.dst",
            format = "DST",
            label = layout.text,
            points = points,
            sourceBytes = byteArrayOf(),
            threadColors = listOf(0xE6BE70),
            editableName = null,
            isModified = false
        )
        val design = EmbroideryIntegrity.normalize(synthetic).getOrThrow().design
        require(design.endFound && design.stitchCount > 0 &&
            design.bounds.widthMm <= MAX_SPAN_MM && design.bounds.heightMm <= MAX_SPAN_MM) {
            "Composição fora do limite dimensional de QA (300 mm)."
        }
        return DesktopMatrixDocument(original = design, current = design)
    }
}

/** Undo/redo dos PARÂMETROS individuais (não duplica grandes listas de pontos). */
internal data class DesktopLetterSession private constructor(
    private val revisions: List<DesktopLetterLayout>,
    val position: Int
) {
    companion object {
        const val MAX_UNDO = 8
        fun start(layout: DesktopLetterLayout) = DesktopLetterSession(listOf(layout), 0)
    }

    val current: DesktopLetterLayout get() = revisions[position]
    val canUndo: Boolean get() = position > 0
    val canRedo: Boolean get() = position < revisions.lastIndex

    fun record(updated: DesktopLetterLayout): DesktopLetterSession {
        if (updated == current) return this
        val next = revisions.take(position + 1) + updated
        val bounded = next.takeLast(MAX_UNDO + 1)
        return DesktopLetterSession(bounded, bounded.lastIndex)
    }

    fun undo(): DesktopLetterSession {
        check(canUndo) { "Nenhum ajuste individual para desfazer." }
        return copy(position = position - 1)
    }

    fun redo(): DesktopLetterSession {
        check(canRedo) { "Nenhum ajuste individual para refazer." }
        return copy(position = position + 1)
    }
}

package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform

/**
 * Histórico de edição da sessão Windows: guarda apenas operações, e não cópias
 * de centenas de milhares de pontadas. Desfazer/refazer recalculam por meio
 * do MESMO MatrixEditor/EmbroideryIntegrity do Android.
 *
 * Nunca persiste no .FLP, não grava em disco e não altera o documento-base.
 * Manter no máximo oito operações reconstruíveis evita uso de memória
 * proporcional à quantidade de matrizes abertas.
 */
internal sealed interface DesktopEditAction {
    data class Transform(val value: EditTransform) : DesktopEditAction
    data object RestoreOriginal : DesktopEditAction
}

internal data class DesktopHistoryTransition(
    val timeline: DesktopEditTimeline,
    val document: DesktopMatrixDocument
)

internal data class DesktopEditTimeline private constructor(
    private val baseline: DesktopMatrixDocument,
    private val actions: List<DesktopEditAction>,
    val position: Int
) {
    companion object {
        const val MAX_ACTIONS = 8

        fun start(document: DesktopMatrixDocument): DesktopEditTimeline =
            DesktopEditTimeline(document, emptyList(), 0)
    }

    val canUndo: Boolean get() = position > 0
    val canRedo: Boolean get() = position < actions.size
    val total: Int get() = actions.size

    /**
     * Registrar somente APÓS o serviço ter aprovado a edição. Ações novas
     * invalidam a pilha de refazer. Ao atingir o limite, consolida a operação
     * mais antiga na linha-base de sessão, preservando as oito mais recentes.
     */
    fun record(action: DesktopEditAction): DesktopEditTimeline {
        val appended = actions.take(position) + action
        if (appended.size <= MAX_ACTIONS) {
            return copy(actions = appended, position = appended.size)
        }
        val nextBaseline = applyAction(baseline, appended.first())
        return DesktopEditTimeline(
            baseline = nextBaseline,
            actions = appended.drop(1),
            position = MAX_ACTIONS
        )
    }

    fun undo(): DesktopHistoryTransition {
        check(canUndo) { "Não há edição para desfazer." }
        val previous = copy(position = position - 1)
        return DesktopHistoryTransition(previous, previous.reconstruct())
    }

    fun redo(): DesktopHistoryTransition {
        check(canRedo) { "Não há edição para refazer." }
        val next = copy(position = position + 1)
        return DesktopHistoryTransition(next, next.reconstruct())
    }

    private fun reconstruct(): DesktopMatrixDocument =
        actions.take(position).fold(baseline, ::applyAction)

    private fun applyAction(
        document: DesktopMatrixDocument,
        action: DesktopEditAction
    ): DesktopMatrixDocument = when (action) {
        is DesktopEditAction.Transform ->
            DesktopMatrixService.edit(document, action.value)
        DesktopEditAction.RestoreOriginal ->
            DesktopMatrixService.reset(document)
    }
}

package com.timachado.brothermatrizes.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopTransformInputTest {
    private fun parse(
        scale: String = "1,00",
        horizontal: String = "1,00",
        vertical: String = "1,00",
        angle: String = "0",
        moveX: String = "0",
        moveY: String = "0"
    ) = DesktopTransformInput.parse(
        scale = scale,
        horizontal = horizontal,
        vertical = vertical,
        angle = angle,
        moveX = moveX,
        moveY = moveY
    )

    @Test
    fun `aceita vírgula decimal e expande eixos independentemente`() {
        val transform = parse(
            scale = "1,25",
            horizontal = "1,50",
            vertical = "0,75",
            angle = "-15,5",
            moveX = "12,3",
            moveY = "-4,6"
        )
        assertEquals(1.25f, transform.scale)
        assertEquals(1.5f, transform.scaleX)
        assertEquals(0.75f, transform.scaleY)
        assertEquals(-15.5f, transform.rotationDegrees)
        assertEquals(123, transform.offsetXUnits)
        assertEquals(-46, transform.offsetYUnits)
    }

    @Test
    fun `rejeita entrada inválida antes de aplicar qualquer alteração`() {
        val cases = listOf(
            parseArgs(scale = "0,20"),
            parseArgs(scale = "4"),
            parseArgs(horizontal = "0,49"),
            parseArgs(vertical = "2,51"),
            parseArgs(horizontal = "NaN"),
            parseArgs(vertical = "Infinity"),
            parseArgs(angle = "720"),
            parseArgs(moveX = "200,1"),
            parseArgs(moveY = "-201"),
            parseArgs(horizontal = "texto")
        )
        cases.forEach { args ->
            assertFailsWith<IllegalArgumentException> {
                DesktopTransformInput.parse(
                    scale = args[0], horizontal = args[1], vertical = args[2],
                    angle = args[3], moveX = args[4], moveY = args[5]
                )
            }
        }
    }

    private fun parseArgs(
        scale: String = "1", horizontal: String = "1", vertical: String = "1",
        angle: String = "0", moveX: String = "0", moveY: String = "0"
    ) = listOf(scale, horizontal, vertical, angle, moveX, moveY)

    @Test
    fun `expansão horizontal mantém altura quando vertical é neutra`() {
        val initial = DesktopMatrixService.createName("QA", 15f)
        val oldWidth = initial.current.bounds.maxXUnits - initial.current.bounds.minXUnits
        val oldHeight = initial.current.bounds.maxYUnits - initial.current.bounds.minYUnits
        assertTrue(oldWidth > 0 && oldHeight > 0)
        val transform = parse(horizontal = "1,50")
        val edited = DesktopMatrixService.edit(initial, transform)
        val newWidth = edited.current.bounds.maxXUnits - edited.current.bounds.minXUnits
        val newHeight = edited.current.bounds.maxYUnits - edited.current.bounds.minYUnits
        assertTrue(newWidth.toFloat() / oldWidth in 1.45f..1.55f)
        assertTrue(newHeight.toFloat() / oldHeight in 0.97f..1.03f)
        assertEquals(initial.current.points, initial.original.points)
    }

    @Test
    fun `expansão vertical mantém largura e é reversível no histórico`() {
        val initial = DesktopMatrixService.createName("QA", 15f)
        val beforeWidth = initial.current.bounds.maxXUnits - initial.current.bounds.minXUnits
        val beforeHeight = initial.current.bounds.maxYUnits - initial.current.bounds.minYUnits
        val transform = parse(vertical = "1,30")
        val edited = DesktopMatrixService.edit(initial, transform)
        val afterWidth = edited.current.bounds.maxXUnits - edited.current.bounds.minXUnits
        val afterHeight = edited.current.bounds.maxYUnits - edited.current.bounds.minYUnits
        assertTrue(afterWidth.toFloat() / beforeWidth in 0.97f..1.03f)
        assertTrue(afterHeight.toFloat() / beforeHeight in 1.25f..1.35f)

        val timeline = DesktopEditTimeline.start(initial)
            .record(DesktopEditAction.Transform(transform))
        val undo = timeline.undo()
        assertEquals(initial.current.points, undo.document.current.points)
        val redo = undo.timeline.redo()
        assertEquals(edited.current.points, redo.document.current.points)
    }
}

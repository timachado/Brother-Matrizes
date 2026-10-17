package com.timachado.brothermatrizes.core.embroidery

/**
 * QA-only decomposition of a textured logo's FLAT COLOR STUDY into exact
 * connected masks. These masks help a human inspect the emblem, individual
 * glyphs and counters before editing Satin rails.
 *
 * It does NOT transform photographic thread highlights into stitches,
 * certify the source artwork, alter the original photo, or create PES bytes.
 */
internal data class RasterAssistedElement(
    val id: Int,
    val color: Int,
    val pixelCount: Int,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val enclosedHoles: Int,
    val requiresManualReview: Boolean = true
) {
    val widthPx: Int get() = maxX - minX + 1
    val heightPx: Int get() = maxY - minY + 1
}

internal data class RasterAssistedElementPlan(
    val elements: List<RasterAssistedElement>,
    val totalComponents: Int,
    val sourceForegroundPixels: Int,
    val displayedForegroundPixels: Int,
    val undisplayedForegroundPixels: Int,
    val originalAlphaPreserved: Boolean,
    val exportPermitted: Boolean = false,
    private val pixels: IntArray,
    private val labels: IntArray,
    private val width: Int,
    private val height: Int,
    private val sourceLabel: String
) {
    fun isolate(id: Int): Result<RasterImagePixels> = runCatching {
        val part = elements.firstOrNull { it.id == id } ?: error(
            "Elemento fora da seleção assistida."
        )
        val image = IntArray(pixels.size) { index ->
            if (labels[index] == part.id) pixels[index] else 0
        }
        require(image.count { (it ushr 24) >= 180 } == part.pixelCount) {
            "A máscara perdeu pixels do elemento original."
        }
        RasterImagePixels(width, height, image,
            sourceLabel + " — elemento " + part.id + " (QA)")
    }

    fun summary(): String =
        totalComponents.toString() + " elemento(s) visuais em famílias de cor; " +
            elements.size + " disponível(is) para revisão. " +
            if (undisplayedForegroundPixels > 0) {
                undisplayedForegroundPixels.toString() +
                    " pixels de partes adicionais permanecem na imagem " +
                    "original e exigem revisão, sem serem descartados. " +
                    "Nenhuma matriz PES foi gerada."
            } else {
                "Todos os pixels foram identificados para revisão. " +
                    "Ainda não há pontos de bordado homologados."
            }
}

internal object RasterAssistedElementPlanner {
    private const val MAX_IMAGE_SIDE = 512
    private const val MAX_VISIBLE_ELEMENTS = 48
    private const val MAX_COMPONENTS = 4096
    private const val ALPHA_LIMIT = 180

    fun propose(
        original: RasterImagePixels,
        flatStudy: RasterThreadTextureStudyReport
    ): Result<RasterAssistedElementPlan> = runCatching {
        val w = original.width
        val h = original.height
        val flat = flatStudy.flatPreview
        require(w in 8..MAX_IMAGE_SIDE && h in 8..MAX_IMAGE_SIDE &&
            original.argb.size == w * h &&
            flat.width == w && flat.height == h &&
            flat.argb.size == original.argb.size &&
            flatStudy.originalAlphaHolesPreserved &&
            !flatStudy.exportPermitted
        ) { "Análise de elementos incompatível com a foto original." }

        var sourceForeground = 0
        for (index in original.argb.indices) {
            val visible = (original.argb[index] ushr 24) >= ALPHA_LIMIT
            val flatVisible = (flat.argb[index] ushr 24) >= ALPHA_LIMIT
            require(visible == flatVisible) {
                "Uma cor plana modificou a silhueta ou a contraforma original."
            }
            if (visible) sourceForeground++
        }
        require(sourceForeground >= 24 && !RasterEmbroideryApproval
            .canExportFromImageTool()
        ) { "Sem desenho confiável para revisão assistida." }

        val labels = IntArray(w * h) { -1 }
        val queue = IntArray(w * h)
        data class Component(
            val id: Int,
            val color: Int,
            val pixels: Int,
            val minX: Int,
            val maxX: Int,
            val minY: Int,
            val maxY: Int
        )
        val parts = ArrayList<Component>()
        for (root in labels.indices) {
            val c = flat.argb[root]
            if ((c ushr 24) < ALPHA_LIMIT || labels[root] >= 0) continue
            require(parts.size < MAX_COMPONENTS) {
                "Muitos fragmentos de textura: recorte a arte e revise manualmente."
            }
            val id = parts.size + 1
            labels[root] = id
            queue[0] = root
            var head = 0
            var tail = 1
            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                minX = minOf(minX,x); maxX = maxOf(maxX,x)
                minY = minOf(minY,y); maxY = maxOf(maxY,y)
                // 8-connected, but NEVER connect two different thread-color
                // families or paint transparent holes between glyph strokes.
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val next = ny*w+nx
                    if (labels[next] == -1 && flat.argb[next] == c) {
                        labels[next] = id
                        queue[tail++] = next
                    }
                }
            }
            parts += Component(id,c,tail,minX,maxX,minY,maxY)
        }
        require(parts.isNotEmpty() && parts.sumOf { it.pixels } ==
            sourceForeground
        ) { "Algum pixel de linha foi ignorado pela separação." }

        val selected = parts.sortedWith(
            compareByDescending<Component> { it.pixels }
                .thenBy { it.minY }.thenBy { it.minX }
        ).take(MAX_VISIBLE_ELEMENTS)

        fun countHoles(component: Component): Int {
            val boxW = component.maxX - component.minX + 1
            val boxH = component.maxY - component.minY + 1
            if (boxW < 3 || boxH < 3) return 0
            val visited = BooleanArray(boxW * boxH)
            val localQueue = IntArray(boxW * boxH)
            var holes = 0
            for (sy in 0 until boxH) for (sx in 0 until boxW) {
                val start = sy*boxW+sx
                if (visited[start] || labels[(sy+component.minY)*w +
                    sx+component.minX] == component.id) continue
                visited[start] = true
                localQueue[0] = start
                var head = 0
                var tail = 1
                var touchesOuterEdge = false
                while (head < tail) {
                    val p = localQueue[head++]
                    val x = p % boxW
                    val y = p / boxW
                    if (x == 0 || y == 0 || x == boxW-1 || y == boxH-1) {
                        touchesOuterEdge = true
                    }
                    val neighbors = intArrayOf(
                        if (x > 0) p-1 else -1,
                        if (x < boxW-1) p+1 else -1,
                        if (y > 0) p-boxW else -1,
                        if (y < boxH-1) p+boxW else -1
                    )
                    for (next in neighbors) {
                        if (next < 0 || visited[next]) continue
                        val gx = component.minX + next % boxW
                        val gy = component.minY + next / boxW
                        if (labels[gy*w+gx] == component.id) continue
                        visited[next] = true
                        localQueue[tail++] = next
                    }
                }
                if (!touchesOuterEdge) holes++
            }
            return holes
        }
        val visible = selected.map { c ->
            RasterAssistedElement(c.id,c.color,c.pixels,c.minX,c.minY,
                c.maxX,c.maxY,countHoles(c))
        }
        val shown = visible.sumOf { it.pixelCount }
        RasterAssistedElementPlan(
            elements = visible,
            totalComponents = parts.size,
            sourceForegroundPixels = sourceForeground,
            displayedForegroundPixels = shown,
            undisplayedForegroundPixels = sourceForeground - shown,
            originalAlphaPreserved = true,
            pixels = flat.argb,
            labels = labels,
            width = w,
            height = h,
            sourceLabel = original.label
        )
    }
}

package com.timachado.brothermatrizes.core.embroidery

/**
 * Conservative QA component budget for text-based logos (e.g., a mark and
 * two words). A 512x512 ARGB canvas uses up to 24 independent masks, so the
 * worst-case raw mask storage is about 24 MiB. It is NOT a stitch-count or
 * machine-safety approval; those guards remain independently enforced.
 */
internal object RasterQaComponentBudget {
    const val MAX_COMPONENTS = 24
    const val MAX_COLORS = 4
    const val MAX_STITCH_COMMANDS = 90_000
}

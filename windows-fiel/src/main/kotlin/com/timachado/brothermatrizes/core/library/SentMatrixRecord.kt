package com.timachado.brothermatrizes.core.library

/**
 * Modelo JVM com os mesmos cinco campos do SentMatrixRecord Android.
 * A lista Windows só é preenchida quando houver registro real de envio;
 * na QA atual permanece vazia. Não cria histórico falso.
 */
data class SentMatrixRecord(
    val id: String,
    val fileName: String,
    val format: String,
    val method: String,
    val sentAtMillis: Long
)

package com.timachado.brothermatrizes

import android.content.Context
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.StitchCommand

/**
 * Telemetria estritamente técnica para builds DEBUG.
 *
 * Não envia texto digitado, label, nome de arquivo, conta, e-mail, IP
 * customizado ou qualquer dado de conteúdo do usuário. Session Replay não é
 * habilitado. O objetivo é comparar a sequência de pontos gerada no aparelho
 * com o motor de referência.
 */
internal object DebugTelemetry {
    private var initialized =
        false

    fun setup(
        context: Context
    ) {
        if (
            initialized ||
            BuildConfig.POSTHOG_PROJECT_TOKEN
                .isBlank()
        ) {
            return
        }

        val config =
            PostHogAndroidConfig(
                apiKey =
                    BuildConfig
                        .POSTHOG_PROJECT_TOKEN,
                host =
                    "https://us.i.posthog.com"
            )

        PostHogAndroid.setup(
            context.applicationContext,
            config
        )

        initialized =
            true
    }

    fun captureSimulationOpened(
        design: EmbroideryDesign
    ) {
        if (
            !initialized
        ) {
            return
        }

        val visible =
            design.points
                .filter {
                    it.command !=
                        StitchCommand.END
                }

        val firstCommands =
            visible
                .take(
                    32
                )
                .joinToString(
                    ","
                ) {
                    it.command.name
                }

        val firstPoints =
            visible
                .take(
                    16
                )
                .joinToString(
                    "|"
                ) {
                    point ->
                    point.command.name +
                        ":" +
                        point.xUnits +
                        ":" +
                        point.yUnits
                }

        val commandCounts =
            StitchCommand.entries
                .associate {
                        command ->
                    command.name to
                        visible.count {
                            it.command ==
                                command
                        }
                }

        PostHog.capture(
            event =
                "brother debug simulation opened",
            properties =
                mapOf(
                    "\$process_person_profile" to
                        false,
                    "app_version" to
                        BuildConfig.VERSION_NAME,
                    "format" to
                        design.format,
                    "width_mm" to
                        design.bounds.widthMm,
                    "height_mm" to
                        design.bounds.heightMm,
                    "stitch_count" to
                        design.stitchCount,
                    "jump_count" to
                        design.jumpCount,
                    "source_y_axis_down" to
                        design.sourceYAxisDown,
                    "hoop" to
                        (
                            design.hoopProfile
                                ?.name
                                ?: "none"
                            ),
                    "first_commands" to
                        firstCommands,
                    "first_points" to
                        firstPoints,
                    "command_counts" to
                        commandCounts
                )
        )
    }
}

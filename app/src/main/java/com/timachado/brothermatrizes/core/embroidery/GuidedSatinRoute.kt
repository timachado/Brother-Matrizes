package com.timachado.brothermatrizes.core.embroidery

import org.json.JSONObject

/**
 * A rota marcada em uma imagem orienta a ORDEM de regioes Satin.
 * Nao representa pontadas individuais. Um JUMP_NO_STITCH nunca e
 * convertido em pontos STITCH.
 */
data class GuidedSatinPoint(val x: Float, val y: Float)

data class GuidedSatinStep(
    val order: Int,
    val jumpWithoutStitch: Boolean,
    val points: List<GuidedSatinPoint>
)

data class GuidedSatinRoute(
    val start: GuidedSatinPoint,
    val steps: List<GuidedSatinStep>
) {
    init {
        require(steps.isNotEmpty() && steps.any { !it.jumpWithoutStitch })
        require(steps.map { it.order } == (1..steps.size).toList())
    }

    companion object {
        fun parse(json: String): GuidedSatinRoute {
            val root = JSONObject(json)
            require(root.getString("format") == "brother-matrizes-route")
            require(root.getInt("version") == 1)
            require(root.getString("coordinates") == "image_pixels_x_right_y_down")

            val reference = root.getJSONObject("reference")
            val bounds = reference.getJSONObject("contentBoundsPx")
            val left = bounds.getDouble("left").toFloat()
            val top = bounds.getDouble("top").toFloat()
            val width = (bounds.getDouble("right") - left).toFloat()
            val height = (bounds.getDouble("bottom") - top).toFloat()
            require(width > 0f && height > 0f)
            require(reference.getInt("width") >= bounds.getDouble("right"))
            require(reference.getInt("height") >= bounds.getDouble("bottom"))

            fun point(obj: JSONObject): GuidedSatinPoint {
                val u = ((obj.getDouble("x").toFloat() - left) / width)
                val v = ((obj.getDouble("y").toFloat() - top) / height)
                require(u.isFinite() && v.isFinite())
                // Pequenas ultrapassagens do traco sao aceitas; nao truncar o caminho.
                require(u in -0.2f..1.2f && v in -0.2f..1.2f)
                return GuidedSatinPoint(u, v)
            }

            val rules = root.getJSONObject("instructions")
            require(rules.getBoolean("firstStitchMustStartAtMarkedPosition")) {
                "A rota precisa determinar a primeira penetracao da agulha."
            }
            require(rules.getBoolean("finishCurrentConnectedSatinRegionBeforeNext"))
            require(rules.getBoolean("pathIsRegionGuidanceNotIndividualNeedleStitches"))
            require(rules.getBoolean("doNotReturnToCompletedSatinRegion"))
            require(rules.getBoolean("doNotDrawUnmarkedConnectingStitches"))

            val raw = root.getJSONArray("route")
            require(raw.length() in 1..128)
            val steps = (0 until raw.length()).map { index ->
                val step = raw.getJSONObject(index)
                val kind = step.getString("kind")
                require(kind == "SATIN_REGION" || kind == "JUMP_NO_STITCH")
                val vertices = step.getJSONArray("points")
                require(vertices.length() in 1..10000)
                GuidedSatinStep(
                    order = step.getInt("order"),
                    jumpWithoutStitch = kind == "JUMP_NO_STITCH",
                    points = (0 until vertices.length()).map { j ->
                        point(vertices.getJSONObject(j))
                    }
                )
            }
            return GuidedSatinRoute(point(root.getJSONObject("start")), steps)
        }
    }
}

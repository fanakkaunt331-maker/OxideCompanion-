package com.oxide.companion

import android.graphics.Color



object FilterEngine {

    data class FilterParams(
        val overlayColorArgb: Int,
        val alphaPercent: Float
    )

    fun computeFilterParams(settings: OverlaySettings): FilterParams {
        val preset = FilterPreset.fromKey(settings.activeFilterPreset)

        return if (preset == FilterPreset.CUSTOM) {
            computeCustomFilter(settings)
        } else {
            computePresetFilter(preset, settings)
        }
    }

    private fun computePresetFilter(preset: FilterPreset, settings: OverlaySettings): FilterParams {
        val baseAlpha = preset.overlayColorARGB ushr 24
        val adjustedAlpha = (baseAlpha / 255f) * settings.filterIntensity
        val clampedAlpha = adjustedAlpha.coerceIn(0f, 0.3f)
        val colorWithoutAlpha = preset.overlayColorARGB and 0x00FFFFFF
        val finalAlpha = (clampedAlpha * 255).toInt().coerceIn(0, 76)
        return FilterParams(
            overlayColorArgb = (finalAlpha shl 24) or colorWithoutAlpha,
            alphaPercent = clampedAlpha
        )
    }

    private fun computeCustomFilter(settings: OverlaySettings): FilterParams {
        val hsl = hsvToRgb(settings.customHue, settings.customSaturation, settings.customBrightness)
        val rgb = Color.rgb(hsl.first, hsl.second, hsl.third)
        val alpha = (settings.filterAlpha * settings.filterIntensity * 255)
            .toInt().coerceIn(0, 76)
        return FilterParams(
            overlayColorArgb = (alpha shl 24) or (rgb and 0x00FFFFFF),
            alphaPercent = alpha / 255f
        )
    }

    private fun hsvToRgb(hueDeg: Float, sat: Float, value: Float): Triple<Int, Int, Int> {
        val h = ((hueDeg % 360f) + 360f) % 360f
        val s = sat.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - Math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Triple(
            ((r1 + m) * 255).toInt().coerceIn(0, 255),
            ((g1 + m) * 255).toInt().coerceIn(0, 255),
            ((b1 + m) * 255).toInt().coerceIn(0, 255)
        )
    }

    fun getLimitationsNotice(): String = """
        ⚠️ Important Technical Limitation
        
        These visual filters are SEMI-TRANSPARENT COLOR OVERLAYS drawn on top 
        of the entire screen using Android's WindowManager overlay system. 
        
        They are NOT GPU shaders. They do NOT modify how Oxide renders its 
        graphics internally. They cannot produce:
        • True bloom/glow effects
        • Screen-space ambient occlusion (SSAO)  
        • HDR tone mapping
        • Per-pixel shader transformations
        
        The overlay adds a small compositing cost to every frame.
    """.trimIndent()
}
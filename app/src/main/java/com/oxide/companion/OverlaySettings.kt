package com.oxide.companion
data class OverlaySettings(
    val isEnabled: Boolean = false,
    val transparency: Float = 0.85f,
    val buttonSizeDp: Int = 48,
    val panelWidthDp: Int = 280,
    val panelHeightDp: Int = 400,
    val autoHideEnabled: Boolean = true,
    val autoHideDelayMs: Long = 5000L,
    val animationsEnabled: Boolean = true,
    val panelPositionX: Int = 0,
    val panelPositionY: Int = 0,
    val isPanelExpanded: Boolean = false,
    val activeFilterPreset: String = "natural",
    val filterIntensity: Float = 0.15f,
    val filterAlpha: Float = 0.12f,
    val customHue: Float = 0f,
    val customSaturation: Float = 1f,
    val customBrightness: Float = 1f,
    val autoActivateOnLaunch: Boolean = false
)
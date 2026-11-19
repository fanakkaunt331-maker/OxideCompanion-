package com.oxide.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.oxide.companion.GraphicsProfile
import com.oxide.companion.OverlaySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "oxide_companion_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val PANEL_TRANSPARENCY = floatPreferencesKey("panel_transparency")
        val BUTTON_SIZE_DP = intPreferencesKey("button_size_dp")
        val PANEL_WIDTH_DP = intPreferencesKey("panel_width_dp")
        val PANEL_HEIGHT_DP = intPreferencesKey("panel_height_dp")
        val AUTO_HIDE_ENABLED = booleanPreferencesKey("auto_hide_enabled")
        val AUTO_HIDE_DELAY_MS = longPreferencesKey("auto_hide_delay_ms")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val PANEL_POSITION_X = intPreferencesKey("panel_position_x")
        val PANEL_POSITION_Y = intPreferencesKey("panel_position_y")
        val ACTIVE_FILTER_PRESET = stringPreferencesKey("active_filter_preset")
        val FILTER_INTENSITY = floatPreferencesKey("filter_intensity")
        val FILTER_ALPHA = floatPreferencesKey("filter_alpha")
        val CUSTOM_HUE = floatPreferencesKey("custom_hue")
        val CUSTOM_SATURATION = floatPreferencesKey("custom_saturation")
        val CUSTOM_BRIGHTNESS = floatPreferencesKey("custom_brightness")
        val AUTO_ACTIVATE_ON_LAUNCH = booleanPreferencesKey("auto_activate_on_launch")
        val SELECTED_GRAPHICS_PROFILE = stringPreferencesKey("selected_graphics_profile")
    }

    val overlaySettings: Flow<OverlaySettings> = context.dataStore.data.map { prefs ->
        OverlaySettings(
            isEnabled = prefs[Keys.OVERLAY_ENABLED] ?: false,
            transparency = prefs[Keys.PANEL_TRANSPARENCY] ?: 0.85f,
            buttonSizeDp = prefs[Keys.BUTTON_SIZE_DP] ?: 48,
            panelWidthDp = prefs[Keys.PANEL_WIDTH_DP] ?: 280,
            panelHeightDp = prefs[Keys.PANEL_HEIGHT_DP] ?: 400,
            autoHideEnabled = prefs[Keys.AUTO_HIDE_ENABLED] ?: true,
            autoHideDelayMs = prefs[Keys.AUTO_HIDE_DELAY_MS] ?: 5000L,
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
            panelPositionX = prefs[Keys.PANEL_POSITION_X] ?: 0,
            panelPositionY = prefs[Keys.PANEL_POSITION_Y] ?: 0,
            activeFilterPreset = prefs[Keys.ACTIVE_FILTER_PRESET] ?: "natural",
            filterIntensity = prefs[Keys.FILTER_INTENSITY] ?: 0.15f,
            filterAlpha = prefs[Keys.FILTER_ALPHA] ?: 0.12f,
            customHue = prefs[Keys.CUSTOM_HUE] ?: 0f,
            customSaturation = prefs[Keys.CUSTOM_SATURATION] ?: 1f,
            customBrightness = prefs[Keys.CUSTOM_BRIGHTNESS] ?: 1f,
            autoActivateOnLaunch = prefs[Keys.AUTO_ACTIVATE_ON_LAUNCH] ?: false
        )
    }

    val selectedGraphicsProfile: Flow<GraphicsProfile> = context.dataStore.data.map { prefs ->
        val key = prefs[Keys.SELECTED_GRAPHICS_PROFILE] ?: "balanced"
        GraphicsProfile.fromKey(key)
    }

    suspend fun saveOverlaySettings(settings: OverlaySettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OVERLAY_ENABLED] = settings.isEnabled
            prefs[Keys.PANEL_TRANSPARENCY] = settings.transparency
            prefs[Keys.BUTTON_SIZE_DP] = settings.buttonSizeDp
            prefs[Keys.PANEL_WIDTH_DP] = settings.panelWidthDp
            prefs[Keys.PANEL_HEIGHT_DP] = settings.panelHeightDp
            prefs[Keys.AUTO_HIDE_ENABLED] = settings.autoHideEnabled
            prefs[Keys.AUTO_HIDE_DELAY_MS] = settings.autoHideDelayMs
            prefs[Keys.ANIMATIONS_ENABLED] = settings.animationsEnabled
            prefs[Keys.PANEL_POSITION_X] = settings.panelPositionX
            prefs[Keys.PANEL_POSITION_Y] = settings.panelPositionY
            prefs[Keys.ACTIVE_FILTER_PRESET] = settings.activeFilterPreset
            prefs[Keys.FILTER_INTENSITY] = settings.filterIntensity
            prefs[Keys.FILTER_ALPHA] = settings.filterAlpha
            prefs[Keys.CUSTOM_HUE] = settings.customHue
            prefs[Keys.CUSTOM_SATURATION] = settings.customSaturation
            prefs[Keys.CUSTOM_BRIGHTNESS] = settings.customBrightness
            prefs[Keys.AUTO_ACTIVATE_ON_LAUNCH] = settings.autoActivateOnLaunch
        }
    }

    suspend fun saveGraphicsProfile(profile: GraphicsProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_GRAPHICS_PROFILE] = profile.key
        }
    }

    suspend fun clearAllSettings() {
        context.dataStore.edit { it.clear() }
    }
}
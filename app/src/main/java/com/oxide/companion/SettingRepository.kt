package com.oxide.companion

import android.content.Context
import com.oxide.companion.GraphicsProfile
import com.oxide.companion.OverlaySettings
import kotlinx.coroutines.flow.Flow

class SettingsRepository(context: Context) {
    private val dataStore = SettingsDataStore(context)

    val overlaySettings: Flow<OverlaySettings> = dataStore.overlaySettings
    val selectedGraphicsProfile: Flow<GraphicsProfile> = dataStore.selectedGraphicsProfile

    suspend fun updateOverlaySettings(settings: OverlaySettings) {
        dataStore.saveOverlaySettings(settings)
    }

    suspend fun updateGraphicsProfile(profile: GraphicsProfile) {
        dataStore.saveGraphicsProfile(profile)
    }

    suspend fun resetSettings() {
        dataStore.clearAllSettings()
    }
}
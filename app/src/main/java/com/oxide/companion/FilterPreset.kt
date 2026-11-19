package com.oxide.companion

enum class FilterPreset(
    val key: String,
    val displayName: String,
    val overlayColorARGB: Int,
    val description: String
) {
    NATURAL(
        key = "natural",
        displayName = "Natural",
        overlayColorARGB = 0x00FFFFFF,
        description = "Естественная цветопередача без изменений"
    ),
    CINEMATIC(
        key = "cinematic",
        displayName = "Cinematic",
        overlayColorARGB = 0x14000000,
        description = "Кинематографичная тональность с лёгким затемнением"
    ),
    COLD(
        key = "cold",
        displayName = "Cold",
        overlayColorARGB = 0x120040FF,
        description = "Холодные синеватые оттенки"
    ),
    WARM(
        key = "warm",
        displayName = "Warm",
        overlayColorARGB = 0x12FF8000,
        description = "Тёплые оранжево-жёлтые оттенки"
    ),
    NIGHT(
        key = "night",
        displayName = "Night",
        overlayColorARGB = 0x1E100020,
        description = "Мягкий тёмный оттенок для ночного режима"
    ),
    CUSTOM(
        key = "custom",
        displayName = "Custom",
        overlayColorARGB = 0x00FFFFFF,
        description = "Пользовательские настройки фильтра"
    );

    companion object {
        fun fromKey(key: String): FilterPreset {
            return entries.find { it.key == key } ?: NATURAL
        }
    }
}
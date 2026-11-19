package com.oxide.companion

enum class GraphicsProfile(
    val key: String,
    val displayName: String,
    val description: String,
    val recommendations: List<String>,
    val expectedLoad: LoadLevel
) {
    PERFORMANCE(
        key = "performance",
        displayName = "Performance",
        description = "Максимальная производительность, минимальная нагрузка",
        recommendations = listOf(
            "Graphics Quality: Low",
            "Shadow Quality: Off",
            "Anti-Aliasing: Off",
            "Post-Processing: Low",
            "Texture Quality: Low",
            "View Distance: Medium"
        ),
        expectedLoad = LoadLevel.LOW
    ),
    BALANCED(
        key = "balanced",
        displayName = "Balanced",
        description = "Сбалансированное качество изображения",
        recommendations = listOf(
            "Graphics Quality: Medium",
            "Shadow Quality: Medium",
            "Anti-Aliasing: FXAA",
            "Post-Processing: Medium",
            "Texture Quality: Medium",
            "View Distance: High"
        ),
        expectedLoad = LoadLevel.MEDIUM
    ),
    VISUAL(
        key = "visual",
        displayName = "Visual",
        description = "Максимальное визуальное качество",
        recommendations = listOf(
            "Graphics Quality: High",
            "Shadow Quality: High",
            "Anti-Aliasing: TAA/SMAA",
            "Post-Processing: High",
            "Texture Quality: High",
            "View Distance: Ultra"
        ),
        expectedLoad = LoadLevel.HIGH
    );

    enum class LoadLevel { LOW, MEDIUM, HIGH }

    companion object {
        fun fromKey(key: String): GraphicsProfile {
            return entries.find { it.key == key } ?: BALANCED
        }
    }
}
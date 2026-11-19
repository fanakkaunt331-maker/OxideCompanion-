package com.oxide.companion

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val shizukuManager = ShizukuManager()
    private lateinit var settingsRepo: com.oxide.companion.SettingsRepository
    private var currentScreen by mutableStateOf(Screen.Home)
    private var overlaySettings by mutableStateOf(OverlaySettings())
    private var oxideInstalled by mutableStateOf(false)
    private var oxideVersion by mutableStateOf<String?>(null)

    enum class Screen { Home, Filters, Profiles, Settings, ShizukuSetup }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepo = (application as App).settingsRepository

        setContent {
            OxideCompanionTheme {
                val shizukuStatus by shizukuManager.status.collectAsState()

                LaunchedEffect(Unit) {
                    settingsRepo.overlaySettings.collect { settings ->
                        overlaySettings = settings
                    }
                }

                LaunchedEffect(Unit) {
                    refreshOxideStatus()
                }

                when (currentScreen) {
                    Screen.Home -> HomeScreen(
                        shizukuStatus = shizukuStatus,
                        oxideInstalled = oxideInstalled,
                        oxideVersion = oxideVersion,
                        overlaySettings = overlaySettings,
                        onRequestShizukuPermission = { shizukuManager.requestPermission() },
                        onRefreshShizuku = { shizukuManager.refreshStatus() },
                        onLaunchOxide = { handleLaunchOxide() },
                        onToggleOverlay = { handleToggleOverlay() },
                        onNavigateToFilters = { currentScreen = Screen.Filters },
                        onNavigateToProfiles = { currentScreen = Screen.Profiles },
                        onNavigateToSettings = { currentScreen = Screen.Settings },
                        onNavigateToShizukuSetup = { currentScreen = Screen.ShizukuSetup }
                    )
                    Screen.Filters -> FiltersScreen(
                        settings = overlaySettings,
                        onUpdateSettings = { newSettings ->
                            lifecycleScope.launch {
                                settingsRepo.updateOverlaySettings(newSettings)
                            }
                        },
                        onBack = { currentScreen = Screen.Home }
                    )
                    Screen.Profiles -> {
                        val profile by settingsRepo.selectedGraphicsProfile.collectAsState(initial = GraphicsProfile.BALANCED)
                        ProfilesScreen(
                            selectedProfile = profile,
                            onSelectProfile = { p ->
                                lifecycleScope.launch {
                                    settingsRepo.updateGraphicsProfile(p)
                                }
                            },
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        settings = overlaySettings,
                        onUpdateSettings = { newSettings ->
                            lifecycleScope.launch {
                                settingsRepo.updateOverlaySettings(newSettings)
                            }
                        },
                        onCheckPermissions = { checkAllPermissions() },
                        onRunDiagnostics = { runDiagnostics() },
                        onBack = { currentScreen = Screen.Home }
                    )
                    Screen.ShizukuSetup -> ShizukuSetupScreen(onBack = { currentScreen = Screen.Home })
                }
            }
        }
    }

    private fun refreshOxideStatus() {
        oxideInstalled = OxideLauncher.isInstalled(this)
        oxideVersion = OxideLauncher.getVersionInfo(this)?.versionName
    }

    private fun handleLaunchOxide() {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            PermissionHelper.requestOverlayPermission(this)
            return
        }
        lifecycleScope.launch {
            val current = settingsRepo.overlaySettings.first()
            settingsRepo.updateOverlaySettings(current.copy(isEnabled = true))
            OverlayService.start(this@MainActivity)
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(300)
            OxideLauncher.launch(this@MainActivity)
        }
    }

    private fun handleToggleOverlay() {
        if (overlaySettings.isEnabled) {
            OverlayService.stop(this)
            lifecycleScope.launch {
                settingsRepo.updateOverlaySettings(overlaySettings.copy(isEnabled = false))
            }
        } else {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                PermissionHelper.requestOverlayPermission(this)
                return
            }
            OverlayService.start(this)
            lifecycleScope.launch {
                settingsRepo.updateOverlaySettings(overlaySettings.copy(isEnabled = true))
            }
        }
    }

    private fun checkAllPermissions() {
        val results = mutableListOf<String>()
        results.add("SYSTEM_ALERT_WINDOW: ${if (PermissionHelper.hasOverlayPermission(this)) "✅ Granted" else "❌ Missing"}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            results.add("POST_NOTIFICATIONS: ${if (notifGranted) "✅ Granted" else "❌ Missing"}")
        }
        results.add("Shizuku: ${shizukuManager.status.value.displayName}")
        results.add("Oxide Installed: ${if (oxideInstalled) "✅ Yes" else "❌ No"}")
        android.app.AlertDialog.Builder(this)
            .setTitle("Permission Check Results")
            .setMessage(results.joinToString("\n\n"))
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .show()
    }

    private fun runDiagnostics() {
        val sb = StringBuilder()
        sb.appendLine("Device: ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("")
        sb.appendLine("Android 15 Overlay Restriction:")
        sb.appendLine("• Overlay service MUST be started from foreground")
        sb.appendLine("• Use 'Launch Oxide' button which pre-starts overlay")
        sb.appendLine("")
        sb.appendLine("Visual Filters:")
        sb.appendLine("• Screen color overlays via WindowManager")
        sb.appendLine("• NOT GPU shaders — no bloom/SSAO/HDR possible")
        sb.appendLine("")
        sb.appendLine("Graphics Profiles:")
        sb.appendLine("• Recommendations only — manual application required")
        android.app.AlertDialog.Builder(this)
            .setTitle("Compatibility Diagnostics")
            .setMessage(sb.toString())
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
            .show()
    }

    // ============ COMPOSE UI ============

    @Composable
    private fun OxideCompanionTheme(content: @Composable () -> Unit) {
        val colorScheme = darkColorScheme(
            primary = Color(0xFF4ECDC4),
            onPrimary = Color(0xFF1A1A22),
            primaryContainer = Color(0xFF2A2A36),
            onPrimaryContainer = Color(0xFFE8E8EC),
            secondary = Color(0xFFFFB347),
            background = Color(0xFF1A1A22),
            onBackground = Color(0xFFE8E8EC),
            surface = Color(0xFF22222C),
            onSurface = Color(0xFFE8E8EC),
            surfaceVariant = Color(0xFF2A2A36),
            onSurfaceVariant = Color(0xFF9A9AAA),
            outline = Color(0xFF3A3A48),
            error = Color(0xFFF44336)
        )
        MaterialTheme(colorScheme = colorScheme, content = content)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeScreen(
        shizukuStatus: ShizukuStatus,
        oxideInstalled: Boolean,
        oxideVersion: String?,
        overlaySettings: OverlaySettings,
        onRequestShizukuPermission: () -> Unit,
        onRefreshShizuku: () -> Unit,
        onLaunchOxide: () -> Unit,
        onToggleOverlay: () -> Unit,
        onNavigateToFilters: () -> Unit,
        onNavigateToProfiles: () -> Unit,
        onNavigateToSettings: () -> Unit,
        onNavigateToShizukuSetup: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Oxide Companion", color = Color(0xFFE8E8EC)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A22))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF22222C))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏝️", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Oxide Companion", style = MaterialTheme.typography.titleLarge, color = Color(0xFFE8E8EC))
                        Text("Graphical & auxiliary tool", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
                    }
                }

                ShizukuStatusCard(
                    status = shizukuStatus,
                    onRequestPermission = onRequestShizukuPermission
                )

                if (!shizukuStatus.isConnected) {
                    TextButton(onClick = onRefreshShizuku, modifier = Modifier.align(Alignment.End)) {
                        Text("Re-check Connection", color = Color(0xFF4ECDC4))
                    }
                    TextButton(onClick = onNavigateToShizukuSetup, modifier = Modifier.align(Alignment.End)) {
                        Text("Initial Setup Instructions", color = Color(0xFF4ECDC4))
                    }
                }

                OxideStatusCard(
                    isInstalled = oxideInstalled,
                    versionName = oxideVersion,
                    onLaunch = onLaunchOxide
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF22222C))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Floating Overlay", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (overlaySettings.isEnabled)
                                "Active • Drag button to reposition"
                            else
                                "Disabled • Enable to show floating panel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF9A9AAA)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onToggleOverlay,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (overlaySettings.isEnabled)
                                    Color(0xFFF44336).copy(alpha = 0.7f)
                                else Color(0xFF3BA89F),
                                contentColor = Color(0xFF1A1A22)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (overlaySettings.isEnabled) "Disable Overlay" else "Enable Overlay")
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallNavCard("Visual Filters", "Screen color overlays", onNavigateToFilters, Modifier.weight(1f))
                    SmallNavCard("Graphics Profiles", "Recommended settings", onNavigateToProfiles, Modifier.weight(1f))
                }

                SmallNavCard("Settings", "Overlay config, permissions", onNavigateToSettings, Modifier.fillMaxWidth())

                Text(
                    text = "This app does not modify game memory or provide unfair advantages.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6A6A7A),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }

    @Composable
    private fun ShizukuStatusCard(status: ShizukuStatus, onRequestPermission: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222C))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(
                            when (status) {
                                is ShizukuStatus.ConnectedAdb, is ShizukuStatus.ConnectedRoot -> Color(0xFF4CAF50)
                                is ShizukuStatus.NoPermission -> Color(0xFFFF9800)
                                is ShizukuStatus.Error -> Color(0xFFF44336)
                                else -> Color(0xFF78909C)
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shizuku / ADB Status", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(status.displayName, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE8E8EC))
                Spacer(modifier = Modifier.height(4.dp))
                Text(status.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
                if (status == ShizukuStatus.NoPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3BA89F), contentColor = Color(0xFF1A1A22)),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Request Permission") }
                }
            }
        }
    }

    @Composable
    private fun OxideStatusCard(isInstalled: Boolean, versionName: String?, onLaunch: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222C))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(
                            if (isInstalled) Color(0xFF4CAF50) else Color(0xFF78909C)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Oxide: Survival Island", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isInstalled) "Installed • v$versionName" else "Not installed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9A9AAA)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onLaunch,
                    enabled = isInstalled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC8F3A), contentColor = Color(0xFF1A1A22)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (isInstalled) "Launch Oxide" else "Install Oxide First") }
            }
        }
    }

    @Composable
    private fun SmallNavCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
        Card(
            modifier = modifier.clickable(onClick = onClick),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222C))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FiltersScreen(settings: OverlaySettings, onUpdateSettings: (OverlaySettings) -> Unit, onBack: () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Visual Filters", color = Color(0xFFE8E8EC)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color(0xFFE8E8EC)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A22))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A36))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ Technical Limitations", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFB347))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(FilterEngine.getLimitationsNotice(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
                    }
                }
                Text("Presets", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                FilterPreset.entries.forEach { preset ->
                    FilterPresetCard(
                        preset = preset,
                        isActive = settings.activeFilterPreset == preset.key,
                        onClick = { onUpdateSettings(settings.copy(activeFilterPreset = preset.key)) }
                    )
                }
                if (settings.activeFilterPreset != "natural") {
                    Text("Filter Intensity", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                    Slider(
                        value = settings.filterIntensity,
                        onValueChange = { onUpdateSettings(settings.copy(filterIntensity = it)) },
                        valueRange = 0f..1f,
                        steps = 20
                    )
                    Text("Current: ${(settings.filterIntensity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun FilterPresetCard(preset: FilterPreset, isActive: Boolean, onClick: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF2A2A36) else Color(0xFF22222C))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(Color(preset.overlayColorARGB)),
                    contentAlignment = Alignment.Center
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(preset.displayName, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE8E8EC))
                    Text(preset.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
                }
                if (isActive) Icon(Icons.Default.Check, null, tint = Color(0xFF4ECDC4), modifier = Modifier.size(20.dp))
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ProfilesScreen(selectedProfile: GraphicsProfile, onSelectProfile: (GraphicsProfile) -> Unit, onBack: () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Graphics Profiles", color = Color(0xFFE8E8EC)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color(0xFFE8E8EC)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A22))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A36))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ℹ️ How to Use", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFB347))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("These are RECOMMENDATIONS only. Apply them manually in Oxide → Settings → Graphics.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
                    }
                }
                GraphicsProfile.entries.forEach { profile ->
                    ProfileRecommendationCard(
                        profile = profile,
                        isSelected = selectedProfile.key == profile.key,
                        onSelect = { onSelectProfile(profile) }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun ProfileRecommendationCard(profile: GraphicsProfile, isSelected: Boolean, onSelect: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2A2A36) else Color(0xFF22222C))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFE8E8EC))
                    Text("Load: ${profile.expectedLoad.name}", style = MaterialTheme.typography.labelSmall, color = when (profile.expectedLoad) {
                        GraphicsProfile.LoadLevel.LOW -> Color(0xFF4CAF50)
                        GraphicsProfile.LoadLevel.MEDIUM -> Color(0xFFFF9800)
                        GraphicsProfile.LoadLevel.HIGH -> Color(0xFFF44336)
                    })
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(profile.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
                Spacer(modifier = Modifier.height(8.dp))
                profile.recommendations.forEachIndexed { index, rec ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("${index + 1}. ", color = Color(0xFF3BA89F))
                        Text(rec, color = Color(0xFFE8E8EC))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFFCC8F3A) else Color(0xFF2A2A36),
                        contentColor = if (isSelected) Color(0xFF1A1A22) else Color(0xFFE8E8EC)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (isSelected) "Selected" else "Select Profile") }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(settings: OverlaySettings, onUpdateSettings: (OverlaySettings) -> Unit, onCheckPermissions: () -> Unit, onRunDiagnostics: () -> Unit, onBack: () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings", color = Color(0xFFE8E8EC)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color(0xFFE8E8EC)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A22))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Overlay Behavior", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                SwitchPreference("Auto-activate on Oxide Launch", "Automatically enable overlay when launching Oxide", settings.autoActivateOnLaunch) { onUpdateSettings(settings.copy(autoActivateOnLaunch = it)) }
                SwitchPreference("Auto-hide Panel", "Collapse floating panel after inactivity", settings.autoHideEnabled) { onUpdateSettings(settings.copy(autoHideEnabled = it)) }
                SwitchPreference("Animations", "Enable smooth transitions", settings.animationsEnabled) { onUpdateSettings(settings.copy(animationsEnabled = it)) }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                Text("Panel Transparency: ${(settings.transparency * 100).toInt()}%", color = Color(0xFF9A9AAA))
                Slider(value = settings.transparency, onValueChange = { onUpdateSettings(settings.copy(transparency = it)) }, valueRange = 0.3f..1f)
                Text("Button Size: ${settings.buttonSizeDp}dp", color = Color(0xFF9A9AAA))
                Slider(value = settings.buttonSizeDp.toFloat(), onValueChange = { onUpdateSettings(settings.copy(buttonSizeDp = it.toInt())) }, valueRange = 32f..72f)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Permissions & Diagnostics", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                Button(onClick = onCheckPermissions, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A36), contentColor = Color(0xFFE8E8EC)), shape = RoundedCornerShape(8.dp)) { Text("Verify All Permissions") }
                Button(onClick = onRunDiagnostics, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A36), contentColor = Color(0xFFE8E8EC)), shape = RoundedCornerShape(8.dp)) { Text("Run Compatibility Diagnostics") }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun SwitchPreference(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE8E8EC))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4ECDC4), checkedTrackColor = Color(0xFF3BA89F)))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ShizukuSetupScreen(onBack: () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Shizuku Setup Guide", color = Color(0xFFE8E8EC)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color(0xFFE8E8EC)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A22))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InstructionStep(1, "Install Shizuku", "Download Shizuku from Google Play Store or GitHub Releases. Minimum Android 7.0 required.")
                InstructionStep(2, "Enable Wireless Debugging", "Go to Developer Options → Wireless Debugging → Turn ON.")
                InstructionStep(3, "Pair Device (One-Time)", "In Wireless Debugging, tap 'Pair device with pairing code'. Enter the code in Shizuku.")
                InstructionStep(4, "Start Shizuku Service", "After pairing, press 'Start' in Shizuku.")
                InstructionStep(5, "Grant Permission", "Open Oxide Companion → tap 'Request Permission' on the home screen.")
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun InstructionStep(stepNumber: Int, title: String, description: String) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222C))
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).background(Color(0xFF3BA89F), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("$stepNumber", color = Color(0xFF1A1A22), fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8EC))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AAA))
                }
            }
        }
    }
}
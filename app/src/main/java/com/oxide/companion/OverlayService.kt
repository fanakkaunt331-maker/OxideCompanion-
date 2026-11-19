package com.oxide.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OverlayService : Service() {
    // ... дальше твой код

    private lateinit var windowManager: WindowManager
    private lateinit var floatingPanelView: FloatingPanelView
    private lateinit var filterOverlayView: View
    private var filterOverlayParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepo: SettingsRepository
    private var autoHideRunnable: Runnable? = null
    private var cachedSettings: OverlaySettings = OverlaySettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepo = (application as com.oxide.companion.App).settingsRepository
        createNotificationChannel()
        startForegroundWithNotification()
        setupViews()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun setupViews() {
        floatingPanelView = FloatingPanelView(this).apply {
            onDragDelta = { dx, dy ->
                panelParams?.let { params ->
                    params.x += dx
                    params.y += dy
                    windowManager.updateViewLayout(floatingPanelView, params)
                }
            }
            onExpandToggle = { isExpanded ->
                scheduleAutoHide(isExpanded)
            }
            onCloseRequested = {
                stopSelf()
            }
            onFilterChanged = { presetKey ->
                scope.launch {
                    val current = settingsRepo.overlaySettings.first()
                    settingsRepo.updateOverlaySettings(current.copy(activeFilterPreset = presetKey))
                }
            }
            onProfileChanged = { profileKey ->
                scope.launch {
                    val profile = GraphicsProfile.fromKey(profileKey)
                    settingsRepo.updateGraphicsProfile(profile)
                }
            }
        }

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        filterOverlayView = createFilterOverlayView()
        filterOverlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(floatingPanelView, panelParams)
        windowManager.addView(filterOverlayView, filterOverlayParams)
    }

    private fun createFilterOverlayView(): View {
        return object : View(this) {
            private val paint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val params = FilterEngine.computeFilterParams(cachedSettings)
                paint.color = params.overlayColorArgb
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepo.overlaySettings.collectLatest { settings ->
                cachedSettings = settings
                floatingPanelView.updateSettings(settings)
                filterOverlayView.invalidate()
                if (settings.panelPositionX != 0 || settings.panelPositionY != 0) {
                    panelParams?.let { params ->
                        params.x = settings.panelPositionX
                        params.y = settings.panelPositionY
                        windowManager.updateViewLayout(floatingPanelView, params)
                    }
                }
            }
        }
        scope.launch {
            settingsRepo.selectedGraphicsProfile.collectLatest { profile ->
                floatingPanelView.setGraphicsProfileDisplayName(profile.displayName)
            }
        }
    }

    private fun scheduleAutoHide(isExpanded: Boolean) {
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        if (isExpanded && cachedSettings.autoHideEnabled) {
            autoHideRunnable = Runnable {
                floatingPanelView.performClick()
            }
            handler.postDelayed(autoHideRunnable!!, cachedSettings.autoHideDelayMs)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.OVERLAY_CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Oxide Companion floating overlay"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, Constants.OVERLAY_CHANNEL_ID)
            .setContentTitle("Oxide Companion Overlay")
            .setContentText("Floating panel is active")
            .setSmallIcon(R.drawable.ic_overlay_button)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.OVERLAY_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.OVERLAY_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        try { windowManager.removeView(floatingPanelView) } catch (_: Exception) {}
        try { windowManager.removeView(filterOverlayView) } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_STOP = "com.oxide.companion.STOP_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
package com.oxide.companion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

// Убраны импорты .models и .filters, так как все классы теперь в одном пакете!

class FloatingPanelView(context: Context) : FrameLayout(context) {
    // ... дальше твой код
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var isDragging = false
    private var downX = 0f
    private var downY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var settings: OverlaySettings = OverlaySettings()
    private var currentFilterParams: FilterEngine.FilterParams? = null

    private lateinit var collapsedButton: View
    private lateinit var expandedPanel: LinearLayout
    private lateinit var filterIndicator: TextView
    private lateinit var profileIndicator: TextView

    // ... дальше идёт твой код с init { ... }

    var onExpandToggle: ((Boolean) -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onFilterChanged: ((String) -> Unit)? = null
    var onProfileChanged: ((String) -> Unit)? = null
    var onDragDelta: ((Int, Int) -> Unit)? = null

    init {
        collapsedButton = createCollapsedButton()
        addView(collapsedButton, LayoutParams(
            dpToPx(settings.buttonSizeDp),
            dpToPx(settings.buttonSizeDp),
            Gravity.CENTER
        ))

        expandedPanel = createExpandedPanel()
        expandedPanel.visibility = GONE
        addView(expandedPanel, LayoutParams(
            dpToPx(settings.panelWidthDp),
            dpToPx(settings.panelHeightDp),
            Gravity.CENTER
        ))
    }

    private fun createCollapsedButton(): View {
        return object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                paint.color = Color.argb(
                    (settings.transparency * 255).toInt(), 40, 40, 48
                )
                canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)
                paint.color = Color.WHITE
                paint.textSize = spToPx(18f)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("◆", width / 2f, height / 2f + spToPx(6f), paint)
            }
        }.apply {
            layoutParams = LayoutParams(dpToPx(settings.buttonSizeDp), dpToPx(settings.buttonSizeDp))
            isClickable = true
            setOnClickListener { togglePanel() }
        }
    }

    private fun createExpandedPanel(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb((settings.transparency * 255).toInt(), 30, 30, 38))
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)

            val header = TextView(context).apply {
                text = "Oxide Companion"
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            addView(header, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) })

            filterIndicator = TextView(context).apply {
                text = FilterPreset.fromKey(settings.activeFilterPreset).displayName
                textSize = 15f
                setTextColor(Color.WHITE)
                setOnClickListener { cycleFilter() }
            }
            addView(filterIndicator, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4); bottomMargin = dpToPx(12) })

            profileIndicator = TextView(context).apply {
                text = GraphicsProfile.BALANCED.displayName
                textSize = 15f
                setTextColor(Color.WHITE)
                setOnClickListener { cycleProfile() }
            }
            addView(profileIndicator, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4); bottomMargin = dpToPx(12) })

            val notice = TextView(context).apply {
                text = "Filters are screen overlays, not GPU shaders."
                textSize = 10f
                setTextColor(Color.GRAY)
            }
            addView(notice, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(8) })
        }
    }

    fun updateSettings(newSettings: OverlaySettings) {
        settings = newSettings
        currentFilterParams = FilterEngine.computeFilterParams(newSettings)
        invalidate()
        expandedPanel.setBackgroundColor(Color.argb(
            (newSettings.transparency * 255).toInt(), 30, 30, 38
        ))
        filterIndicator.text = FilterPreset.fromKey(newSettings.activeFilterPreset).displayName
    }

    fun setGraphicsProfileDisplayName(name: String) {
        profileIndicator.text = name
    }

    private fun togglePanel() {
        val isNowExpanded = expandedPanel.visibility == GONE
        expandedPanel.visibility = if (isNowExpanded) VISIBLE else GONE
        collapsedButton.visibility = if (isNowExpanded) GONE else VISIBLE
        onExpandToggle?.invoke(isNowExpanded)
    }

    private fun cycleFilter() {
        val presets = FilterPreset.entries.toList()
        val currentIndex = presets.indexOfFirst { it.key == settings.activeFilterPreset }
        val nextIndex = (currentIndex + 1) % presets.size
        val nextPreset = presets[nextIndex]
        settings = settings.copy(activeFilterPreset = nextPreset.key)
        filterIndicator.text = nextPreset.displayName
        currentFilterParams = FilterEngine.computeFilterParams(settings)
        onFilterChanged?.invoke(nextPreset.key)
        invalidate()
    }

    private fun cycleProfile() {
        val profiles = GraphicsProfile.entries.toList()
        val currentIndex = profiles.indexOfFirst { it.key == "balanced" }
        val nextIndex = (currentIndex + 1) % profiles.size
        val nextProfile = profiles[nextIndex]
        profileIndicator.text = nextProfile.displayName
        onProfileChanged?.invoke(nextProfile.key)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.rawX - downX)
                val dy = abs(event.rawY - downY)
                if (dx > dpToPx(10) || dy > dpToPx(10)) {
                    isDragging = true
                    onDragDelta?.invoke(
                        (event.rawX - lastTouchX).toInt(),
                        (event.rawY - lastTouchY).toInt()
                    )
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    return true
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && expandedPanel.visibility == GONE) {
                    togglePanel()
                }
                isDragging = false
                return true
            }
            else -> return super.onTouchEvent(event)
        }
    }

    fun getCurrentOverlayColor(): Int? {
        return currentFilterParams?.overlayColorArgb
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}
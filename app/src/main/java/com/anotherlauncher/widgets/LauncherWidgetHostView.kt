package com.anotherlauncher.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewOutlineProvider
import android.view.MotionEvent
import android.os.SystemClock
import android.widget.FrameLayout
import kotlin.math.ceil

/** The grid owns spacing. Provider content gets its full measured area, with no second inset. */
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var spanX: Int = 1
    var spanY: Int = 1
    private var published: List<Float>? = null
    
    private val enforcedRectangle = Rect()
    private val enforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context)
    private val cornerRadiusEnforcementOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (enforcedRectangle.isEmpty || enforcedCornerRadius <= 0f) {
                outline.setEmpty()
            } else {
                outline.setRoundRect(enforcedRectangle, enforcedCornerRadius)
            }
        }
    }
    
    private val publish = Runnable {
        if (isInEditMode || !isAttachedToWindow || width <= 0 || height <= 0 || appWidgetId < 0) return@Runnable
        val density = resources.displayMetrics.density
        val w = width / density
        val h = height / density
        val signature = listOf(w, h, spanX.toFloat(), spanY.toFloat())
        if (signature == published) return@Runnable
        // updateAppWidgetSize would subtract the platform padding despite setPadding(0).
        // Publish the actual provider content dimensions directly, once after layout.
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(w, h)))
        }
        // These optional provider hints were observed on One UI; geometry still uses public APIs.
        if (appWidgetInfo?.provider?.packageName == "com.sec.android.daemonapp") {
            options.putInt("semAppWidgetColumnSpan", spanX)
            options.putInt("semAppWidgetRowSpan", spanY)
        }
        updateAppWidgetOptions(options)
        published = signature
    }
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
        clipToPadding = false
        published = null
    }

    private fun enforceRoundedCorners() {
        val pkg = appWidgetInfo?.provider?.packageName ?: ""
        val isSamsungWidget = pkg.startsWith("com.samsung.") || pkg.startsWith("com.sec.") || pkg == "com.android.settings.intelligence"
        
        if (!isSamsungWidget || enforcedCornerRadius <= 0f) {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = false
            return
        }
        val background = RoundedCornerEnforcement.findBackground(this)
        if (background == null || RoundedCornerEnforcement.hasAppWidgetOptedOut(background)) {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = false
            return
        }
        RoundedCornerEnforcement.computeRoundedRectangle(this, background, enforcedRectangle)
        outlineProvider = cornerRadiusEnforcementOutline
        clipToOutline = true
        invalidateOutline()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); publishAfterLayout() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        publishAfterLayout()
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        enforceRoundedCorners()
    }
    fun publishAfterLayout() { removeCallbacks(publish); post(publish) }
    override fun onDetachedFromWindow() { removeCallbacks(publish); super.onDetachedFromWindow() }
}

/** Measure the real RemoteViews at their supported size and uniformly fit that view into the slot.
 * Native View transforms also transform touch coordinates; no drawing-only or bitmap workaround.
 */
class FittingWidgetFrame @JvmOverloads constructor(context: Context, val widget: LauncherWidgetHostView = LauncherWidgetHostView(context)) : FrameLayout(context) {
    private var providerGestureBlocked = false
    private var providerDownTime = 0L
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            providerGestureBlocked = false
            providerDownTime = event.downTime
        }
        return if (providerGestureBlocked) true else super.dispatchTouchEvent(event)
    }

    /** Transfer this touch stream to Home after a long hold. A provider must not click on release. */
    fun claimGestureForLauncher() {
        if (providerGestureBlocked) return
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(providerDownTime.takeIf { it > 0 } ?: now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
        providerGestureBlocked = true
        parent?.requestDisallowInterceptTouchEvent(true)
    }
    fun finishLauncherGesture() {
        // Keep this stream blocked until a fresh DOWN, even if a second pointer canceled pickup.
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    var requirements = WidgetRequirements(0f, 0f, 1f, 1f, false, false)
        set(value) { if (field != value) { field = value; requestLayout() } }
    var geometry: WidgetFit? = null
        private set
    init {
        clipChildren = false
        clipToPadding = false
        widget.pivotX = 0f; widget.pivotY = 0f
        addView(widget)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        val density = resources.displayMetrics.density
        val fit = WidgetSizing.fit(width, height, ceil(requirements.minWidth * density).toInt(), ceil(requirements.minHeight * density).toInt())
        geometry = fit
        widget.visibility = if (fit == null) View.INVISIBLE else View.VISIBLE
        widget.measure(View.MeasureSpec.makeMeasureSpec(fit?.width ?: 0, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(fit?.height ?: 0, View.MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val fit = geometry ?: run { widget.layout(0, 0, 0, 0); return }
        widget.layout(0, 0, fit.width, fit.height)
        val scaled = fit.scale.coerceAtLeast(.05f)
        widget.scaleX = scaled; widget.scaleY = scaled
        widget.translationX = (width - fit.width * scaled) / 2f
        widget.translationY = (height - fit.height * scaled) / 2f
        widget.publishAfterLayout()
    }
}

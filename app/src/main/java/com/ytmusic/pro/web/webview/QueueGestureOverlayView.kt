@file:Suppress("unused", "UnusedVariable")

package com.ytmusic.pro.web.webview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.ytmusic.pro.web.webapp.QueueLayoutSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.toColorInt

class QueueGestureOverlayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {

    interface Listener {
        fun onQueueTouchPassthroughStart(x: Float, y: Float)

        fun onQueueTouchPassthroughMove(x: Float, y: Float)

        fun onQueueTouchPassthroughEnd(x: Float, y: Float, cancelled: Boolean)

        fun onQueueLongPress(index: Int)

        fun onQueueDragRequested(fromIndex: Int, toIndex: Int, fingerprint: String?)

        fun onQueueRemoveRequested(index: Int, fingerprint: String?)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMillis = ViewConfiguration.getLongPressTimeout().toLong()
    private val minControlVisualSize = 46f * density
    private val minControlHitSize = 58f * density
    private val handleVisualWidth = 44f * density
    private val handleHitWidth = 68f * density

    private val removeFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#B01E3B".toColorInt()
            style = Paint.Style.FILL
        }
    private val removeStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2.3f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val handlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val handleFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 255, 255, 255)
            style = Paint.Style.FILL
        }
    private val dragHighlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 255, 255, 255)
            style = Paint.Style.FILL
        }

    private var latestSnapshot: QueueLayoutSnapshot? = null
    private var renderedItems: List<RenderedQueueItem> = emptyList()
    private var activeGesture: ActiveGesture? = null

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = INVISIBLE
    }

    fun updateQueueLayout(snapshot: QueueLayoutSnapshot?) {
        latestSnapshot = snapshot
        rebuildLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentGesture = activeGesture
        val draggingIndex = currentGesture?.takeIf { it.mode == GestureMode.DRAGGING }?.item?.index
        renderedItems.forEach { item ->
            if (
                draggingIndex == item.index ||
                currentGesture?.targetIndex == item.index ||
                (currentGesture != null &&
                    currentGesture.mode == GestureMode.HANDLE_PENDING &&
                    currentGesture.item.index == item.index)
            ) {
                canvas.drawRoundRect(item.itemRect, 16f * density, 16f * density, dragHighlightPaint)
            }
            drawHandle(canvas, item)
            drawRemoveButton(canvas, item)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val item = findItemAt(event.x, event.y) ?: return false
                parent?.requestDisallowInterceptTouchEvent(true)
                beginGesture(item, event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val gesture = activeGesture ?: return false
                gesture.lastX = event.x
                gesture.lastY = event.y

                when (gesture.mode) {
                    GestureMode.DRAGGING -> {
                        gesture.targetIndex = findNearestIndex(event.y)
                        invalidate()
                        return true
                    }

                    GestureMode.TOUCH_PASSTHROUGH -> {
                        listener?.onQueueTouchPassthroughMove(event.x, event.y)
                        return true
                    }

                    GestureMode.HANDLE_PENDING -> {
                        if (hasMovedEnough(gesture, event.x, event.y)) {
                            cancelLongPress(gesture)
                            gesture.mode = GestureMode.TOUCH_PASSTHROUGH
                            listener?.onQueueTouchPassthroughStart(gesture.downX, gesture.downY)
                            listener?.onQueueTouchPassthroughMove(event.x, event.y)
                        }
                        return true
                    }

                    GestureMode.REMOVE_PENDING -> {
                        if (hasMovedEnough(gesture, event.x, event.y) &&
                            !gesture.item.removeHitRect.contains(event.x, event.y)
                        ) {
                            clearGesture()
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                val gesture = activeGesture ?: return false
                finishGesture(event.x, event.y, cancelled = false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (activeGesture != null) {
                    finishGesture(activeGesture?.lastX ?: 0f, activeGesture?.lastY ?: 0f, cancelled = true)
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    private fun rebuildLayout() {
        val snapshot = latestSnapshot
        if (snapshot == null || width <= 0 || height <= 0 || snapshot.items.isEmpty()) {
            renderedItems = emptyList()
            clearGesture()
            visibility = INVISIBLE
            invalidate()
            return
        }

        val scaleX = width / snapshot.viewportWidth
        val scaleY = height / snapshot.viewportHeight
        renderedItems =
            snapshot.items.mapNotNull { item ->
                val itemRect = item.itemRect.toViewRect(scaleX, scaleY)
                if (itemRect.width() <= 1f || itemRect.height() <= 1f) {
                    return@mapNotNull null
                }

                val handleRect = buildHandleRect(itemRect)
                val removeRect = buildRemoveRect(itemRect)

                RenderedQueueItem(
                    index = item.index,
                    fingerprint = item.fingerprint,
                    itemRect = itemRect,
                    dragRect = item.dragRect?.toViewRect(scaleX, scaleY),
                    handleRect = handleRect,
                    handleHitRect = expandRectWithinBounds(handleRect, itemRect, handleHitWidth, itemRect.height()),
                    removeRect = removeRect,
                    removeHitRect = expandRectWithinBounds(removeRect, itemRect, minControlHitSize, minControlHitSize),
                )
            }
        visibility = if (renderedItems.isEmpty()) INVISIBLE else VISIBLE
        invalidate()
    }

    private fun buildHandleRect(itemRect: RectF): RectF {
        val margin = 8f * density
        val width = min(handleVisualWidth, max(34f * density, itemRect.width() * 0.16f))
        val height = min(54f * density, max(40f * density, itemRect.height() - (12f * density)))
        val top = itemRect.centerY() - (height / 2f)
        return RectF(
            itemRect.left + margin,
            top,
            itemRect.left + margin + width,
            top + height,
        )
    }

    private fun buildRemoveRect(itemRect: RectF): RectF {
        val margin = 10f * density
        val size = min(56f * density, max(minControlVisualSize, itemRect.height() - (12f * density)))
        val left = max(itemRect.left + margin, itemRect.right - margin - size)
        val top = itemRect.centerY() - (size / 2f)
        return RectF(left, top, left + size, top + size)
    }

    private fun expandRectWithinBounds(
        rect: RectF,
        bounds: RectF,
        minWidth: Float,
        minHeight: Float,
    ): RectF {
        val width = min(bounds.width(), max(minWidth, rect.width()))
        val height = min(bounds.height(), max(minHeight, rect.height()))
        val maxLeft = max(bounds.left, bounds.right - width)
        val maxTop = max(bounds.top, bounds.bottom - height)
        val left = (rect.centerX() - (width / 2f)).coerceIn(bounds.left, maxLeft)
        val top = (rect.centerY() - (height / 2f)).coerceIn(bounds.top, maxTop)
        return RectF(left, top, left + width, top + height)
    }

    private fun drawRemoveButton(canvas: Canvas, item: RenderedQueueItem) {
        canvas.drawOval(item.removeRect, removeFillPaint)
        val inset = item.removeRect.width() * 0.32f
        canvas.drawLine(
            item.removeRect.left + inset,
            item.removeRect.top + inset,
            item.removeRect.right - inset,
            item.removeRect.bottom - inset,
            removeStrokePaint,
        )
        canvas.drawLine(
            item.removeRect.right - inset,
            item.removeRect.top + inset,
            item.removeRect.left + inset,
            item.removeRect.bottom - inset,
            removeStrokePaint,
        )
    }

    private fun drawHandle(canvas: Canvas, item: RenderedQueueItem) {
        canvas.drawRoundRect(item.handleRect, 18f * density, 18f * density, handleFillPaint)
        val centerX = item.handleRect.centerX()
        val centerY = item.handleRect.centerY()
        val spacing = 6f * density
        val halfHeight = 10f * density
        canvas.drawLine(centerX - spacing, centerY - halfHeight, centerX - spacing, centerY + halfHeight, handlePaint)
        canvas.drawLine(centerX + spacing, centerY - halfHeight, centerX + spacing, centerY + halfHeight, handlePaint)
    }

    private fun beginGesture(item: RenderedQueueItem, x: Float, y: Float) {
        clearGesture()
        val hitRemoveButton = item.removeHitRect.contains(x, y)
        val hitHandle = item.handleHitRect.contains(x, y)
        val gesture =
            ActiveGesture(
                item = item,
                downX = x,
                downY = y,
                lastX = x,
                lastY = y,
                hitRemoveButton = hitRemoveButton,
                hitHandle = hitHandle,
                mode =
                    when {
                        hitRemoveButton -> GestureMode.REMOVE_PENDING
                        hitHandle -> GestureMode.HANDLE_PENDING
                        else -> GestureMode.TOUCH_PASSTHROUGH
                    },
            )
        activeGesture = gesture
        when (gesture.mode) {
            GestureMode.REMOVE_PENDING -> Unit
            GestureMode.HANDLE_PENDING -> {
                gesture.longPressRunnable =
                    Runnable {
                        val current = activeGesture ?: return@Runnable
                        if (current !== gesture || current.mode != GestureMode.HANDLE_PENDING) {
                            return@Runnable
                        }

                        current.mode = GestureMode.DRAGGING
                        current.targetIndex = current.item.index
                        listener?.onQueueLongPress(current.item.index)
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        invalidate()
                    }.also { postDelayed(it, longPressTimeoutMillis) }
            }
            GestureMode.TOUCH_PASSTHROUGH -> {
                listener?.onQueueTouchPassthroughStart(x, y)
            }
            GestureMode.DRAGGING -> Unit
        }
        invalidate()
    }

    private fun finishGesture(x: Float, y: Float, cancelled: Boolean) {
        val gesture = activeGesture ?: return
        cancelLongPress(gesture)

        when (gesture.mode) {
            GestureMode.DRAGGING -> {
                if (!cancelled) {
                    val targetIndex = gesture.targetIndex
                    if (targetIndex != null && targetIndex != gesture.item.index) {
                        listener?.onQueueDragRequested(gesture.item.index, targetIndex, gesture.item.fingerprint)
                    }
                }
            }
            GestureMode.TOUCH_PASSTHROUGH -> {
                listener?.onQueueTouchPassthroughEnd(x, y, cancelled)
            }
            GestureMode.REMOVE_PENDING -> {
                if (!cancelled && gesture.hitRemoveButton && gesture.item.removeHitRect.contains(x, y)) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    listener?.onQueueRemoveRequested(gesture.item.index, gesture.item.fingerprint)
                }
            }
            GestureMode.HANDLE_PENDING -> Unit
        }

        clearGesture()
    }

    private fun clearGesture() {
        activeGesture?.let(::cancelLongPress)
        activeGesture = null
        invalidate()
    }

    private fun cancelLongPress(gesture: ActiveGesture) {
        gesture.longPressRunnable?.let(::removeCallbacks)
        gesture.longPressRunnable = null
    }

    private fun hasMovedEnough(gesture: ActiveGesture, x: Float, y: Float): Boolean {
        return abs(x - gesture.downX) > touchSlop || abs(y - gesture.downY) > touchSlop
    }

    private fun findItemAt(x: Float, y: Float): RenderedQueueItem? {
        return renderedItems.asReversed().firstOrNull { it.itemRect.contains(x, y) }
    }

    private fun findNearestIndex(y: Float): Int? {
        if (renderedItems.isEmpty()) {
            return null
        }

        return renderedItems.minByOrNull { item ->
            abs(item.itemRect.centerY() - y)
        }?.index
    }

    private fun com.ytmusic.pro.web.webapp.QueueRect.toViewRect(scaleX: Float, scaleY: Float): RectF {
        return RectF(
            left * scaleX,
            top * scaleY,
            (left + width) * scaleX,
            (top + height) * scaleY,
        )
    }

    private data class RenderedQueueItem(
        val index: Int,
        val fingerprint: String?,
        val itemRect: RectF,
        val dragRect: RectF?,
        val handleRect: RectF,
        val handleHitRect: RectF,
        val removeRect: RectF,
        val removeHitRect: RectF,
    )

    private data class ActiveGesture(
        val item: RenderedQueueItem,
        val downX: Float,
        val downY: Float,
        var lastX: Float,
        var lastY: Float,
        val hitRemoveButton: Boolean,
        val hitHandle: Boolean,
        var mode: GestureMode = GestureMode.REMOVE_PENDING,
        var targetIndex: Int? = null,
        var longPressRunnable: Runnable? = null,
    )

    private enum class GestureMode {
        REMOVE_PENDING,
        HANDLE_PENDING,
        TOUCH_PASSTHROUGH,
        DRAGGING,
    }
}

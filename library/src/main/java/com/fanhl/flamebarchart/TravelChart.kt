package com.fanhl.flamebarchart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.annotation.ColorInt
import android.support.annotation.Dimension
import android.support.v4.content.ContextCompat
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import com.fanhl.util.ColorUtils
import com.fanhl.util.CompatibleHelper
import com.fanhl.widget.OverScroller
import java.util.*


/**
 * 数据图表
 *
 * @author fanhl
 */
class TravelChart @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    /** 绘制x轴的labels */
    private val xLabelPaint by lazy { TextPaint() }
    /** 顶部提示文字的宽度 */
    private val xHintPaint by lazy { TextPaint() }
    private val xHintAlphaGradientInterpolator by lazy { AccelerateDecelerateInterpolator() }

    private val scroller by lazy { OverScroller(context, null, true, SnapSplineOverScroller(context)) }

    private var mIsBeingDragged = false
    /** 速度管理 */
    private val velocityTracker by lazy { VelocityTracker.obtain() }

    private var mTouchSlop: Int = 0
    private var mMinimumVelocity: Int = 0
    private var mMaximumVelocity: Int = 0
    private var mOverscrollDistance: Int = 0

    private var mScrollX: Int
        get() {
            return calculationScrollX(currentXAxis, currentXAxisOffsetPercent)
        }
        set(value) {
            val (centerX, centerXOffset) = calculationCenterX(value)
            this.currentXAxis = centerX
            this.currentXAxisOffsetPercent = centerXOffset
        }
    private var mScrollY: Int
        get() {
            return 0
        }
        set(value) {
        }

    // --------------------------------- 输入 ---------------------------

    /** 柱宽 */
    var barWidth = 0
        set(value) {
            if (field == value) return

            field = value
            barWidthHalf = value / 2
        }
    /** 柱间距 */
    var barInterval = 0
    /** 柱子的背景图 */
    var barDrawableDefault: Drawable? = null
    var barDrawablePressed: Drawable? = null
    var barDrawableFocused: Drawable? = null

    /** bar顶部的提示内容的上下padding */
    var barHintPadding = 0
    /** bar顶部背景图 */
    var barHintBackground: Drawable? = null
    /** bar的背景图案中文字的padding */
    var barHintBackgroundPadding = 0
        set(value) {
            field = value
            barHintBackgroundPaddingLeft = value
            barHintBackgroundPaddingTop = value
            barHintBackgroundPaddingRight = value
            barHintBackgroundPaddingBottom = value
        }
    var barHintBackgroundPaddingLeft = 0
    var barHintBackgroundPaddingTop = 0
    var barHintBackgroundPaddingRight = 0
    var barHintBackgroundPaddingBottom = 0
    @Dimension
    var barHintTextSize = 0f
    @ColorInt
    var barHintTextColor = 0

    /** x轴的上下padding */
    var xAxisPadding = 0
    /** x轴的中心的背景图案 */
    var xAxisCurrentBackground: Drawable? = null
        set(value) {
            if (field == value) return
            field = value
        }
    /** x轴的中心的背景图案中文字的padding */
    var xAxisCurrentBackgroundPadding = 0
    @Dimension
    var xLabelTextSize = 0f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }
    @ColorInt
    var xLabelTextColor = 0
    @ColorInt
    var xLabelTextColorFocused = 0

    var data: DefaultData<*>? = null
        set(value) {
            field = value
            invalidate()
        }

    // --------------------------------- 运算 ---------------------------------

    private var barWidthHalf = 0

    /** bar顶部的提示内容文字的高度 */
    private var barHintContentHeight = 0
    /** 绘制x轴的内容的高度 */
    private var xAxisContentHeight = 0

    /** 当前居中的x轴值 */
    private var currentXAxis = 0
    /** 当前居中偏移值 (-0.5,0.5] */
    private var currentXAxisOffsetPercent = 0f

    /**
     * Position of the last motion event.
     */
    private var mLastMotionX: Int = 0

    /** 存放xLabel等的尺寸 */
    private var textBounds = Rect()

    init {
        val configuration = ViewConfiguration.get(context)
        mTouchSlop = configuration.scaledTouchSlop
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity
        mOverscrollDistance = configuration.scaledOverscrollDistance
        // see more config : /android/widget/HorizontalScrollView.java:222


        val resources = context.resources
        val a = context.obtainStyledAttributes(attrs, R.styleable.TravelChart, defStyleAttr, R.style.Widget_Travel_Chart)

        barWidth = a.getDimensionPixelOffset(R.styleable.TravelChart_barWidth, resources.getDimensionPixelOffset(R.dimen.bar_width))
        barInterval = a.getDimensionPixelOffset(R.styleable.TravelChart_barInterval, resources.getDimensionPixelOffset(R.dimen.bar_interval))
        barDrawableDefault = a.getDrawable(R.styleable.TravelChart_barDrawableDefault) ?: ContextCompat.getDrawable(context, R.drawable.bar_drawable_default)
        barDrawablePressed = a.getDrawable(R.styleable.TravelChart_barDrawablePressed) ?: ContextCompat.getDrawable(context, R.drawable.bar_drawable_pressed)
        barDrawableFocused = a.getDrawable(R.styleable.TravelChart_barDrawableFocused) ?: ContextCompat.getDrawable(context, R.drawable.bar_drawable_focused)

        barHintPadding = a.getDimensionPixelOffset(R.styleable.TravelChart_barHintPadding, resources.getDimensionPixelOffset(R.dimen.bar_hint_padding))
        barHintBackground = a.getDrawable(R.styleable.TravelChart_barHintBackground) ?: ContextCompat.getDrawable(context, R.drawable.bar_hint_background)
        barHintBackgroundPadding = a.getDimensionPixelOffset(R.styleable.TravelChart_barHintBackgroundPadding, resources.getDimensionPixelOffset(R.dimen.bar_hint_background_padding))
        barHintBackgroundPaddingLeft = a.getDimensionPixelOffset(R.styleable.TravelChart_barHintBackgroundPaddingLeft, resources.getDimensionPixelOffset(R.dimen.bar_hint_background_padding))
        barHintBackgroundPaddingTop = a.getDimensionPixelOffset(R.styleable.TravelChart_barHintBackgroundPaddingTop, resources.getDimensionPixelOffset(R.dimen.bar_hint_background_padding))
        barHintBackgroundPaddingRight = a.getDimensionPixelOffset(R.styleable.TravelChart_barHintBackgroundPaddingRight, resources.getDimensionPixelOffset(R.dimen.bar_hint_background_padding))
        barHintBackgroundPaddingBottom = a.getDimensionPixelOffset(R.styleable.TravelChart_barHintBackgroundPaddingBottom, resources.getDimensionPixelOffset(R.dimen.bar_hint_background_padding))
        barHintTextSize = a.getDimension(R.styleable.TravelChart_barHintTextSize, resources.getDimension(R.dimen.bar_hint_text_size))
        barHintTextColor = a.getColor(R.styleable.TravelChart_barHintTextColor, ContextCompat.getColor(context, R.color.bar_hint_text_color))

        xAxisPadding = a.getDimensionPixelOffset(R.styleable.TravelChart_xAxisPadding, resources.getDimensionPixelOffset(R.dimen.x_axis_padding))
        xAxisCurrentBackground = a.getDrawable(R.styleable.TravelChart_xAxisCurrentBackground) ?: ContextCompat.getDrawable(context, R.drawable.x_axis_current_background)
        xAxisCurrentBackgroundPadding = a.getDimensionPixelOffset(R.styleable.TravelChart_xAxisCurrentBackgroundPadding, resources.getDimensionPixelOffset(R.dimen.x_axis_current_background_padding))
        xLabelTextSize = a.getDimension(R.styleable.TravelChart_xLabelTextSize, resources.getDimension(R.dimen.x_label_text_size))
        xLabelTextColor = a.getColor(R.styleable.TravelChart_xLabelTextColor, ContextCompat.getColor(context, R.color.x_label_text_color))
        xLabelTextColorFocused = a.getColor(R.styleable.TravelChart_xLabelTextColorFocused, ContextCompat.getColor(context, R.color.x_label_text_color_focused))

        a.recycle()

        //x轴label的绘制相关
        xLabelPaint.textAlign = Paint.Align.CENTER
        xLabelPaint.textSize = xLabelTextSize
        xLabelPaint.color = xLabelTextColor

        xHintPaint.textAlign = Paint.Align.CENTER
        xHintPaint.textSize = barHintTextSize
        xHintPaint.color = barHintTextColor

        if (isInEditMode) {
            val random = Random()
            data = DefaultData<DefaultItem>().apply {
                (1..20).forEach { list.add(DefaultItem(it, random.nextFloat())) }
            }
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()

        // FIXME: 2018/6/5 fanhl

    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        velocityTracker.addMovement(ev)

        val action = ev?.action ?: return false

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                //只有一个元素也没办法滚动
                if (getChildCount() <= 1) {
                    return false
                }

                mIsBeingDragged = !scroller.isFinished
                if (mIsBeingDragged) {
                    this.parent?.requestDisallowInterceptTouchEvent(true)
                }

                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }

                mLastMotionX = ev.x.toInt()
            }
            MotionEvent.ACTION_MOVE -> {
                val x = ev.x.toInt()
                var deltaX = mLastMotionX - x
                if (!mIsBeingDragged && Math.abs(deltaX) > mTouchSlop) {
                    val parent = parent
                    parent?.requestDisallowInterceptTouchEvent(true)
                    mIsBeingDragged = true
                    if (deltaX > 0) {
                        deltaX -= mTouchSlop
                    } else {
                        deltaX += mTouchSlop
                    }
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    mLastMotionX = x

                    val oldX = mScrollX
                    val oldY = 0
                    val range = getScrollRange()
                    val canOverscroll = overScrollMode == View.OVER_SCROLL_ALWAYS || overScrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0

                    // Calling overScrollBy will call onOverScrolled, which
                    // calls onScrollChanged if applicable.
                    if (overScrollBy(deltaX, 0, mScrollX, 0, range, 0, mOverscrollDistance, 0, true)) {
                        // Break our velocity if we hit a scroll barrier.
                        velocityTracker.clear()
                    }

                    if (canOverscroll) {
                        val pulledToX = oldX + deltaX
                        if (pulledToX < 0) {
                            // 边缘效果
//                            mEdgeGlowLeft.onPull(deltaX.toFloat() / width, 1f - ev.getY(activePointerIndex) / height)
//                            if (!mEdgeGlowRight.isFinished()) {
//                                mEdgeGlowRight.onRelease()
//                            }
                        } else if (pulledToX > range) {
                            // 边缘效果
//                            mEdgeGlowRight.onPull(deltaX.toFloat() / width, ev.getY(activePointerIndex) / height)
//                            if (!mEdgeGlowLeft.isFinished()) {
//                                mEdgeGlowLeft.onRelease()
//                            }
                        }
//                        if (mEdgeGlowLeft != null && (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished())) {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//                                postInvalidateOnAnimation()
//                            } else {
//                                postInvalidate()
//                            }
//                        }
                    }

                    if (oldX <= 0 && deltaX < 0) {
                        return false
                    } else if (oldX >= getScrollRange() && deltaX > 0) {
                        return false
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mIsBeingDragged) {
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                    val initialVelocity = velocityTracker.xVelocity.toInt()

                    if (getChildCount() > 1) {
                        if (Math.abs(initialVelocity) > mMinimumVelocity) {
                            fling(-initialVelocity)
                        } else {
                            if (scroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0)) {
                                CompatibleHelper.postInvalidateOnAnimation(this)
                            }
                        }
                    }

//                    mActivePointerId = INVALID_POINTER
                    mIsBeingDragged = false
                    recycleVelocityTracker()

//                    if (mEdgeGlowLeft != null) {
//                        mEdgeGlowLeft.onRelease()
//                        mEdgeGlowRight.onRelease()
//                    }

                    if (scroller.isFinished && Math.abs(currentXAxisOffsetPercent) > 0f) {
                        changeCurrentXAxis(currentXAxis)
                    }
                }
                //暂时防warning
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (mIsBeingDragged && getChildCount() > 1) {
                    if (scroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0)) {
                        CompatibleHelper.postInvalidateOnAnimation(this)
                    }
//                    mActivePointerId = INVALID_POINTER
                    mIsBeingDragged = false
                    recycleVelocityTracker()

//                    if (mEdgeGlowLeft != null) {
//                        mEdgeGlowLeft.onRelease()
//                        mEdgeGlowRight.onRelease()
//                    }

                    if (scroller.isFinished && Math.abs(currentXAxisOffsetPercent) > 0f) {
                        changeCurrentXAxis(currentXAxis)
                    }
                }
            }
        }

        return true
    }

    /**
     * Fling the scroll vie
     *
     * @param velocityX The initial velocity in the X direction. Positive
     * numbers mean that the finger/cursor is moving down the screen,
     * which means we want to scroll towards the left.
     */
    private fun fling(velocityX: Int) {
        if (getChildCount() > 0) {
            val width = width - paddingRight - paddingLeft
//            val right = getScrollRange()

            //注意，这里划到结束要直接滑动到对应xAxis,没有currentXAxisOffsetPercent
//            scroller.fling(mScrollX, mScrollY, velocityX, 0, 0, getScrollRange(), 0, 0, width / 2, 0)
            scroller.fling(mScrollX, mScrollY, velocityX, 0, 0, getScrollRange(), 0, 0, barWidth + barInterval, 0)

//            val dx: Int = (ScrollerUtils.getSplineFlingDistance(velocityX) * Math.signum(velocityX.toDouble())).toInt()
//            val duration = ScrollerUtils.getSplineFlingDuration(velocityX)
//            scroller.startScroll(mScrollX, mScrollY, dx, 0, duration)

            val movingRight = velocityX > 0

            val currentFocused = findFocus()
            var newFocused: View? = null// findFocusableViewInMyBounds(movingRight, scroller.finalX, currentFocused)

            if (newFocused == null) {
                newFocused = this
            }

            if (newFocused !== currentFocused) {
                newFocused.requestFocus(if (movingRight) View.FOCUS_RIGHT else View.FOCUS_LEFT)
            }

            CompatibleHelper.postInvalidateOnAnimation(this)
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        if (mScrollX != x || 0 != y) {
            val oldX = mScrollX
            val oldY = mScrollY
            mScrollX = x
            mScrollY = y
//            invalidateParentCaches()
            onScrollChanged(mScrollX, mScrollY, oldX, oldY)
            if (!awakenScrollBars()) {
                CompatibleHelper.postInvalidateOnAnimation(this)
            }
        }
    }

    override fun computeScroll() {
        //先判断mScroller滚动是否完成
        if (scroller.computeScrollOffset()) {

            //这里调用View的scrollTo()完成实际的滚动
//            scrollTo(scroller.currX, scroller.currY)
            val (centerX, centerXOffset) = calculationCenterX(scroller.currX)
            this.currentXAxis = centerX
            this.currentXAxisOffsetPercent = centerXOffset

            //必须调用该方法，否则不一定能看到滚动效果
            CompatibleHelper.postInvalidateOnAnimation(this)
        }
        super.computeScroll()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val validWidth = width - paddingLeft - paddingRight
        val validHeight = height - paddingTop - paddingBottom

        val saveCount = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingRight.toFloat())

        drawValid(canvas, validWidth, validHeight)

        canvas.restoreToCount(saveCount)
    }

    /**
     * 绘制有效区域
     */
    private fun drawValid(canvas: Canvas, validWidth: Int, validHeight: Int) {
        val barHintTop = barHintPadding
//        val barHintBottom = barHintTop + barHintContentHeight
        var barHintLeft = 0
        val barHintRight = 0

        val barsPaddingTop = barHintPadding + barHintContentHeight + barHintPadding
        val barsPaddingBottom = xAxisPadding + xAxisContentHeight + xAxisPadding

        val barsPaddingLeft = 0
        val barsPaddingRight = 0

        val barsWidth = validWidth - barsPaddingLeft - barsPaddingRight
        val barsHeight = validHeight - barsPaddingTop - barsPaddingBottom


        val barsSaveCount = canvas.save()
        canvas.translate(barsPaddingLeft.toFloat(), barsPaddingTop.toFloat())

        drawBars(canvas, barsWidth, barsHeight)

        canvas.restoreToCount(barsSaveCount)

        val xAxisPaddingTop = validHeight - xAxisPadding - xAxisContentHeight
//        val xAxisPaddingBottom = xAxisPadding
        val xAxisPaddingLeft = 0
        val xAxisPaddingRight = 0

        val xAxisWidth = validWidth - xAxisPaddingLeft - xAxisPaddingRight
        val xAxisHeight = xAxisContentHeight// validHeight - xAxisPaddingTop - xAxisPaddingBottom

        val xAxisSaveCount = canvas.save()
        canvas.translate(xAxisPaddingLeft.toFloat(), xAxisPaddingTop.toFloat())

        drawXAxis(canvas, xAxisWidth, xAxisHeight)

        canvas.restoreToCount(xAxisSaveCount)

        val barHintWidth = validWidth - barHintLeft - barHintRight
        val barHintHeight = barHintContentHeight

        val barHintSaveCount = canvas.save()
        canvas.translate(barHintLeft.toFloat(), barHintTop.toFloat())

        // 注意 这个 barHint 会跟随 bar 的高度变化
        drawBarHint(canvas, barHintWidth, barHintHeight, barsHeight)

        canvas.restoreToCount(barHintSaveCount)
    }

    /**
     * 绘制柱状态图区域
     */
    private fun drawBars(canvas: Canvas, barsWidth: Int, barsHeight: Int) {
        val horizontalMidpoint = barsWidth / 2

        forEachValid(data, barsWidth) { index, item ->
            (if (index == currentXAxis) {
                barDrawableFocused
            } else if (false) {
                // FIXME: 2018/6/4 fanhl
                barDrawablePressed
            } else {
                barDrawableDefault
            })?.apply {
                val barCenterX = horizontalMidpoint + ((index - currentXAxis - currentXAxisOffsetPercent) * (barWidth + barInterval)).toInt()
                setBounds(
                        barCenterX - barWidthHalf,
                        (barsHeight * (1 - item.getYAxis())).toInt(),
                        barCenterX + barWidthHalf,
                        barsHeight
                )
                draw(canvas)
            }
        }
    }

    /**
     * 绘制x轴
     */
    private fun drawXAxis(canvas: Canvas, xAxisWidth: Int, xAxisHeight: Int) {
        val horizontalMidpoint = xAxisWidth / 2
        val verticalMidpoint = xAxisHeight / 2

        //绘制水平居中的背景框
        data?.list?.takeIf { it.isNotEmpty() }?.apply {
            //当前要显示在居中背景中的元素的索引
            val currentIndex = minOf(maxOf(0, currentXAxis), size - 1)
            //上一个在居中背景中显示的元素的索引 , 若无此元素则显示-1
            val previousIndex = when {
                currentXAxisOffsetPercent > 0f -> (currentIndex + 1).takeIf { it < size } ?: -1
                currentXAxisOffsetPercent < 0f -> currentIndex - 1
                else -> -1
            }

            //注：这里要加一个动态渐变

            val currentXLabel = get(currentIndex).getXLabel()
            xLabelPaint.getTextBounds(currentXLabel, 0, currentXLabel.length, textBounds)

            val currentLabelWidth = textBounds.width()
            val previousLabelWidth = if (previousIndex >= 0) {
                val previousXLabel = get(previousIndex).getXLabel()
                xLabelPaint.getTextBounds(previousXLabel, 0, previousXLabel.length, textBounds)
                textBounds.right - textBounds.left
            } else -1

            //获取两个宽度之间的某个宽度
            val labelWidthGradient = if (previousLabelWidth >= 0) {
                (currentLabelWidth * (1 - Math.abs(currentXAxisOffsetPercent)) + previousLabelWidth * Math.abs(currentXAxisOffsetPercent)).toInt()
            } else currentLabelWidth

            var currentBgWidth = xAxisCurrentBackgroundPadding + labelWidthGradient + xAxisCurrentBackgroundPadding
            val currentBgHeight = xAxisContentHeight

            //中间的背景的宽度不能小于高度（保持至少为圆）
            currentBgWidth = maxOf(currentBgWidth, currentBgHeight)
            //先画 居中的当前的背景
            xAxisCurrentBackground?.apply {
                setBounds(
                        horizontalMidpoint - currentBgWidth / 2,
                        verticalMidpoint - currentBgHeight / 2,
                        horizontalMidpoint + currentBgWidth / 2,
                        verticalMidpoint + currentBgHeight / 2
                )
                draw(canvas)
            }
        }
        //绘制 xLabels

        forEachValid(data, xAxisWidth) { index, item ->
            val textCenterX = horizontalMidpoint + (index - currentXAxis - currentXAxisOffsetPercent) * (barWidth + barInterval)
            val textCenterY = verticalMidpoint - ((xLabelPaint.descent() + xLabelPaint.ascent()) / 2)

            //颜色渐变
            // 为0时,颜色为xLabelTextColorFocused，为1时颜色为xLabelTextColor，为(0,1)之间时，取这两者之间的值
            val xLabelPaintColorGradientPercent = Math.min(Math.abs(index - currentXAxis - currentXAxisOffsetPercent), 1f)
            val xLabelColor = ColorUtils.getColorGradient(xLabelTextColorFocused, xLabelTextColor, xLabelPaintColorGradientPercent)

            xLabelPaint.color = xLabelColor

            canvas.drawText(item.getXLabel(), textCenterX, textCenterY, xLabelPaint)
        }
    }

    /**
     * 绘制顶部提示区域
     */
    private fun drawBarHint(canvas: Canvas, barHintWidth: Int, barHintHeight: Int, barsHeight: Int) {
        val horizontalMidpoint = barHintWidth / 2
        val verticalMidpoint = barHintHeight / 2

        //绘制水平居中提示
        data?.list?.takeIf { it.isNotEmpty() }?.apply {
            //当前要显示在居中背景中的元素的索引
            val currentIndex = minOf(maxOf(0, currentXAxis), size - 1)
            val currentItem = get(currentIndex)
            val x = horizontalMidpoint + (currentIndex - currentXAxis - currentXAxisOffsetPercent) * (barWidth + barInterval)

            val y = verticalMidpoint + (barsHeight * (1 - currentItem.getYAxis()))
            val yText = (barHintBackgroundPaddingTop + (barHintContentHeight - barHintBackgroundPaddingTop - barHintBackgroundPaddingBottom) / 2) + (barsHeight * (1 - currentItem.getYAxis()))

            val currentXLabel = currentItem.getXHint()
            xHintPaint.getTextBounds(currentXLabel, 0, currentXLabel.length, textBounds)

            val currentLabelWidth = textBounds.width()

            var currentBgWidth = barHintBackgroundPaddingLeft + currentLabelWidth + barHintBackgroundPaddingRight
            val currentBgHeight = barHintContentHeight

            //中间的背景的宽度不能小于高度（保持至少为圆）
            currentBgWidth = maxOf(currentBgWidth, currentBgHeight)

            val offsetAlpha = xHintAlphaGradientInterpolator.getInterpolation(if (currentXAxis < 0 || (currentXAxis <= 0 && currentXAxisOffsetPercent < 0f)) {
                //超过两端时不隐藏
                1f
            } else if (currentXAxis > size - 1 || (currentXAxis >= size - 1 && currentXAxisOffsetPercent > 0f)) {
                //超过两端时不隐藏
                1f
            } else {
                1 - 2 * Math.abs(currentXAxisOffsetPercent)
            })
            val velocityAlpha = xHintAlphaGradientInterpolator.getInterpolation(if (currentXAxis < 0 || (currentXAxis <= 0 && currentXAxisOffsetPercent < 0f)) {
                //超过两端时不隐藏
                1f
            } else if (currentXAxis > size - 1 || (currentXAxis >= size - 1 && currentXAxisOffsetPercent > 0f)) {
                //超过两端时不隐藏
                1f
            } else {
                minOf(maxOf(2 - Math.abs(scroller.currVelocity / BAR_HINT_VISIBLE_THRESHOLD), 0f), 1f)
            })

//            Log.d("TravelChart", "drawBarHint: scroller.currVelocity:${scroller.currVelocity} mScrollX:$mScrollX currentXAxis:$currentXAxis currentXAxisOffsetPercent:$currentXAxisOffsetPercent offsetAlpha:$offsetAlpha")

            val alpha = (offsetAlpha * velocityAlpha * 255).toInt()

            //绘制背景
            barHintBackground?.apply {
                this.alpha = alpha
                setBounds(
                        (x - currentBgWidth / 2).toInt(),
                        (y - currentBgHeight / 2).toInt(),
                        (x + currentBgWidth / 2).toInt(),
                        (y + currentBgHeight / 2).toInt()
                )
                draw(canvas)
            }
            xHintPaint.alpha = alpha
            canvas.drawText(currentXLabel, x, yText - ((xHintPaint.descent() + xHintPaint.ascent()) / 2), xHintPaint)
        }
    }


    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // Calling this with the present values causes it to re-claim them
        scrollTo(mScrollX, 0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        xLabelPaint.getTextBounds(X_LABEL_DEFAULT, 0, X_LABEL_DEFAULT.length, textBounds)
        xAxisContentHeight = xAxisCurrentBackgroundPadding + (textBounds.bottom - textBounds.top) + xAxisCurrentBackgroundPadding
        xHintPaint.getTextBounds(X_LABEL_DEFAULT, 0, X_LABEL_DEFAULT.length, textBounds)
        barHintContentHeight = barHintBackgroundPaddingTop + (textBounds.bottom - textBounds.top) + barHintBackgroundPaddingBottom
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        // Treat animating scrolls differently; see #computeScroll() for why.
        if (!scroller.isFinished) {
            val oldX = mScrollX
            val oldY = 0
            mScrollX = scrollX
//            mScrollY = scrollY
//            invalidateParentIfNeeded()
            onScrollChanged(mScrollX, 0, oldX, oldY)
            if (clampedX) {
                scroller.springBack(mScrollX, 0, 0, getScrollRange(), 0, 0)
            }
        } else {
//            super.scrollTo(scrollX, scrollY)
            scrollTo(scrollX, scrollY)
        }

        awakenScrollBars()
    }

    private fun getChildCount(): Int {
        return data?.list?.size ?: 0
    }

    private fun getScrollRange(): Int {
        return data?.list?.size?.takeIf { it > 0 }?.let {
            (it - 1) * (barWidth + barInterval)
        } ?: 0
    }

    private fun recycleVelocityTracker() {
        //这里临时改用 by lazy (为啥要recycle啊？)
//        velocityTracker.recycle()
    }

    /**
     * 根据centerX与centerXOffset计算出scrollX
     */
    private fun calculationScrollX(centerX: Int, centerXOffset: Float): Int {
        return ((centerX + centerXOffset) * (barWidth + barInterval)).toInt()
    }

    /**
     * 根据scrollX计算出centerX与centerXOffset
     */
    private fun calculationCenterX(scrollX: Int): Pair<Int, Float> {
        var centerX = scrollX / (barWidth + barInterval)
        var centerXOffset = (scrollX % (barWidth + barInterval)).toFloat() / (barWidth + barInterval)
        //注意 centerXOffset 的 值在 区间 (-0.5,0.5]中
        if (centerXOffset > 0.5f) {
            centerX += 1
            centerXOffset -= 1
        }

        return Pair(centerX, centerXOffset)
    }

    /**
     * 对 data中 在validWidth有效绘制区域内的item进行遍历处理
     */
    private fun forEachValid(data: DefaultData<*>?, validWidth: Int, block: (Int, IItem) -> Unit) {
        data?.apply {
            if (list.isEmpty()) {
                return
            }

            val indexStart = getValidIndexStart(validWidth)
            val indexEnd = getValidIndexEnd(validWidth, list.size)

            (indexStart..indexEnd).forEach { index ->
                block(index, list[index])
            }
        }
    }

    /**
     * 获取有效的（在绘制区域内的）data中list的起始index
     *
     * @param width 总绘制宽度
     */
    private fun getValidIndexStart(width: Int): Int {
        val horizontalMidpoint = width / 2

        //仅绘制在屏幕内的bar
        val indexStart = maxOf(
                (currentXAxis - (horizontalMidpoint + currentXAxisOffsetPercent * (barWidth + barInterval) + barWidth / 2) / (barWidth + barInterval)).toInt() - 1,
                0
        )
        return indexStart
    }

    /**
     * 获取有效的（在绘制区域内的）data中list的结束index
     *
     * @param width 总绘制宽度
     * @param listSize data中list的总长度
     */
    private fun getValidIndexEnd(width: Int, listSize: Int): Int {
        val horizontalMidpoint2 = width / 2
        val indexEnd = minOf(
                ((width - (horizontalMidpoint2 + currentXAxisOffsetPercent * (barWidth + barInterval) + barWidth / 2)) / (barWidth + barInterval) + currentXAxis).toInt() + 1,
                listSize - 1
        )
        return indexEnd
    }

    /**
     *设置当前的xAxis
     */
    private fun changeCurrentXAxis(xAxis: Int) {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }

        val startScrollX = calculationScrollX(this.currentXAxis, currentXAxisOffsetPercent)
        val endScrollX = calculationScrollX(xAxis, 0f)
        scroller.startScroll(startScrollX, 0, endScrollX - startScrollX, 0, AUTO_SCROLL_DURATION_DEFAULT)
        invalidate()
    }

    /**
     * 设置x值(设置当前元素显示第几个item)
     */
    fun setXAxis(xAxis: Int) {
        changeCurrentXAxis(xAxis)
    }

    companion object {
        private const val AUTO_SCROLL_DURATION_DEFAULT = 250

        private const val X_LABEL_DEFAULT = "Today"

        /**
         * barHint 是否显示 的 滑动速度阀值
         * 小于阀值是完全显示，大于2倍阀值时完全不显示
         * 在1倍至2倍区间中半透明显示
         */
        private const val BAR_HINT_VISIBLE_THRESHOLD = 100f
    }

    /**
     * TravelChart的图表的数据结构
     */
    interface IData {
        /**
         * 获取y轴最大坐标值
         */
        fun getYAxisMin(): Float

        /**
         * 获取y轴最大坐标值
         */
        fun getYAxisMax(): Float
    }

    /**
     * TravelChart的图表上关键点的数据结构
     */
    interface IItem {
        /**
         * 获取x轴Label的值
         */
        fun getXLabel(): String

        /**
         * 获取x轴对应项居中时顶部的提示文字
         */
        fun getXHint(): String

        /**
         * 获取y轴坐标值
         */
        fun getYAxis(): Float
    }

    /**
     * TravelChart要绘制的数据
     */
    class DefaultData<T : IItem> {
        val list = ArrayList<T>()
        // 添加数据时，判断数据是否在屏幕外，再决定是否 invalidate()
    }

    private data class DefaultItem(val x: Int, val y: Float) : IItem {
        override fun getXLabel(): String {
            return if (Math.abs(x - 15) <= 0.01f) "Today" else "$x"
        }

        override fun getXHint(): String {
            return "${(x * y * 100).toInt()}km"
        }

        override fun getYAxis(): Float {
            return y
        }
    }

    /**
     * 直接滚动到对应xAxis的scroller
     *
     * 这里划到结束要直接滑动到对应xAxis,没有currentXAxisOffsetPercent
     */
    inner class SnapSplineOverScroller(context: Context) : OverScroller.SplineOverScroller(context) {
        override fun getSplineFlingDistance(velocity: Int): Double {
            val splineFlingDistance = super.getSplineFlingDistance(velocity)
            val destX = mScrollX + splineFlingDistance * Math.signum(velocity.toFloat())

            val remainder = destX % (barWidth + barInterval)

            val offset = if (remainder > (barWidth + barInterval) / 2) {
                remainder - barWidth + barInterval
            } else {
                remainder
            }

            // FIXME: 2018/6/5 fanhl 这里滑动还是对不准

            Log.d("SnapSplineOverScroller", "getSplineFlingDistance: r0:${mScrollX.toDouble() / (barWidth + barInterval)} r1:${(destX - offset) / (barWidth + barInterval)}")
            return splineFlingDistance - offset
        }
    }
}
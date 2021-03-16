package com.junpu.oral.correct.correct

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.scaleMatrix
import com.junpu.log.L
import com.junpu.oral.correct.utils.resizeImage
import com.junpu.utils.isNotNullOrBlank
import kotlin.math.abs
import kotlin.math.min

/**
 * 批改
 * @author junpu
 * @date 2021/2/22
 */
class CorrectView : View, ScaleGestureDetector.OnScaleGestureListener {

    private val detector by lazy { ScaleGestureDetector(context, this) }

    // 作业
    private var bitmap: Bitmap? = null

    // 缩放参数
    private var scaleBaseValue = 0f // 每次缩放的基数
    private var scalePointX = 0f // 每次缩放中心点
    private var scalePointY = 0f // 每次缩放中心点

    // 拖动参数
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var moveValue = 10 // 触发移动最小距离
    private var continuousDrag = false // 触发持续拖动，第一次移动moveValue距离后触发持续拖动，直到手指离开屏幕

    private var mode = Mode.NONE // 当前模式
    private var touchMode = TouchMode.NONE // 当前touch模式

    private val curMatrix = Matrix() // 图片矩阵
    private val matrixValues = FloatArray(9) // 矩阵数组

    private var markManager = MarkManager(context)
    private var isDelMark = false // 是否为删除模式
    private var isDragMark = false // 是否为拖动模式

    private lateinit var getText: () -> String

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initBitmapConfig()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        markManager.release()
    }

    override fun onDraw(canvas: Canvas?) {
        canvas ?: return
        bitmap ?: return
        // 画图片
        canvas.drawBitmap(bitmap!!, curMatrix, null)
        // 画标记
        markManager.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return super.onTouchEvent(event)
        val x = event.x
        val y = event.y
        val pointerCount = event.pointerCount
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (mode == Mode.NONE) {
                    touchMode = TouchMode.DRAG
                } else {
                    // 触摸区域判定
                    when (markManager.checkTouchArea(x, y)) {
                        // 触摸到了删除按钮
                        MarkManager.TouchArea.DELETE -> {
                            isDelMark = true
                            markManager.removeMark()
                        }
                        // 触摸到了拖动按钮
                        MarkManager.TouchArea.DRAG -> {
                            isDragMark = true
                            markManager.lockMark(false)
                        }
                        // 触摸到了某个标记
                        MarkManager.TouchArea.MARK -> {
                            markManager.lockMark(true)
                        }
                        // 触摸到了空白区域
                        MarkManager.TouchArea.NONE -> {
                            var flag = true
                            when (mode) {
                                Mode.RIGHT -> markManager.generateRight(x, y)
                                Mode.WRONG -> markManager.generateWrong(x, y)
                                Mode.TEXT -> {
                                    val text = getText()
                                    if (text.isNotNullOrBlank())
                                        markManager.generateText(x, y, text)
                                    else
                                        flag = false
                                }
                                Mode.PEN -> markManager.generatePath(x, y)
                                else -> Unit
                            }
                            if (flag) markManager.lockMark(false)
                        }
                    }
                }
                lastMoveX = x
                lastMoveY = y
                if (mode != Mode.NONE) invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val tx = x - lastMoveX
                val ty = y - lastMoveY
                // 拖拽模式
                if (mode == Mode.NONE) {
                    if (touchMode == TouchMode.DRAG && pointerCount == 1 &&
                        ((abs(tx) > moveValue || abs(ty) > moveValue) || continuousDrag)
                    ) {
                        markManager.translateX += tx
                        markManager.translateY += ty
                        curMatrix.postTranslate(tx, ty)
                        invalidate()
                        lastMoveX = x
                        lastMoveY = y
                        continuousDrag = true
                    }
                } else { // 标记模式
                    if (isDelMark) return false
                    if (mode == Mode.PEN && !isDragMark) {
                        markManager.addPathPoint(x, y, tx, ty, continuousDrag)
                        continuousDrag = true
                    } else if ((abs(tx) > moveValue || abs(ty) > moveValue) || continuousDrag) {
                        markManager.translateMark(tx, ty, continuousDrag)
                        continuousDrag = true
                    }
                    invalidate()
                    lastMoveX = x
                    lastMoveY = y
                }
            }
            MotionEvent.ACTION_UP -> {
                touchMode = TouchMode.NONE
                continuousDrag = false
                isDelMark = false
                isDragMark = false
                markManager.unlockMark()
            }
        }
        return detector.onTouchEvent(event)
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector ?: return false
        if (mode == Mode.NONE && touchMode == TouchMode.ZOOM) {
            val oldScale = markManager.scale
            markManager.scale = scaleBaseValue * detector.scaleFactor
            if (markManager.scale == oldScale) return false
            val scaleFactor = markManager.scale / oldScale
            curMatrix.postScale(scaleFactor, scaleFactor, scalePointX, scalePointY)
            curMatrix.getValues(matrixValues)
            markManager.translateX = matrixValues[Matrix.MTRANS_X]
            markManager.translateY = matrixValues[Matrix.MTRANS_Y]
            invalidate()
        }
        return false
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        detector ?: return false
        if (mode == Mode.NONE) {
            touchMode = TouchMode.ZOOM
        }
        scaleBaseValue = markManager.scale
        scalePointX = detector.focusX
        scalePointY = detector.focusY
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        touchMode = TouchMode.NONE
    }

    /**
     * 初始化Bitmap配置
     */
    private fun initBitmapConfig() {
        val w = bitmap?.width ?: return
        val h = bitmap?.height ?: return
        L.vv("initBitmapConfig view: ${width}/${height}, bitmap: ${w}/${h}")
        val scaleX = width / w.toFloat()
        val scaleY = height / h.toFloat()
        val scale = min(scaleX, scaleY)
        val tx = if (scaleX > scaleY) (width - w) / 2f else 0f
        val ty = if (scaleX < scaleY) (height - h) / 2f else 0f
        curMatrix.run {
            reset()
            postScale(scale, scale)
            postTranslate(tx, ty)
        }
        markManager.init(w, h, scale, tx, ty)
    }

    /**
     * 设置背景图片
     */
    fun setBitmap(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val size = resizeImage(w, h)
        val scale = min(size.width / w.toFloat(), size.height / h.toFloat())
        val matrix = scaleMatrix(scale, scale)
        this.bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, false)
        if (this.bitmap != bitmap) bitmap.recycle()
        initBitmapConfig()
        invalidate()
    }

    /**
     * 向左旋转
     */
    fun rotate(clockwise: Boolean = true) {
//        val bitmap = bitmap ?: return
//        val degree = if (clockwise) 90f else -90f
//        val x = markManager.translateX + bitmap.width * markManager.scale / 2
//        val y = markManager.translateY + bitmap.height * markManager.scale / 2
//        curMatrix.postRotate(degree, x, y)
//        invalidate()
    }

    /**
     * 设置模式
     */
    fun setMode(mode: Mode) {
        this.mode = mode
    }

    /**
     * 清除画布
     */
    fun clear() {
        markManager.clear()
        invalidate()
    }

    /**
     * 随时获取EditText内的文字
     */
    fun setGetText(getText: () -> String) {
        this.getText = getText
    }

    /**
     * 设置缩放百分比
     */
    fun setScalePercent(percent: Float, continuousScale: Boolean) {
        markManager.setScalePercent(percent, continuousScale)
        invalidate()
    }

    /**
     * 撤销
     */
    fun undo() {
        markManager.undo()
        invalidate()
    }

    /**
     * 重做
     */
    fun redo() {
        markManager.redo()
        invalidate()
    }

    /**
     * View模式、拖动、缩放
     */
    enum class TouchMode {
        NONE,
        DRAG,
        ZOOM,
    }

    /**
     * 画笔模式
     */
    enum class Mode {
        NONE,
        RIGHT,
        WRONG,
        PEN,
        TEXT,
    }

}

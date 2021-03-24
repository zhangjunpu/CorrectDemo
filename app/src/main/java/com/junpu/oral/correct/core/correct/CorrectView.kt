package com.junpu.oral.correct.core.correct

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.scaleMatrix
import com.junpu.log.L
import com.junpu.oral.correct.core.TouchArea
import com.junpu.oral.correct.utils.resizeImage
import com.junpu.utils.isNotNullOrBlank
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * 批改
 * @author junpu
 * @date 2021/2/22
 */
class CorrectView : View, ScaleGestureDetector.OnScaleGestureListener {

    private val detector by lazy { ScaleGestureDetector(context, this) }

    // 原始图片
    private var originalSrcBitmap: Bitmap? = null // 原图
    private var originalBinBitmap: Bitmap? = null // 二值化
    private val originalBitmap: Bitmap?
        get() = if (isShowingBin) originalBinBitmap else originalSrcBitmap
    private var isShowingBin = false // 当前显示的是原图

    private var srcBitmap: Bitmap? = null // 原图
    private var bitmap: Bitmap? = null // 作业
    private var markBitmap: Bitmap? = null // mark

    // 缩放参数
    private var scaleBaseValue = 0f // 每次缩放的基数
    private var scalePointX = 0f // 每次缩放中心点
    private var scalePointY = 0f // 每次缩放中心点

    // 拖动参数
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var lastMarkMoveX = 0f
    private var lastMarkMoveY = 0f
    private var touchSlop = ViewConfiguration.get(context).scaledTouchSlop // 触发移动最小距离
    private var continuousDrag = false // 触发持续拖动，第一次移动moveValue距离后触发持续拖动，直到手指离开屏幕

    private var mode = Mode.NONE // 当前模式
    private var touchMode = TouchMode.NONE // 当前touch模式

    private val curMatrix = Matrix() // 图片矩阵
    private val matrixValues = FloatArray(9) // 矩阵数组

    private val markManager = MarkCorrectManager(context)
    private var isDelMark = false // 是否为删除模式
    private var isDragMark = false // 是否为拖动模式

    private lateinit var getText: () -> String

    // 图片基本数据
    private var degree = 0f // 旋转角度
    private var translateX = 0f // 当前移动X轴距离
    private var translateY = 0f // 当前移动Y轴距离
    private var scale = 1f // 当前缩放系数
        set(value) {
            field = min(IMAGE_SCALE_MAX, max(IMAGE_SCALE_MIN, value))
        }

    private val pointF = PointF() // 临时地址

    /**
     * 是否允许标记
     */
    var isMarkEnabled = true
        set(value) {
            field = value
            invalidate()
        }

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
        bitmap?.recycle()
        markBitmap?.recycle()
    }

    override fun onDraw(canvas: Canvas?) {
        canvas ?: return
        bitmap ?: return
        markBitmap ?: return
        // 画图片
        canvas.drawBitmap(bitmap!!, curMatrix, null)
        // 画标记
        if (isMarkEnabled) canvas.drawBitmap(markBitmap!!, curMatrix, null)
    }

    override fun invalidate() {
        markManager.draw()
        super.invalidate()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return super.onTouchEvent(event)
        if (!isMarkEnabled) return super.onTouchEvent(event)
        val x = event.x
        val y = event.y
        toMarkPoint(x, y, pointF)
        val mx = pointF.x
        val my = pointF.y
        val pointerCount = event.pointerCount
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (mode == Mode.NONE) {
                    touchMode = TouchMode.DRAG
                } else {
                    // 触摸区域判定
                    when (markManager.checkTouchArea(mx, my)) {
                        // 触摸到了删除按钮
                        TouchArea.DELETE -> {
                            isDelMark = true
                            markManager.removeMark()
                        }
                        // 触摸到了拖动按钮
                        TouchArea.DRAG -> {
                            isDragMark = true
                            markManager.lockMark(false)
                        }
                        // 触摸到了某个标记
                        TouchArea.MARK -> {
                            markManager.lockMark(true)
                        }
                        // 触摸到了空白区域
                        TouchArea.NONE -> {
                            var flag = true
                            when (mode) {
                                Mode.RIGHT -> markManager.generateRight(mx, my)
                                Mode.WRONG -> markManager.generateWrong(mx, my)
                                Mode.TEXT -> {
                                    val text = getText()
                                    if (text.isNotNullOrBlank())
                                        markManager.generateText(mx, my, text)
                                    else
                                        flag = false
                                }
                                Mode.PEN -> markManager.generatePath(mx, my)
                                else -> Unit
                            }
                            if (flag) markManager.lockMark(false)
                        }
                    }
                }
                if (mode != Mode.NONE) invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val tx = x - lastMoveX
                val ty = y - lastMoveY
                val mtx = mx - lastMarkMoveX
                val mty = my - lastMarkMoveY
                // 拖拽模式
                if (mode == Mode.NONE) {
                    if (touchMode == TouchMode.DRAG && pointerCount == 1 &&
                        ((tx.absoluteValue > touchSlop || ty.absoluteValue > touchSlop) || continuousDrag)
                    ) {
                        translateX += tx
                        translateY += ty
                        curMatrix.postTranslate(tx, ty)
                        invalidate()
                        continuousDrag = true
                    }
                } else { // 标记模式
                    if (isDelMark) return false
                    if (mode == Mode.PEN && !isDragMark) {
                        markManager.addPathPoint(mx, my, mtx, mty, continuousDrag)
                        continuousDrag = true
                    } else if ((tx.absoluteValue > touchSlop || ty.absoluteValue > touchSlop) || continuousDrag) {
                        markManager.translateMark(mtx, mty, continuousDrag)
                        continuousDrag = true
                    }
                    invalidate()
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
        lastMoveX = x
        lastMoveY = y
        lastMarkMoveX = mx
        lastMarkMoveY = my
        return detector.onTouchEvent(event)
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector ?: return false
        if (mode == Mode.NONE && touchMode == TouchMode.ZOOM) {
            val oldScale = scale
            scale = scaleBaseValue * detector.scaleFactor
            if (scale == oldScale) return false
            val scaleFactor = scale / oldScale
            curMatrix.postScale(scaleFactor, scaleFactor, scalePointX, scalePointY)
            curMatrix.getValues(matrixValues)
            translateX = matrixValues[Matrix.MTRANS_X]
            translateY = matrixValues[Matrix.MTRANS_Y]
            invalidate()
        }
        return false
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        detector ?: return false
        if (mode == Mode.NONE) {
            touchMode = TouchMode.ZOOM
        }
        scaleBaseValue = scale
        scalePointX = detector.focusX
        scalePointY = detector.focusY
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        touchMode = TouchMode.NONE
    }

    /**
     * 坐标转换，从view坐标到mark坐标
     */
    private fun toMarkPoint(x: Float, y: Float, point: PointF) {
        val rx = (x - translateX) / scale
        val ry = (y - translateY) / scale
        point.set(rx, ry)
    }

    /**
     * 初始化Bitmap配置
     */
    private fun initBitmapConfig() {
        val w = bitmap?.width ?: return
        val h = bitmap?.height ?: return
        val scaleX = width / w.toFloat()
        val scaleY = height / h.toFloat()
        scale = min(scaleX, scaleY)
        translateX = if (scaleX > scaleY) (width - w * scale) / 2f else 0f
        translateY = if (scaleX < scaleY) (height - h * scale) / 2f else 0f
        L.vv("initBitmapConfig view: ${width}/${height}, bitmap: ${w}/${h}, scale: $scaleX/$scaleY, translate: $translateX/$translateY")
        curMatrix.run {
            reset()
            postScale(scale, scale)
            postTranslate(translateX, translateY)
        }
        markBitmap?.recycle()
        markBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        markManager.initBitmap(markBitmap!!)
    }

    /**
     * 生成Bitmap
     */
    fun toBitmap(): Bitmap? {
        if (!isMarkEnabled) return bitmap
        val b = bitmap ?: return null
        val mb = markBitmap ?: return null
        L.vv("b: ${b.width}/${b.height}, mb: ${mb.width}/${mb.height}")
        val bitmap = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        markManager.save {
            bitmap.applyCanvas {
                val rect = Rect(0, 0, b.width, b.height)
                drawBitmap(b, null, rect, null)
                drawBitmap(mb, null, rect, null)
            }
        }
        return bitmap
    }

    /**
     * 设置背景图片
     */
    fun setBitmap(src: Bitmap?, bin: Bitmap?, orientation: Int) {
        src ?: return
        this.degree = orientation.toFloat()
        this.originalSrcBitmap = src
        this.bitmap = resizeBitmap(src, degree)
        bin?.let {
            this.originalBinBitmap = it
            this.bitmap = resizeBitmap(it, degree)
            isShowingBin = true
        }
        initBitmapConfig()
        markManager.rotate(degree) // 必须放initBitmapConfig后面
        invalidate()
    }

    /**
     * 获取缩放、旋转后的Bitmap
     */
    private fun resizeBitmap(bitmap: Bitmap, degree: Float = 0f): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val size = resizeImage(w, h)
        L.vv("resize from $w/$h to $size")
        val scale = min(size.width / w.toFloat(), size.height / h.toFloat())
        val matrix = scaleMatrix(scale, scale).apply { if (degree != 0f) postRotate(degree) }
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, false)
    }

    /**
     * 向左旋转
     */
    fun rotate(clockwise: Boolean = true) {
        val b = originalBitmap ?: return
        val deg = if (clockwise) 90f else -90f
        degree = (degree + deg) % 360
        if (bitmap != b) this.bitmap?.recycle()
        this.bitmap = resizeBitmap(b, degree)
        initBitmapConfig()
        markManager.rotate(degree) // 必须放initBitmapConfig后面
        invalidate()
    }

    /**
     * 切换Bitmap
     */
    fun switchBitmap() {
        originalBinBitmap ?: return
        isShowingBin = !isShowingBin
        val b = originalBitmap ?: return
        this.bitmap = resizeBitmap(b, degree)
        invalidate()
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

    companion object {
        // 图片缩放范围
        const val IMAGE_SCALE_MAX = 3f
        const val IMAGE_SCALE_MIN = .3f
    }

}

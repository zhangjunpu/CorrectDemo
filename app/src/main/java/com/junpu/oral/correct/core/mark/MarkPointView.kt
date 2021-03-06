package com.junpu.oral.correct.core.mark

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.scaleMatrix
import com.junpu.log.L
import com.junpu.oral.correct.core.TouchArea
import com.junpu.oral.correct.utils.resizeImage
import kotlin.math.absoluteValue
import kotlin.math.min

/**
 * 标记错题
 * @author junpu
 * @date 2021/3/18
 */
class MarkPointView : View {

    // 原始图片
    private var originalSrcBitmap: Bitmap? = null // 原图
    private var originalBinBitmap: Bitmap? = null // 二值化
    private val originalBitmap: Bitmap?
        get() = if (isShowingBin) originalBinBitmap else originalSrcBitmap
    private var isShowingBin = false // 当前显示的是原图

    private var bitmap: Bitmap? = null // 当前图片
    private var markBitmap: Bitmap? = null // mark

    // 拖动参数
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var lastMarkMoveX = 0f
    private var lastMarkMoveY = 0f
    private var touchSlop = ViewConfiguration.get(context).scaledTouchSlop // 触发移动最小距离
    private var continuousDrag = false // 触发持续拖动，第一次移动moveValue距离后触发持续拖动，直到手指离开屏幕

    private val curMatrix = Matrix() // 图片矩阵
    private var isDelMark = false // 是否为删除模式
    private val markManager = MarkPointManager(context)

    private var degree = 0f // 旋转角度
    private var translateX = 0f // 当前移动X轴距离
    private var translateY = 0f // 当前移动Y轴距离
    private var scale = 1f // 当前缩放系数

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
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 触摸区域判定
                when (markManager.checkTouchArea(mx, my)) {
                    // 触摸到了删除按钮
                    TouchArea.DELETE -> {
                        isDelMark = true
                        markManager.removeMark()
                    }
                    // 触摸到了某个标记
                    TouchArea.MARK -> {
                        markManager.lockMark()
                    }
                    // 触摸到了空白区域
                    TouchArea.NONE -> markManager.run { if (generatePoint(mx, my)) lockMark() }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDelMark) return false
                val tx = x - lastMoveX
                val ty = y - lastMoveY
                val mtx = mx - lastMarkMoveX
                val mty = my - lastMarkMoveY
                if (continuousDrag || (tx.absoluteValue > touchSlop || ty.absoluteValue > touchSlop)) {
                    markManager.translateMark(mtx, mty)
                    continuousDrag = true
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                continuousDrag = false
                isDelMark = false
                markManager.unlockMark()
            }
        }
        lastMoveX = x
        lastMoveY = y
        lastMarkMoveX = mx
        lastMarkMoveY = my
        return true
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
        markBitmap = emptyBitmap(w, h)
        markManager.initBitmap(markBitmap!!)
    }

    /**
     * 生成Bitmap
     */
    fun toBitmaps(): Array<Bitmap?> {
        val b = bitmap ?: return emptyArray()
        val w = b.width
        val h = b.height

        val src: Bitmap
        var bin: Bitmap? = null
        if (originalBinBitmap == null) {
            src = b
        } else {
            src = if (isShowingBin) resizeBitmap(originalSrcBitmap!!, degree) else b
            bin = if (isShowingBin) b else resizeBitmap(originalBinBitmap!!, degree)
        }

        // 画标记
        if (isMarkEnabled) {
            val resultSrc = emptyBitmap(w, h)
            val resultBin = if (originalBinBitmap != null) emptyBitmap(w, h) else null

            val rect = Rect(0, 0, w, h)
            resultSrc.applyCanvas {
                drawBitmap(src, null, rect, null)
                markManager.drawMark(this)
            }
            bin?.let {
                resultBin?.applyCanvas {
                    drawBitmap(bin, null, rect, null)
                    markManager.drawMark(this)
                }
            }
            return arrayOf(resultSrc, resultBin)
        }

        return arrayOf(src, bin)
    }

    private fun emptyBitmap(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

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
        markManager.rotate(degree) // 当前View不涉及Mark旋转，不加这行也无所谓，但CorrectView必须要加；
        invalidate()
    }

    /**
     * 单独设置二值化图片
     */
    fun setBinBitmap(bin: Bitmap?) {
        bin ?: return
        this.originalBinBitmap = bin
        if (isShowingBin) {
            this.bitmap = resizeBitmap(bin, degree)
            invalidate()
        }
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
     * 清除画布
     */
    fun clear() {
        markManager.clear()
        invalidate()
    }

    /**
     * 释放
     */
    fun release() {
        markManager.release()
        originalSrcBitmap?.recycle()
        originalBinBitmap?.recycle()
        bitmap?.recycle()
        markBitmap?.recycle()
        originalSrcBitmap = null
        originalBinBitmap = null
        bitmap = null
        markBitmap = null
    }

    /**
     * 标记数量变化回调
     */
    fun doOnPointCountChanged(callback: (count: Int) -> Unit) {
        markManager.doOnPointCountChanged(callback)
    }

    /**
     * 获取标记数量
     */
    fun getMarkCount() = markManager.getMarkCount()

    /**
     * 获取标记坐标
     */
    fun getMarkPointLocation() = markManager.getMarkLocation()

}
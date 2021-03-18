package com.junpu.oral.correct.mark

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.scaleMatrix
import com.junpu.log.L
import com.junpu.oral.correct.correct.MarkCorrectManager
import com.junpu.oral.correct.utils.resizeImage
import kotlin.math.abs
import kotlin.math.min

/**
 * 标记错题
 * @author junpu
 * @date 2021/3/18
 */
class MarkPointView : View {

    private var srcBitmap: Bitmap? = null // 原图
    private var bitmap: Bitmap? = null // 作业
    private var markBitmap: Bitmap? = null // mark

    // 拖动参数
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var lastMarkMoveX = 0f
    private var lastMarkMoveY = 0f
    private var moveValue = 10 // 触发移动最小距离
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
                    MarkCorrectManager.TouchArea.DELETE -> {
                        isDelMark = true
                        markManager.removeMark()
                    }
                    // 触摸到了某个标记
                    MarkCorrectManager.TouchArea.MARK -> {
                        markManager.lockMark()
                    }
                    // 触摸到了空白区域
                    MarkCorrectManager.TouchArea.NONE -> {
                        markManager.generatePoint(mx, my)
                        markManager.lockMark()
                    }
                }
                lastMoveX = x
                lastMoveY = y
                lastMarkMoveX = mx
                lastMarkMoveY = my
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val tx = x - lastMoveX
                val ty = y - lastMoveY
                val mtx = mx - lastMarkMoveX
                val mty = my - lastMarkMoveY

                if (isDelMark) return false
                if (continuousDrag || (abs(tx) > moveValue || abs(ty) > moveValue)) {
                    markManager.translateMark(mtx, mty)
                    continuousDrag = true
                }
                invalidate()
                lastMoveX = x
                lastMoveY = y
                lastMarkMoveX = mx
                lastMarkMoveY = my
            }
            MotionEvent.ACTION_UP -> {
                continuousDrag = false
                isDelMark = false
                markManager.unlockMark()
            }
        }
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
        markBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        markManager.initBitmap(markBitmap!!)
    }

    /**
     * 生成Bitmap
     */
    fun toBitmap(): Bitmap? {
        if (!isMarkEnabled) {
            return bitmap
        }
        val b = bitmap ?: return null
        val mb = markBitmap ?: return null
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
    fun setBitmap(bitmap: Bitmap) {
        srcBitmap = bitmap
        val w = bitmap.width
        val h = bitmap.height
        val matrix = getResizeScaleMatrix(w, h)
        this.bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, false)
        initBitmapConfig()
        invalidate()
    }

    /**
     * 获取缩放后的矩阵
     */
    private fun getResizeScaleMatrix(w: Int, h: Int): Matrix {
        val size = resizeImage(w, h)
        val scale = min(size.width / w.toFloat(), size.height / h.toFloat())
        return scaleMatrix(scale, scale)
    }

    /**
     * 向左旋转
     */
    fun rotate(clockwise: Boolean = true) {
        val b = srcBitmap ?: return
        val deg = if (clockwise) 90f else -90f
        degree = (degree + deg) % 360
        val m = getResizeScaleMatrix(b.height, b.width).apply { postRotate(degree) }
        if (this.bitmap != b) this.bitmap?.recycle()
        this.bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, false)
        initBitmapConfig()
        markManager.rotate(degree) // 必须放initBitmapConfig后面
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
     * 标记数量变化回调
     */
    fun doOnPointCountChanged(callback: (count: Int) -> Unit) {
        markManager.doOnPointCountChanged(callback)
    }

    /**
     * 获取标记数量
     */
    fun getMarkCount() = markManager.getMarkCount()

    fun getMarkPointLocation() = markManager.getMarkLocation()

}
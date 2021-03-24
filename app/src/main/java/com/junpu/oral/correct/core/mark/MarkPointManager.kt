package com.junpu.oral.correct.core.mark

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import com.junpu.oral.correct.R
import com.junpu.oral.correct.core.TouchArea
import com.junpu.oral.correct.core.module.MarkPoint

/**
 * 标记错题Mark
 * @author junpu
 * @date 2021/3/18
 */
class MarkPointManager(private val context: Context) {

    private val pointMark by lazy { PointMark() }
    private val symbolMark by lazy { SymbolMark() }
    private val selectedMark by lazy { SelectedMark() }

    // 画布宽高
    private var width = 0
    private var height = 0

    private var rotation = 0 // 旋转方向，0,1,2,3 -> 0,90,180,270
    private var onPointCountChangedCallback: ((count: Int) -> Unit)? = null
    private val markList = arrayListOf<PointF>() // mark数据队列
    private var curIndex = -1 // 当前选中
    private val curPoint: PointF? // 当前选中Mark
        get() = if (curIndex in markList.indices) markList[curIndex] else null

    // 临时内存地址
    private var rectF = RectF() // 临时RectF
    private var arr = FloatArray(4) // 临时数组
    private var lockedPoint: PointF? = null // 处理中的mark指针

    // mark Canvas
    private val canvas by lazy { Canvas() }

    /**
     * 初始化赋值
     */
    fun initBitmap(bitmap: Bitmap) {
        canvas.setBitmap(bitmap)
        width = canvas.width
        height = canvas.height
    }

    /**
     * 生成新标记 - path
     */
    fun generatePoint(x: Float, y: Float) {
        if (!checkOutOfBounds(x, y)) return
        markList.add(PointF(x, y))
        curIndex = markList.lastIndex
        onPointCountChangedCallback?.invoke(markList.size)
    }

    /**
     * 删除当前标记
     */
    fun removeMark() {
        if (curIndex != -1) {
            markList.removeAt(curIndex)
            curIndex = if (markList.isNullOrEmpty()) -1 else markList.lastIndex
            onPointCountChangedCallback?.invoke(markList.size)
        }
    }

    /**
     * 清除标记
     */
    fun clear() {
        markList.clear()
        markList.trimToSize()
        onPointCountChangedCallback?.invoke(markList.size)
    }

    /**
     * 画mark
     */
    fun draw() {
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        markList.forEachIndexed { index, it ->
            pointMark.draw(canvas, it)
            // 当前选中的mark，画选中框
            if (index == curIndex) selectedMark.draw(canvas, it)
        }
    }

    /**
     * 在指定的canvas上画标记、或勾
     */
    fun drawMark(canvas: Canvas) {
        if (markList.isNullOrEmpty()) {
            symbolMark.draw(canvas)
        } else {
            markList.forEach { pointMark.draw(canvas, it) }
        }
    }

    /**
     * 移动标记，不包含path
     */
    fun translateMark(tx: Float, ty: Float) {
        val point = lockedPoint ?: return
        val x = point.x + tx
        val y = point.y + ty
        // 边界检测
        if (!checkOutOfBounds(x, y, SELECTED_RADIUS)) return
        point.x = x
        point.y = y
    }

    /**
     * 检查触摸区域
     * @return [TouchArea] 触摸区域,
     * [TouchArea.DELETE] 删除按钮，
     * [TouchArea.MARK] 某个mark区域内，
     * [TouchArea.NONE] 空白区域
     */
    fun checkTouchArea(x: Float, y: Float): TouchArea {
        val point = curPoint ?: return TouchArea.NONE
        selectedMark.getBounds(point, rectF)
        val r = rectF.right
        val t = rectF.top
        val offset = TOUCH_OFFSET.toFloat() // 触摸误差范围

        // 判断x, y是否触摸到了指定的rect区域内
        val isTouched = { rect: RectF ->
            rect.inset(-offset, -offset) // 增加触摸误差范围
            rect.contains(x, y)
        }

        // 预留判断是否点到了当前选中标记上
        val isTouchedCurMark = isTouched(rectF)

        // 判断是否点到了删除按钮
        selectedMark.getButtonBounds(r, t, rectF)
        if (isTouched(rectF)) return TouchArea.DELETE

        // 触摸到了当前标记上
        if (isTouchedCurMark) return TouchArea.MARK

        // 判断是否点击到了某个按钮
        for (i in markList.lastIndex downTo 0) {
            if (point == markList[i]) continue
            pointMark.getBounds(markList[i], rectF)
            if (isTouched(rectF)) {
                curIndex = i
                return TouchArea.MARK
            }
        }
        return TouchArea.NONE
    }

    /**
     * 边界检测，是否在画布范围内
     * @return true 在范围内, false 超出范围
     */
    private fun checkOutOfBounds(x: Float, y: Float, radius: Float = 0f): Boolean {
        // Bitmap的范围
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        return rectF.contains(x - radius, y - radius, x + radius, y + radius)
    }


    /**
     * 声明要操作的mark
     */
    fun lockMark() {
        lockedPoint = curPoint
    }

    /**
     * 释放操作的mark
     */
    fun unlockMark() {
        lockedPoint = null
    }

    /**
     * 释放必要资源
     */
    fun release() {
        markList.clear()
    }

    /**
     * 旋转
     */
    fun rotate(deg: Float) {
        val oldRotation = rotation
        rotation = (deg.toInt() / 90) and 3
        val r = (rotation - oldRotation) and 3
        rotationMarkList(r)
    }

    /**
     * 旋转数据
     */
    private fun rotationMarkList(rotation: Int) {
        /**
         * 根据rotation旋转方向，获得x, y旋转后的在屏幕上对应的点
         */
        fun pointRotation(x: Float, y: Float, arr: FloatArray, rotation: Int) {
            arr[0] = x
            arr[1] = y
            if (rotation == 0) return

            val swap = rotation and 1 == 1
            val w = if (swap) height else width
            val h = if (swap) width else height
            arr[2] = w - x
            arr[3] = h - y

            // 数组正向位移操作
            val move = {
                val temp = arr[arr.lastIndex]
                for (i in arr.lastIndex downTo 1) {
                    arr[i] = arr[i - 1]
                }
                arr[0] = temp
            }

            var i = 0
            while (i < rotation) {
                move()
                i++
            }
        }

        markList.forEach {
            pointRotation(it.x, it.y, arr, rotation)
            it.x = arr[0]
            it.y = arr[1]
        }
    }

    /**
     * 标记数量变化回调
     */
    fun doOnPointCountChanged(callback: (count: Int) -> Unit) {
        onPointCountChangedCallback = callback
    }

    /**
     * 获取标记数量
     */
    fun getMarkCount(): Int = markList.size

    /**
     * 获取标记列表
     */
    fun getMarkLocation() = MarkPoint(width, height, markList)


    /**
     * Point标记
     */
    private inner class PointMark {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#CCFF2E2E")
            style = Paint.Style.FILL
        }

        fun draw(canvas: Canvas, point: PointF) {
            canvas.drawCircle(point.x, point.y, MARK_RADIUS, paint)
        }

        /**
         * 获取Point的边框范围
         */
        fun getBounds(point: PointF, rect: RectF) {
            val x = point.x
            val y = point.y
            val r = MARK_RADIUS
            rect.set(x - r, y - r, x + r, y + r)
        }
    }

    /**
     * 符号相关
     * @author junpu
     * @date 2021/3/8
     */
    private inner class SymbolMark {
        private val symbolRight by lazy {
            ContextCompat.getDrawable(context, R.drawable.ic_symbol_right_thin)
        }

        fun draw(canvas: Canvas) {
            symbolRight?.run {
                getBounds(rectF)
                val l = rectF.left.toInt()
                val t = rectF.top.toInt()
                val r = rectF.right.toInt()
                val b = rectF.bottom.toInt()
                setBounds(l, t, r, b)
                draw(canvas)
            }
        }

        fun getBounds(rect: RectF) {
            rect.setEmpty()
            val w = SYMBOL_RIGHT_WIDTH
            val h = SYMBOL_RIGHT_HEIGHT
            val x = width * 0.75f
            val y = height * 0.75f
            val l = x - w * 0.5f
            val t = y - h * 0.5f
            rect.set(l, t, l + w, t + h)
        }
    }

    /**
     * 当前选中
     * @author junpu
     * @date 2021/3/18
     */
    private inner class SelectedMark {
        private val delIcon by lazy {
            ContextCompat.getDrawable(context, R.drawable.ic_mark_del)
        }

        private val paint = Paint().apply {
            isAntiAlias = true
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = SELECTED_LINE_WIDTH
        }

        fun draw(canvas: Canvas, point: PointF) {
            getBounds(point, rectF)
            // 画框
            canvas.drawRect(rectF, paint)
            val r = rectF.right
            val t = rectF.top

            // 画delete按钮
            getButtonBounds(r, t, rectF)
            delIcon?.setBounds(
                rectF.left.toInt(),
                rectF.top.toInt(),
                rectF.right.toInt(),
                rectF.bottom.toInt()
            )
            delIcon?.draw(canvas)
        }

        /**
         * 获取point的selected边框
         */
        fun getBounds(point: PointF, rect: RectF) {
            val x = point.x
            val y = point.y
            val r = SELECTED_RADIUS
            rect.set(x - r, y - r, x + r, y + r)
        }

        /**
         * 获取以x、y点为中心的按钮区域
         */
        fun getButtonBounds(x: Float, y: Float, rect: RectF) {
            val r = BUTTON_RADIUS
            rect.set(x - r, y - r, x + r, y + r)
        }
    }

    companion object {
        private const val SYMBOL_RIGHT_WIDTH = 200 // 对勾宽
        private const val SYMBOL_RIGHT_HEIGHT = 132 // 对勾高
        private const val MARK_RADIUS = 20f // Point半径 10dp
        private const val SELECTED_LINE_WIDTH = 3f // selected线宽
        private const val SELECTED_RADIUS = 28f // selected半径
        private const val BUTTON_RADIUS = 15f // 删除按钮直径
        private const val TOUCH_OFFSET = 10 // 手指点击的范围误差 4dp
    }
}
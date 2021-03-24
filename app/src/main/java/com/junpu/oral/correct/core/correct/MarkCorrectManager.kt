package com.junpu.oral.correct.core.correct

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import androidx.core.content.ContextCompat
import com.junpu.oral.correct.R
import com.junpu.oral.correct.core.TouchArea
import com.junpu.oral.correct.core.module.MarkCorrect
import com.junpu.oral.correct.core.module.MarkRecord
import com.junpu.oral.correct.core.module.PathPoint
import kotlin.math.max
import kotlin.math.min

/**
 * 标记批改管理
 * @author junpu
 * @date 2021/2/25
 */
class MarkCorrectManager(private var context: Context) {

    private val symbolMark by lazy { SymbolMark() }
    private val textMark by lazy { TextMark() }
    private val pathMark by lazy { PathMark() }
    private val selectedMark by lazy { SelectedMark() }
    private val recordManager by lazy { OperationRecords() }

    // 画布宽高
    private var width = 0
    private var height = 0

    // Mark缩放系数
    private var markScale = 1f
        set(value) {
            field = min(MARK_SCALE_MAX, max(MARK_SCALE_MIN, value))
        }
    private var rotation = 0 // 旋转方向，0,1,2,3 -> 0,90,180,270

    // mark数据
    private val markList = arrayListOf<MarkCorrect>() // mark数据队列
    private var curIndex = -1 // 当前选中
    private val curMark: MarkCorrect? // 当前选中Mark
        get() = if (curIndex in markList.indices) markList[curIndex] else null

    // 临时内存地址
    private var pointF = PointF() // 临时PointF
    private var rectF = RectF() // 临时RectF
    private var arr = FloatArray(4) // 临时数组
    private var lockedMark: MarkCorrect? = null // 处理中的mark指针
    private var lockedPoints: MutableList<PointF>? = null // 操作中的PathPoint

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
     * 生成新标记 - 勾
     */
    fun generateRight(x: Float, y: Float): Boolean {
        if (!checkOutOfBounds(x, y)) return false
        addMark(symbolMark.newMark(x, y, SYMBOL_TYPE_RIGHT))
        return true
    }

    /**
     * 生成新标记 - 叉
     */
    fun generateWrong(x: Float, y: Float): Boolean {
        if (!checkOutOfBounds(x, y)) return false
        addMark(symbolMark.newMark(x, y, SYMBOL_TYPE_WRONG))
        return true
    }

    /**
     * 生成新标记 - 文字
     */
    fun generateText(x: Float, y: Float, text: String?): Boolean {
        if (!checkOutOfBounds(x, y) || text.isNullOrBlank()) return false
        addMark(textMark.newMark(x, y, text))
        return true
    }

    /**
     * 生成新标记 - path
     */
    fun generatePath(x: Float, y: Float): Boolean {
        if (!checkOutOfBounds(x, y)) return false
        addMark(pathMark.newMark(x, y))
        return true
    }

    private fun addMark(mark: MarkCorrect?) {
        mark ?: return
        markList.add(mark)
        recordManager.addAddRecord(mark)
        curIndex = markList.lastIndex
    }

    /**
     * 画mark
     */
    fun draw(isDrawSelected: Boolean = true) {
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        markList.forEachIndexed { index, it ->
            when (it.type) {
                MARK_TYPE_SYMBOL -> symbolMark.draw(canvas, it)
                MARK_TYPE_TEXT -> textMark.draw(canvas, it)
                MARK_TYPE_DRAWING -> pathMark.draw(canvas, it)
            }
            // 当前选中的mark，画选中框
            if (isDrawSelected && index == curIndex) {
                selectedMark.draw(canvas, it)
            }
        }
    }

    /**
     * 删除当前标记
     */
    fun removeMark() {
        if (curIndex != -1) {
            val mark = markList.removeAt(curIndex)
            recordManager.addDeleteRecord(mark, curIndex)
            curIndex = if (markList.isNullOrEmpty()) -1 else markList.lastIndex
        }
    }

    /**
     * 清除标记
     */
    fun clear() {
        markList.clear()
        markList.trimToSize()
        recordManager.clear()
    }

    /**
     * 撤销
     */
    fun undo() {
        recordManager.undo()
    }

    /**
     * 重做
     */
    fun redo() {
        recordManager.redo()
    }

    /**
     * 设置scale百分比
     * [continuousScale] 持续缩放，如果为false，则添加 [RecordType.SCALE] 缩放记录
     */
    fun setScalePercent(percent: Float, continuousScale: Boolean) {
        markScale = MARK_SCALE_MIN + percent * (MARK_SCALE_MAX - MARK_SCALE_MIN)
        val mark = lockedMark ?: curMark ?: return
        // 添加操作记录
        if (!continuousScale) recordManager.addScaleRecord(mark)
        mark.scale = markScale
    }

    /**
     * 移动标记，不包含path
     * [continuousDrag] 如果是false，则添加 [RecordType.MOVE] 操作记录
     */
    fun translateMark(tx: Float, ty: Float, continuousDrag: Boolean) {
        val mark = lockedMark ?: return
        // 添加操作记录
        if (!continuousDrag) recordManager.addMoveRecord(mark)
        if (mark.isTypePath) {
            getMarkBounds(mark, rectF)
            val x = rectF.left + tx
            val y = rectF.top + ty
            if (!checkOutOfBounds(x, y, rectF.width(), rectF.height())) return
            mark.segments?.forEach { it ->
                it.points?.forEach { it.offset(tx, ty) }
            }
        } else {
            val x = mark.x + tx
            val y = mark.y + ty
            // 边界检测
            getMarkBounds(mark, rectF)
            val w = rectF.width()
            val h = rectF.height()
            val r = adjustRotation(rotation - mark.rotation)
            val rx = if (r == 1 || r == 2) x - w else x
            val ry = if (r == 2 || r == 3) y - h else y
            if (!checkOutOfBounds(rx, ry, rectF.width(), rectF.height())) return
            mark.x = x
            mark.y = y
        }
    }

    /**
     * 添加路径
     */
    fun addPathPoint(x: Float, y: Float, tx: Float, ty: Float, continuousDrag: Boolean) {
        val mark = lockedMark ?: return
        if (mark.isTypePath) {
            // 边界范围检测
            var px = x
            var py = y
            getBitmapBounds(rectF)
            if (!rectF.contains(px, py)) {
                if (px < rectF.left) px = rectF.left else if (px > rectF.right) px = rectF.right
                if (py < rectF.top) py = rectF.top else if (py > rectF.bottom) py = rectF.bottom
            }
            lockedPoints?.add(PointF(px, py))
        } else {
            translateMark(tx, ty, continuousDrag)
        }
    }

    /**
     * 检查触摸区域
     * @return [TouchArea] 触摸区域,
     * [TouchArea.DELETE] 删除按钮，
     * [TouchArea.DRAG] 拖动按钮，
     * [TouchArea.MARK] 某个mark区域内，
     * [TouchArea.NONE] 空白区域
     */
    fun checkTouchArea(x: Float, y: Float): TouchArea {
        val mark = curMark ?: return TouchArea.NONE
        getMarkBounds(mark, rectF)
        val l = rectF.left
        val t = rectF.top
        val r = rectF.right
        val offset = TOUCH_OFFSET.toFloat() // 触摸误差范围

        // 判断x, y是否触摸到了指定的rect区域内
        fun isTouched(rect: RectF): Boolean {
            rect.inset(-offset, -offset) // 增加触摸误差范围
            return rect.contains(x, y)
        }

        // 预留判断是否点到了当前选中标记上
        val isTouchedCurMark = isTouched(rectF)

        // 判断是否点到了删除按钮
        selectedMark.getButtonBounds(r, t, rectF)
        if (isTouched(rectF)) return TouchArea.DELETE

        // 判断是否触摸到了拖动按钮（只有path模式拥有拖动按钮）
        if (mark.isTypePath) {
            selectedMark.getButtonBounds(l, t, rectF)
            if (isTouched(rectF)) return TouchArea.DRAG
        }

        // 触摸到了当前标记上
        if (isTouchedCurMark) return TouchArea.MARK

        // 判断是否点击到了某个按钮
        for (i in markList.lastIndex downTo 0) {
            if (mark == markList[i]) continue
            getMarkBounds(markList[i], rectF)
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
    private fun checkOutOfBounds(x: Float, y: Float, w: Float = 0f, h: Float = 0f): Boolean {
        getBitmapBounds(rectF)
        return rectF.contains(x, y, x + w, y + h)
    }

    /**
     * 获取位图在View上的边界
     */
    private fun getBitmapBounds(rect: RectF) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
    }

    /**
     * 获取mark在View上的边界
     */
    private fun getMarkBounds(mark: MarkCorrect, rect: RectF) {
        when (mark.type) {
            MARK_TYPE_SYMBOL -> symbolMark.getBounds(mark, rect)
            MARK_TYPE_TEXT -> textMark.getBounds(mark, rect)
            MARK_TYPE_DRAWING -> pathMark.getBounds(mark, rect)
        }
    }

    /**
     * 声明要操作的mark
     */
    fun lockMark(newSegments: Boolean) {
        lockedMark = curMark
        val mark = lockedMark ?: return
        if (mark.isTypePath) {
            val segments = mark.segments ?: arrayListOf<PathPoint>().also { mark.segments = it }
            val pathPoint = if (segments.isEmpty() || newSegments) pathMark.newPathPoint.also {
                segments.add(it)
                if (newSegments) recordManager.addSegmentsRecord(mark)
            } else segments.last()
            lockedPoints = pathPoint.points ?: arrayListOf<PointF>().also { pathPoint.points = it }
        }
    }

    /**
     * 释放操作的mark
     */
    fun unlockMark() {
        lockedMark = null
        lockedPoints = null
    }

    /**
     * 释放必要资源
     */
    fun release() {
        markList.clear()
        recordManager.clear()
    }

    /**
     * 旋转
     */
    fun rotate(deg: Float) {
        val oldRotation = rotation
        rotation = adjustRotation(deg.toInt() / 90)
        val r = adjustRotation(rotation - oldRotation)
        rotationMarkList(r)
    }

    /**
     * 旋转数据
     */
    private fun rotationMarkList(rotation: Int) {
        markList.forEach {
            if (it.isTypePath) {
                it.segments?.forEach { seg ->
                    seg.points?.forEach { point ->
                        pointRotation(point.x, point.y, arr, rotation)
                        point.set(arr[0], arr[1])
                    }
                }
            } else {
                pointRotation(it.x, it.y, arr, rotation)
                it.x = arr[0]
                it.y = arr[1]
            }
        }
    }

    /**
     * 根据rotation旋转方向，获得x, y旋转后的在屏幕上对应的点
     */
    private fun pointRotation(x: Float, y: Float, arr: FloatArray, rotation: Int) {
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

    /**
     * 获取mark相对于当前方向应该旋转的角度
     */
    private fun getMarkDegree(mark: MarkCorrect, rotation: Int): Float {
        val r = rotation - mark.rotation
        return (r * 90f) % 360
    }

    /**
     * rotation矫正
     */
    private fun adjustRotation(rotation: Int) = rotation and 3

    /**
     * 是否为path模式
     */
    private val MarkCorrect.isTypePath: Boolean get() = type == MARK_TYPE_DRAWING

    /**
     * 保存图片时消除选中框
     */
    fun save(block: () -> Unit) {
        draw(false)
        block()
        draw()
    }

    /**
     * 符号相关
     * @author junpu
     * @date 2021/3/8
     */
    private inner class SymbolMark {

        // 勾
        private val symbolRight by lazy {
            ContextCompat.getDrawable(context, R.drawable.ic_symbol_right)
        }

        // 叉
        private val symbolWrong by lazy {
            ContextCompat.getDrawable(context, R.drawable.ic_symbol_wrong)
        }

        /**
         * 生成新的符号
         */
        fun newMark(x: Float, y: Float, symbol: String?): MarkCorrect {
            return MarkCorrect(MARK_TYPE_SYMBOL, x, y, markScale, symbol, rotation = rotation)
        }

        /**
         * 画勾、叉
         */
        fun draw(canvas: Canvas, mark: MarkCorrect) {
            val img = (if (mark.symbol == SYMBOL_TYPE_RIGHT) symbolRight else symbolWrong) ?: return
            getBounds(mark, rectF)
            getSize(mark, pointF)
            val w = pointF.x
            val h = pointF.y
            val l = mark.x
            val t = mark.y
            canvas.save()
            canvas.rotate(getMarkDegree(mark, rotation), l, t)
            img.run {
                setBounds(l.toInt(), t.toInt(), (l + w).toInt(), (t + h).toInt())
                draw(canvas)
            }
            canvas.restore()
        }

        /**
         * 获取Mark的边界
         */
        fun getBounds(mark: MarkCorrect, rect: RectF) {
            rect.setEmpty()
            val r = adjustRotation(rotation - mark.rotation)
            val swap = r and 1 == 1
            getSize(mark, pointF)
            val w = if (swap) pointF.y else pointF.x
            val h = if (swap) pointF.x else pointF.y
            val x = mark.x
            val y = mark.y
            val l = if (r == 1 || r == 2) x - w else x
            val t = if (r == 2 || r == 3) y - h else y
            rect.set(l, t, l + w, t + h)
        }

        /**
         * 获取符号的尺寸
         */
        private fun getSize(mark: MarkCorrect, rect: PointF) {
            var w = 0f
            var h = 0f
            when (mark.symbol) {
                SYMBOL_TYPE_RIGHT -> {
                    w = SYMBOL_RIGHT_WIDTH * mark.scale
                    h = SYMBOL_RIGHT_HEIGHT * mark.scale
                }
                SYMBOL_TYPE_WRONG -> {
                    w = SYMBOL_WRONG_WIDTH * mark.scale
                    h = SYMBOL_WRONG_HEIGHT * mark.scale
                }
            }
            rect.set(w, h)
        }
    }

    /**
     * 文本相关
     * @author junpu
     * @date 2021/3/8
     */
    private inner class TextMark {

        // text Paint
        private val textPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.RED
            textAlign = Paint.Align.LEFT
        }

        /**
         * 生成新的mark
         */
        fun newMark(x: Float, y: Float, text: String): MarkCorrect {
            return MarkCorrect(MARK_TYPE_TEXT, x, y, markScale, text = text, rotation = rotation)
        }

        /**
         * 画文字
         */
        fun draw(canvas: Canvas, mark: MarkCorrect) {
            if (mark.text.isNullOrBlank()) return
            val x = mark.x
            val y = mark.y
            val texts = mark.getTextForEachLine()
            val textSize = TEXT_SIZE * mark.scale
            textPaint.textSize = textSize
            val metrics = textPaint.fontMetrics // baseline相关参数
            canvas.save()
            canvas.rotate(getMarkDegree(mark, rotation), x, y)
            texts.forEachIndexed { index, text ->
                // 由于drawText是从baseline坐标开始画的，所以需要向下偏移一行baseline到文字顶部的高度，+2为微调
                val offsetY = (index + 1) * textSize - metrics.descent + 2
                canvas.drawText(text, x, y + offsetY, textPaint)
            }
            canvas.restore()
        }

        /**
         * 获取mark的边界
         */
        fun getBounds(mark: MarkCorrect, rect: RectF) {
            rect.setEmpty()
            val r = adjustRotation(rotation - mark.rotation)
            val swap = r and 3 == 1
            getSize(mark, pointF)
            val w = if (swap) pointF.y else pointF.x
            val h = if (swap) pointF.x else pointF.y
            val x = mark.x
            val y = mark.y
            val l = if (r == 1 || r == 2) x - w else x
            val t = if (r == 2 || r == 3) y - h else y
            rect.set(l, t, l + w, t + h)
        }

        /**
         * 获取文字的尺寸
         */
        private fun getSize(mark: MarkCorrect, rect: PointF) {
            val texts = mark.getTextForEachLine()
            val textSize = TEXT_SIZE * mark.scale
            textPaint.textSize = textSize
            val w = texts.maxOf { textPaint.measureText(it) }
            val h = texts.size * textSize
            rect.set(w, h)
        }

        /**
         * 获取每一行的文本
         */
        private fun MarkCorrect.getTextForEachLine(): List<String> {
            return if (text.isNullOrBlank()) emptyList() else text!!.split("\n")
        }
    }

    /**
     * Path相关
     * @author junpu
     * @date 2021/3/8
     */
    private inner class PathMark {

        // path
        private val path = Path()

        // path Paint
        private val pathPaint = Paint().apply {
            isAntiAlias = true
            color = Color.RED
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = PATH_LINE_WIDTH
        }

        /**
         * 生成新的mark
         */
        fun newMark(x: Float, y: Float): MarkCorrect {
            val segments = arrayListOf(newPathPoint)
            return MarkCorrect(MARK_TYPE_DRAWING, x, y, markScale, segments = segments)
        }

        /**
         * 画path
         */
        fun draw(canvas: Canvas, mark: MarkCorrect) {
            if (mark.segments.isNullOrEmpty()) return
            for (it in mark.segments!!) {
                if (it.points.isNullOrEmpty()) continue
                // 画path
                path.run {
                    reset()
                    it.points?.forEachIndexed { index, point ->
                        val x = point.x
                        val y = point.y
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                canvas.drawPath(path, pathPaint.apply { strokeWidth = it.lineWidth })
            }
        }

        /**
         * 获取Mark的边界
         */
        fun getBounds(mark: MarkCorrect, rect: RectF) {
            rect.setEmpty()
            val segments = mark.segments
            if (segments.isNullOrEmpty()) return
            var l = 0f
            var t = 0f
            var r = 0f
            var b = 0f
            var isFirst = true // 第一次赋值
            segments.forEach { point ->
                point.points?.forEach {
                    val x = it.x
                    val y = it.y
                    if (isFirst) {
                        l = x
                        t = y
                        r = x
                        b = y
                        isFirst = false
                    } else {
                        if (x < l) l = x
                        if (y < t) t = y
                        if (x > r) r = x
                        if (y > b) b = y
                    }
                }
            }
            rect.set(l, t, r, b)
        }

        /**
         * new一个新的PathPoint
         */
        val newPathPoint: PathPoint get() = PathPoint(arrayListOf(), PATH_LINE_WIDTH * markScale)
    }

    /**
     * mark选中效果相关
     * @author junpu
     * @date 2021/3/8
     */
    private inner class SelectedMark {

        // 删除icon
        private val delIcon by lazy {
            ContextCompat.getDrawable(context, R.drawable.ic_mark_del)
        }

        // 拖拽
        private val dragIcon by lazy {
            ContextCompat.getDrawable(context, R.drawable.ic_mark_drag)
        }

        // bounds Paint
        private val boundsPaint = Paint().apply {
            isAntiAlias = true
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = SELECTED_LINE_WIDTH
//            pathEffect = DashPathEffect(floatArrayOf(10f, 7f), 0f) // 虚线
        }

        /**
         * 画mark的边框
         */
        fun draw(canvas: Canvas, mark: MarkCorrect) {
            getMarkBounds(mark, rectF)
            if (!rectF.isEmpty) {
                // 画框
                canvas.drawRect(rectF, boundsPaint)
                val l = rectF.left
                val t = rectF.top
                val r = rectF.right

                // 画delete按钮
                getButtonBounds(r, t, rectF)
                delIcon?.setBounds(
                    rectF.left.toInt(),
                    rectF.top.toInt(),
                    rectF.right.toInt(),
                    rectF.bottom.toInt()
                )
                delIcon?.draw(canvas)

                // 画drag按钮
                if (mark.isTypePath) {
                    getButtonBounds(l, t, rectF)
                    dragIcon?.setBounds(
                        rectF.left.toInt(),
                        rectF.top.toInt(),
                        rectF.right.toInt(),
                        rectF.bottom.toInt()
                    )
                    dragIcon?.draw(canvas)
                }
            }
        }

        /**
         * 获取以x、y点为中心的按钮区域
         */
        fun getButtonBounds(x: Float, y: Float, rect: RectF) {
            val r = BUTTON_DIAMETER shr 1
            rect.set(x - r, y - r, x + r, y + r)
        }
    }

    /**
     * 操作记录
     * @author junpu
     * @date 2021/3/11
     */
    private inner class OperationRecords {

        private val recordList = arrayListOf<MarkRecord>() // 操作记录
        private var operateIndex = -1 // 当前操作的记录下标

        /**
         * 添加 [RecordType.ADD] 类型记录
         */
        fun addAddRecord(mark: MarkCorrect) {
            addRecord(mark, RecordType.ADD)
        }

        /**
         * 添加 [RecordType.DELETE] 类型记录
         */
        fun addDeleteRecord(mark: MarkCorrect, index: Int) {
            addRecord(mark, RecordType.DELETE, index)
        }

        /**
         * 添加 [RecordType.MOVE] 类型的记录
         */
        fun addMoveRecord(mark: MarkCorrect) {
            var x = mark.x
            var y = mark.y
            if (mark.isTypePath) {
                pathMark.getBounds(mark, rectF)
                x = rectF.left
                y = rectF.top
            }
            addRecord(mark, RecordType.MOVE, x = x, y = y)
        }

        /**
         * 添加 [RecordType.SCALE] 类型的记录
         */
        fun addScaleRecord(mark: MarkCorrect) {
            addRecord(mark, RecordType.SCALE, scale = mark.scale)
        }

        /**
         * 添加 [RecordType.ADD_SEGMENTS] 类型记录
         */
        fun addSegmentsRecord(mark: MarkCorrect) {
            addRecord(mark, RecordType.ADD_SEGMENTS)
        }

        /**
         * 添加记录
         */
        private fun addRecord(
            mark: MarkCorrect,
            type: RecordType,
            index: Int = 0,
            x: Float = 0f,
            y: Float = 0f,
            scale: Float = 1f
        ) {
            val record = MarkRecord(type, mark, index, x, y, scale, rotation = rotation)
            // 如果下标不在最后，先移除下标后的元素，再添加
            if (operateIndex < recordList.lastIndex && operateIndex + 1 >= 0) {
                for (i in recordList.lastIndex downTo operateIndex + 1) {
                    recordList.removeAt(i)
                }
            }
            recordList.add(record)
            operateIndex = recordList.lastIndex
        }

        /**
         * 旋转Mark的x, y坐标
         */
        private fun rotateMarkPoint(mark: MarkCorrect, rotation: Int) {
            pointRotation(mark.x, mark.y, arr, rotation)
            mark.x = arr[0]
            mark.y = arr[1]
        }

        /**
         * 撤销，[undo]和[redo]中包含了大量[rotation]旋转坐标的逻辑，如果不需要旋转，那逻辑会很简洁。
         */
        fun undo() {
            if (recordList.isNullOrEmpty() || operateIndex !in recordList.indices) return
            val record = recordList[operateIndex--]
            val mark = record.mark
            val r = adjustRotation(rotation - record.rotation)
            when (record.type) {
                RecordType.ADD -> markList.remove(mark)
                RecordType.DELETE -> {
                    if (r != 0) {
                        if (mark.isTypePath) {
                            mark.segments?.forEach { seg ->
                                seg.points?.forEach {
                                    pointRotation(it.x, it.y, arr, r)
                                    it.set(arr[0], arr[1])
                                }
                            }
                        } else {
                            rotateMarkPoint(mark, r)
                        }
                    }
                    markList.add(record.index, mark)
                }
                RecordType.MOVE -> {
                    if (mark.isTypePath) {
                        getMarkBounds(mark, rectF)
                        pointF.set(rectF.left, rectF.top)
                        pointRotation(record.x, record.y, arr, r)
                        var x = arr[0]
                        var y = arr[1]
                        if (r == 1 || r == 2) x -= rectF.width()
                        if (r == 2 || r == 3) y -= rectF.height()
                        val tx = x - rectF.left
                        val ty = y - rectF.top
                        mark.segments?.forEach { seg ->
                            seg.points?.forEach { it.offset(tx, ty) }
                        }
                    } else {
                        pointF.set(mark.x, mark.y)
                        pointRotation(record.x, record.y, arr, r)
                        mark.x = arr[0]
                        mark.y = arr[1]
                    }
                    record.x = pointF.x
                    record.y = pointF.y
                }
                RecordType.SCALE -> {
                    if (!mark.isTypePath) {
                        val scale = mark.scale
                        mark.scale = record.scale
                        record.scale = scale
                    }
                }
                RecordType.ADD_SEGMENTS -> record.segments = mark.segments?.removeLast()
            }
            record.rotation = rotation
        }

        /**
         * 重做
         */
        fun redo() {
            if (recordList.isNullOrEmpty() || operateIndex + 1 !in recordList.indices) return
            val record = recordList[++operateIndex]
            val mark = record.mark
            val r = adjustRotation(rotation - record.rotation)
            when (record.type) {
                RecordType.ADD -> {
                    if (r != 0) {
                        if (mark.isTypePath) {
                            mark.segments?.forEach { seg ->
                                seg.points?.forEach {
                                    pointRotation(it.x, it.y, arr, r)
                                    it.set(arr[0], arr[1])
                                }
                            }
                        } else {
                            if (r != 0) rotateMarkPoint(mark, r)
                        }
                    }
                    markList.add(mark)
                }
                RecordType.DELETE -> markList.remove(mark)
                RecordType.MOVE -> {
                    if (mark.isTypePath) {
                        getMarkBounds(mark, rectF)
                        pointF.set(rectF.left, rectF.top)
                        pointRotation(record.x, record.y, arr, r)
                        var x = arr[0]
                        var y = arr[1]
                        if (r == 1 || r == 2) x -= rectF.width()
                        if (r == 2 || r == 3) y -= rectF.height()
                        val tx = x - rectF.left
                        val ty = y - rectF.top
                        mark.segments?.forEach { seg ->
                            seg.points?.forEach { it.offset(tx, ty) }
                        }
                    } else {
                        pointF.set(mark.x, mark.y)
                        pointRotation(record.x, record.y, arr, r)
                        mark.x = arr[0]
                        mark.y = arr[1]
                    }
                    record.x = pointF.x
                    record.y = pointF.y
                }
                RecordType.SCALE -> {
                    if (!mark.isTypePath) {
                        val scale = mark.scale
                        mark.scale = record.scale
                        record.scale = scale
                    }
                }
                RecordType.ADD_SEGMENTS -> {
                    record.segments?.let { seg ->
                        if (r != 0) {
                            seg.points?.forEach {
                                pointRotation(it.x, it.y, arr, r)
                                it.set(arr[0], arr[1])
                            }
                        }
                        mark.segments?.add(seg)
                    }
                }
            }
            record.rotation = rotation
        }

        /**
         * 清理记录
         */
        fun clear() {
            recordList.clear()
            recordList.trimToSize()
            operateIndex = -1
        }
    }

    /**
     * 记录类型
     * @author junpu
     * @date 2021/3/10
     */
    enum class RecordType {
        ADD, // 增加
        DELETE, // 删除
        MOVE, // 移动
        SCALE, // 缩放
        ADD_SEGMENTS, // 添加片段
    }

    companion object {
        // Mark标记缩放范围
        const val MARK_SCALE_MAX = 1.5f
        const val MARK_SCALE_MIN = .5f

        // 标记类型
        private const val MARK_TYPE_SYMBOL = "symbol"
        private const val MARK_TYPE_TEXT = "text"
        private const val MARK_TYPE_DRAWING = "drawing"

        // 符号类型
        private const val SYMBOL_TYPE_RIGHT = "correct"
        private const val SYMBOL_TYPE_WRONG = "wrong"

        // Mark的默认大小
        private const val TEXT_SIZE = 30 // 默认文字大小
        private const val SYMBOL_RIGHT_WIDTH = 96 // 默认对勾宽度
        private const val SYMBOL_RIGHT_HEIGHT = 64 // 默认对勾高度
        private const val SYMBOL_WRONG_WIDTH = 44 // 默认错叉宽度
        private const val SYMBOL_WRONG_HEIGHT = 40 // 默认错叉高度
        private const val PATH_LINE_WIDTH = 3f // 默认path线宽
        private const val SELECTED_LINE_WIDTH = 3f // 默认selected线宽
        private const val BUTTON_DIAMETER = 30 // 默认删除、拖动按钮直径
        private const val TOUCH_OFFSET = 10 // 触摸误差
    }

}
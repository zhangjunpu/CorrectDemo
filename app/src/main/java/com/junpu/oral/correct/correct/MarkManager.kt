package com.junpu.oral.correct.correct

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import androidx.core.content.ContextCompat
import com.junpu.log.L
import com.junpu.oral.correct.R
import com.junpu.oral.correct.module.CorrectMark
import com.junpu.oral.correct.module.MarkRecord
import com.junpu.oral.correct.module.PathPoint
import kotlin.math.max
import kotlin.math.min

/**
 * 标记管理
 * @author junpu
 * @date 2021/2/25
 */
class MarkManager(private var context: Context) {

    private val symbolMark by lazy { SymbolMark() }
    private val textMark by lazy { TextMark() }
    private val pathMark by lazy { PathMark() }
    private val selectedMark by lazy { SelectedMark() }
    private val recordManager by lazy { OperationRecords() }

    // 图片基本数据
    private var bitmapWidth: Int = 0
    private var bitmapHeight: Int = 0
    var translateX = 0f // 当前移动X轴距离
    var translateY = 0f // 当前移动Y轴距离
    var scale = 1f // 当前缩放系数
        set(value) {
            field = min(IMAGE_SCALE_MAX, max(IMAGE_SCALE_MIN, value))
        }

    // Mark缩放系数
    private var markScale = 1f
        set(value) {
            field = min(MARK_SCALE_MAX, max(MARK_SCALE_MIN, value))
        }

    // mark数据
    private var markId = 0 // markId 自增
    private val markList = arrayListOf<CorrectMark>() // mark数据队列
    private var curIndex = -1 // 当前选中
    private val curMark: CorrectMark? // 当前选中Mark
        get() = if (curIndex in markList.indices) markList[curIndex] else null

    // 临时内存地址指针
    private var pointF = PointF() // 存放坐标转换的临时空间
    private var rectF = RectF() // 存放边界的临时空间
    private var lockedMark: CorrectMark? = null // 处理中的mark指针
    private var lockedPoints: MutableList<PointF>? = null // 操作中的PathPoint

    /**
     * 初始化赋值
     */
    fun init(bitmapWidth: Int, bitmapHeight: Int, scale: Float, tx: Float, ty: Float) {
        this.bitmapWidth = bitmapWidth
        this.bitmapHeight = bitmapHeight
        this.scale = scale
        this.translateX = tx
        this.translateY = ty
    }

    /**
     * 生成新标记
     */
    fun generateRight(x: Float, y: Float) = addMark(symbolMark.newMark(x, y, SYMBOL_TYPE_RIGHT))
    fun generateWrong(x: Float, y: Float) = addMark(symbolMark.newMark(x, y, SYMBOL_TYPE_WRONG))
    fun generateText(x: Float, y: Float, text: String) = addMark(textMark.newMark(x, y, text))
    fun generatePath(x: Float, y: Float) = addMark(pathMark.newMark(x, y))
    private fun addMark(mark: CorrectMark?) {
        mark ?: return
        markList.add(mark)
        recordManager.addAddRecord(mark)
        curIndex = markList.lastIndex
    }

    /**
     * 画mark
     */
    fun draw(canvas: Canvas) {
        markList.forEachIndexed { index, it ->
            when (it.type) {
                MARK_TYPE_SYMBOL -> symbolMark.draw(canvas, it)
                MARK_TYPE_TEXT -> textMark.draw(canvas, it)
                MARK_TYPE_DRAWING -> pathMark.draw(canvas, it)
            }
            // 当前选中的mark，画选中框
            if (index == curIndex) {
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
        val scale = MARK_SCALE_MIN + percent * (MARK_SCALE_MAX - MARK_SCALE_MIN)
        markScale = scale
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
            getMarkBoundsInView(mark, rectF)
            val x = rectF.left + tx
            val y = rectF.top + ty
            if (checkOutOfBoundsInView(x, y, rectF.width(), rectF.height())) return
            mark.segments?.forEach { it ->
                it.points?.forEach { it.offset(tx / scale, ty / scale) }
            }
        } else {
            toViewPoint(mark.x, mark.y, pointF)
            val x = pointF.x + tx
            val y = pointF.y + ty
            // 边界检测
            getMarkBoundsInView(mark, rectF)
            if (checkOutOfBoundsInView(x, y, rectF.width(), rectF.height())) return
            toMarkPoint(x, y, pointF)
            mark.x = pointF.x
            mark.y = pointF.y
        }
    }

    /**
     * 添加路径
     */
    fun addPathPoint(x: Float, y: Float, tx: Float, ty: Float, isFirst: Boolean) {
        val mark = lockedMark ?: return
        if (mark.isTypePath) {
            // 边界范围检测
            var px = x
            var py = y
            getBitmapBoundsInView(rectF)
            if (!rectF.contains(px, py)) {
                if (px < rectF.left) px = rectF.left else if (px > rectF.right) px = rectF.right
                if (py < rectF.top) py = rectF.top else if (py > rectF.bottom) py = rectF.bottom
            }
            toMarkPoint(px, py, pointF)
            lockedPoints?.add(PointF(pointF.x, pointF.y))
        } else {
            translateMark(tx, ty, isFirst)
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
        getMarkBoundsInView(mark, rectF)
        val l = rectF.left
        val t = rectF.top
        val r = rectF.right
        val offset = TOUCH_OFFSET * scale // 触摸误差范围

        // 判断x, y是否触摸到了指定的rect区域内
        fun isTouched(rect: RectF): Boolean {
            rect.inset(-offset, -offset) // 增加触摸误差范围
            return rect.contains(x, y)
        }

        // 预留判断是否点到了当前选中标记上
        val isTouchedCurMark = isTouched(rectF)

        // 判断是否点到了删除按钮
        selectedMark.getButtonBoundsInView(r, t, rectF)
        if (isTouched(rectF)) return TouchArea.DELETE

        // 判断是否触摸到了拖动按钮（只有path模式拥有拖动按钮）
        if (mark.isTypePath) {
            selectedMark.getButtonBoundsInView(l, t, rectF)
            if (isTouched(rectF)) return TouchArea.DRAG
        }

        // 触摸到了当前标记上
        if (isTouchedCurMark) return TouchArea.MARK

        // 判断是否点击到了某个按钮
        for (i in markList.lastIndex downTo 0) {
            if (mark == markList[i]) continue
            getMarkBoundsInView(markList[i], rectF)
            if (isTouched(rectF)) {
                curIndex = i
                return TouchArea.MARK
            }
        }
        return TouchArea.NONE
    }

    /**
     * 边界检测，在View范围内
     */
    private fun checkOutOfBoundsInView(x: Float, y: Float, w: Float = 0f, h: Float = 0f): Boolean {
        getBitmapBoundsInView(rectF)
        L.vv(rectF)
        return !rectF.contains(x, y, x + w, y + h)
    }

    /**
     * 获取位图在View上的边界
     */
    private fun getBitmapBoundsInView(rect: RectF) {
        val l = translateX
        val t = translateY
        val r = translateX + bitmapWidth * scale
        val b = translateY + bitmapHeight * scale
        rect.set(l, t, r, b)
    }

    /**
     * 坐标转换，从mark坐标到view坐标
     */
    private fun toViewPoint(x: Float, y: Float, point: PointF) {
        val ax = x * scale + translateX
        val ay = y * scale + translateY
        point.set(ax, ay)
    }

    /**
     * 坐标转换，从view坐标到mark坐标
     */
    private fun toMarkPoint(x: Float, y: Float, point: PointF) {
        val rax = (x - translateX) / scale
        val ray = (y - translateY) / scale
        point.set(rax, ray)
    }

    /**
     * 获取mark在View上的边界
     */
    private fun getMarkBoundsInView(mark: CorrectMark, rect: RectF) {
        when (mark.type) {
            MARK_TYPE_SYMBOL -> symbolMark.getBoundsInView(mark, rect)
            MARK_TYPE_TEXT -> textMark.getBoundsInView(mark, rect)
            MARK_TYPE_DRAWING -> pathMark.getBoundsInView(mark, rect)
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
        selectedMark.release()
        markList.clear()
    }

    /**
     * 旋转
     */
    fun rotate(degree: Float) {
        val x = translateX + bitmapWidth * scale / 2
        val y = translateY + bitmapHeight * scale / 2
        // TODO: 2021/3/12
    }

    /**
     * 是否为path模式
     */
    private val CorrectMark.isTypePath: Boolean get() = type == MARK_TYPE_DRAWING

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
        fun newMark(x: Float, y: Float, symbol: String?): CorrectMark? {
            if (checkOutOfBoundsInView(x, y)) return null
            toMarkPoint(x, y, pointF)
            val rx = pointF.x
            val ry = pointF.y
            return CorrectMark(markId++, MARK_TYPE_SYMBOL, rx, ry, markScale, symbol)
        }

        /**
         * 画勾、叉
         */
        fun draw(canvas: Canvas, mark: CorrectMark) {
            getSizeInView(mark, pointF)
            val w = pointF.x
            val h = pointF.y
            toViewPoint(mark.x, mark.y, pointF)
            val l = pointF.x
            val t = pointF.y
            val r = l + w
            val b = t + h
            val symbol = if (mark.symbol == SYMBOL_TYPE_RIGHT) symbolRight else symbolWrong
            symbol?.setBounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
            symbol?.draw(canvas)
        }

        /**
         * 获取mark在View上的边界
         */
        fun getBoundsInView(mark: CorrectMark, rect: RectF) {
            rect.setEmpty()
            getSizeInView(mark, pointF)
            val w = pointF.x
            val h = pointF.y
            toViewPoint(mark.x, mark.y, pointF)
            val x = pointF.x
            val y = pointF.y
            rect.set(x, y, x + w, y + h)
        }

        /**
         * 获取Mark的边界
         */
        fun getBounds(mark: CorrectMark, rect: RectF) {
            rect.setEmpty()
            getSize(mark, pointF)
            val w = pointF.x
            val h = pointF.y
            val x = mark.x
            val y = mark.y
            rect.set(x, y, x + w, y + h)
        }

        /**
         * 获取符号在View上的尺寸
         */
        private fun getSizeInView(mark: CorrectMark, rect: PointF) {
            getSize(mark, rect)
            val w = rect.x
            val h = rect.y
            rect.set(w * scale, h * scale)
        }

        /**
         * 获取符号的尺寸
         */
        private fun getSize(mark: CorrectMark, rect: PointF) {
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
        fun newMark(x: Float, y: Float, text: String): CorrectMark? {
            if (checkOutOfBoundsInView(x, y)) return null
            toMarkPoint(x, y, pointF)
            val rx = pointF.x
            val ry = pointF.y
            return CorrectMark(markId++, MARK_TYPE_TEXT, rx, ry, markScale, text = text)
        }

        /**
         * 画文字
         */
        fun draw(canvas: Canvas, mark: CorrectMark) {
            if (mark.text.isNullOrBlank()) return
            toViewPoint(mark.x, mark.y, pointF)
            val x = pointF.x
            val y = pointF.y
            val texts = mark.getTextForEachLine()
            val textSize = TEXT_SIZE * mark.scale * scale
            textPaint.textSize = textSize
            val metrics = textPaint.fontMetrics // baseline相关参数
            texts.forEachIndexed { index, s ->
                // 由于drawText是从baseline坐标开始画的，所以需要向下偏移一行baseline到文字顶部的高度，+2为微调
                val offsetY = (index + 1) * textSize - metrics.descent + 2
                canvas.drawText(s, x, y + offsetY, textPaint)
            }
        }

        /**
         * 获取mark在View上的边界
         */
        fun getBoundsInView(mark: CorrectMark, rect: RectF) {
            rect.setEmpty()
            getSizeInView(mark, pointF)
            val w = pointF.x
            val h = pointF.y
            toViewPoint(mark.x, mark.y, pointF)
            val x = pointF.x
            val y = pointF.y
            rect.set(x, y, x + w, y + h)
        }

        /**
         * 获取mark的边界
         */
        fun getBounds(mark: CorrectMark, rect: RectF) {
            rect.setEmpty()
            getSize(mark, pointF)
            val w = pointF.x
            val h = pointF.y
            val x = mark.x
            val y = mark.y
            rect.set(x, y, x + w, y + h)
        }

        /**
         * 获取文字在View上的尺寸
         */
        private fun getSizeInView(mark: CorrectMark, rect: PointF) {
            getSize(mark, rect)
            val w = rect.x
            val h = rect.y
            rect.set(w * scale, h * scale)
        }

        /**
         * 获取文字的尺寸
         */
        private fun getSize(mark: CorrectMark, rect: PointF) {
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
        private fun CorrectMark.getTextForEachLine(): List<String> {
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
        fun newMark(x: Float, y: Float): CorrectMark? {
            if (checkOutOfBoundsInView(x, y)) return null
            toMarkPoint(x, y, pointF)
            val rx = pointF.x
            val ry = pointF.y
            val segments = arrayListOf(newPathPoint)
            return CorrectMark(markId++, MARK_TYPE_DRAWING, rx, ry, markScale, segments = segments)
        }

        /**
         * 画path
         */
        fun draw(canvas: Canvas, mark: CorrectMark) {
            if (mark.segments.isNullOrEmpty()) return
            for (it in mark.segments!!) {
                if (it.points.isNullOrEmpty()) continue
                // 画path
                path.run {
                    reset()
                    it.points?.forEachIndexed { index, point ->
                        toViewPoint(point.x, point.y, pointF)
                        val x = pointF.x
                        val y = pointF.y
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                canvas.drawPath(path, pathPaint.apply { strokeWidth = it.lineWidth * scale })
            }
        }

        /**
         * 获取mark在View上的边界
         */
        fun getBoundsInView(mark: CorrectMark, rect: RectF) {
            getBounds(mark, rect)
            toViewPoint(rect.left, rect.top, pointF)
            val l = pointF.x
            val t = pointF.y
            toViewPoint(rect.right, rect.bottom, pointF)
            val r = pointF.x
            val b = pointF.y
            rect.set(l, t, r, b)
        }

        /**
         * 获取Mark的边界
         */
        fun getBounds(mark: CorrectMark, rect: RectF) {
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
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_mark_del)
        }

        // 拖拽
        private val dragIcon by lazy {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_mark_drag)
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
        fun draw(canvas: Canvas, mark: CorrectMark) {
            getMarkBoundsInView(mark, rectF)
            if (!rectF.isEmpty) {
                // 画框
                canvas.drawRect(rectF, boundsPaint)
                val l = rectF.left
                val t = rectF.top
                val r = rectF.right

                // 画delete按钮
                getButtonBoundsInView(r, t, rectF)
                val srcDel = Rect(0, 0, delIcon.width, delIcon.height)
                canvas.drawBitmap(delIcon, srcDel, rectF, null)

                if (mark.isTypePath) {
                    // 画drag按钮
                    getButtonBoundsInView(l, t, rectF)
                    val srcDrag = Rect(0, 0, dragIcon.width, dragIcon.height)
                    canvas.drawBitmap(dragIcon, srcDrag, rectF, null)
                }
            }
        }

        /**
         * 获取以x、y点为中心的删除按钮区域
         */
        fun getButtonBoundsInView(x: Float, y: Float, rect: RectF) {
            val d = BUTTON_DIAMETER * scale
            val r = d.toInt() shr 1
            rect.set(x - r, y - r, x + r, y + r)
        }

        /**
         * 释放
         */
        fun release() {
            delIcon.recycle()
        }
    }

    /**
     * 操作记录
     * @author junpu
     * @date 2021/3/11
     */
    private inner class OperationRecords {

        private var recordId = 0 // recordId 自增
        private val recordList = arrayListOf<MarkRecord>() // 操作记录
        private var operateIndex = -1 // 当前操作的记录下标

        /**
         * 添加 [RecordType.ADD] 类型记录
         */
        fun addAddRecord(mark: CorrectMark) {
            addRecord(mark, RecordType.ADD)
        }

        /**
         * 添加 [RecordType.DELETE] 类型记录
         */
        fun addDeleteRecord(mark: CorrectMark, index: Int) {
            addRecord(mark, RecordType.DELETE, index)
        }

        /**
         * 添加 [RecordType.MOVE] 类型的记录
         */
        fun addMoveRecord(mark: CorrectMark) {
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
        fun addScaleRecord(mark: CorrectMark) {
            addRecord(mark, RecordType.SCALE, scale = mark.scale)
        }

        /**
         * 添加 [RecordType.ADD_SEGMENTS] 类型记录
         */
        fun addSegmentsRecord(mark: CorrectMark) {
            addRecord(mark, RecordType.ADD_SEGMENTS)
        }

        /**
         * 添加记录
         */
        private fun addRecord(
            mark: CorrectMark,
            type: RecordType,
            index: Int = 0,
            x: Float = 0f,
            y: Float = 0f,
            scale: Float = 1f
        ) {
            val record = MarkRecord(recordId++, type, mark, index, x, y, scale)
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
         * 撤销
         */
        fun undo() {
            if (recordList.isNullOrEmpty() || operateIndex !in recordList.indices) return
            val record = recordList[operateIndex--]
            val mark = record.mark
            when (record.type) {
                RecordType.ADD -> markList.remove(mark)
                RecordType.DELETE -> markList.add(record.index, mark)
                RecordType.MOVE -> {
                    if (mark.isTypePath) {
                        pathMark.getBounds(mark, rectF)
                        pointF.set(rectF.left, rectF.top)
                        val tx = record.x - rectF.left
                        val ty = record.y - rectF.top
                        mark.segments?.forEach { it ->
                            it.points?.forEach { it.offset(tx, ty) }
                        }
                    } else {
                        pointF.set(mark.x, mark.y)
                        mark.x = record.x
                        mark.y = record.y
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
        }

        /**
         * 重做
         */
        fun redo() {
            if (recordList.isNullOrEmpty() || operateIndex + 1 !in recordList.indices) return
            val record = recordList[++operateIndex]
            val mark = record.mark
            when (record.type) {
                RecordType.ADD -> markList.add(mark)
                RecordType.DELETE -> markList.remove(mark)
                RecordType.MOVE -> {
                    if (mark.isTypePath) {
                        pathMark.getBounds(mark, rectF)
                        pointF.set(rectF.left, rectF.top)
                        val tx = record.x - rectF.left
                        val ty = record.y - rectF.top
                        mark.segments?.forEach { it ->
                            it.points?.forEach { it.offset(tx, ty) }
                        }
                    } else {
                        pointF.set(mark.x, mark.y)
                        mark.x = record.x
                        mark.y = record.y
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
                RecordType.ADD_SEGMENTS -> record.segments?.let { mark.segments?.add(it) }
            }
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
     * 触摸区域
     * @author junpu
     * @date 2021/3/11
     */
    enum class TouchArea {
        NONE, // 空白
        MARK, // 标记
        DRAG, // 拖动按钮
        DELETE, // 删除按钮
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
        // 图片缩放范围
        const val IMAGE_SCALE_MAX = 3f
        const val IMAGE_SCALE_MIN = .3f

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
        private const val TEXT_SIZE = 75 // 默认文字大小
        private const val SYMBOL_RIGHT_WIDTH = 240 // 默认对勾宽度
        private const val SYMBOL_RIGHT_HEIGHT = 160 // 默认对勾高度
        private const val SYMBOL_WRONG_WIDTH = 110 // 默认错叉宽度
        private const val SYMBOL_WRONG_HEIGHT = 100 // 默认错叉高度
        private const val PATH_LINE_WIDTH = 7.5f // 默认path线宽
        private const val SELECTED_LINE_WIDTH = 3.5f // 默认selected线宽
        private const val BUTTON_DIAMETER = 80 // 默认删除图标尺寸
        private const val TOUCH_OFFSET = 20 // 触摸误差
    }

}
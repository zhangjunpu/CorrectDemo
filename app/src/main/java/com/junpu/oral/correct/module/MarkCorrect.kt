package com.junpu.oral.correct.module

import android.graphics.PointF

/**
 * 批改标记
 * @author junpu
 * @date 2021/2/25
 */
data class MarkCorrect(
    var type: String?, // 类型 symbol、text、drawing
    var x: Float,
    var y: Float,
    var scale: Float,
    var symbol: String? = null, // 符号类型 勾、叉
    var text: String? = null,
    var width: Int = 0, // 文字宽度
    var segments: ArrayList<PathPoint>? = null, // drawing路径
    var rotation: Int = 0, // 旋转角度0，1，2，3 -> 0，90，180，270
)

data class PathPoint(
    var points: ArrayList<PointF>?,
    var lineWidth: Float
)

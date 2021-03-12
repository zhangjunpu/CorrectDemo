package com.junpu.oral.correct.module

import android.graphics.PointF

/**
 * 批改标记
 * @author junpu
 * @date 2021/2/25
 */
data class CorrectMark(
    var id: Int,
    var type: String?, // 类型 symbol、text、drawing
    var x: Float,
    var y: Float,
    var scale: Float,
    var symbol: String? = null, // 符号类型 勾、叉
    var text: String? = null,
    var width: Int = 0, // 文字宽度
    var segments: ArrayList<PathPoint>? = null, // drawing路径
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CorrectMark

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id
    }
}

data class PathPoint(
    var points: ArrayList<PointF>?,
    var lineWidth: Float
)

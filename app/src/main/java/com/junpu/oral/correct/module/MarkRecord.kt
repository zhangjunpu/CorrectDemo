package com.junpu.oral.correct.module

import com.junpu.oral.correct.correct.MarkCorrectManager

/**
 * 操作记录
 * @author junpu
 * @date 2021/3/10
 */
data class MarkRecord(
    var type: MarkCorrectManager.RecordType, // 操作类型
    var mark: MarkCorrect, // 操作的mark
    var index: Int = 0, // DELETE 操作前 mark 所在列表的位置
    var x: Float = 0f, // MOVE 操作之前的x
    var y: Float = 0f, // MOVE 操作之前的y
    var scale: Float = 1f, // SCALE 操作前 mark 的 scale 值
    var segments: PathPoint? = null, // 片段
    var rotation: Int,
)

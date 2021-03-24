package com.junpu.oral.correct.core.module

import android.graphics.PointF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 错题整理标记
 * @author junpu
 * @date 2021/3/18
 */
@Parcelize
data class MarkPoint(
    var width: Int,
    var height: Int,
    var location: List<PointF>?
) : Parcelable
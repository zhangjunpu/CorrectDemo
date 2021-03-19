package com.junpu.oral.correct.utils

import android.graphics.Bitmap
import android.util.Size

/**
 *
 * @author junpu
 * @date 2021/2/23
 */

/**
 * 等比缩放原始尺寸为目标尺寸
 * @param width 原始宽度
 * @param height 原始高度
 * @param maxWidth 最大限制宽度，默认1600
 * @param maxHeight 最大限制高度，默认1600
 * @param isResizeMini 如果原始尺寸小于目标尺寸，是否缩放的，默认false
 */
fun resizeImage(
    width: Int,
    height: Int,
    maxWidth: Int = 1600,
    maxHeight: Int = 1600,
    isResizeMini: Boolean = false
): Size {
    val srcRatio = width / height.toFloat()
    val destRatio = maxWidth / maxHeight.toFloat()
    var w = width
    var h = height
    if (srcRatio >= destRatio && (width > maxWidth || isResizeMini)) {
        w = maxWidth
        h = (maxWidth / srcRatio).toInt()
    } else if (srcRatio < destRatio && (height > maxHeight || isResizeMini)) {
        h = maxHeight
        w = (maxHeight * srcRatio).toInt()
    }
    return Size(w, h)
}

fun Bitmap.contentString() = "$width/$height"
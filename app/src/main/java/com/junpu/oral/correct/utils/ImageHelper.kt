package com.junpu.oral.correct.utils

import android.util.Size

/**
 *
 * @author junpu
 * @date 2021/2/23
 */

/**
 * 等比缩放原始尺寸为目标尺寸
 * @param {Number} srcWidth 原始宽度
 * @param {Number} srcHeight 原始高度
 * @param {Number} destWidth 目标宽度，默认1600
 * @param {Number} destHeight 目标高度，默认1600
 * @param {Boolean} isResizeMini 如果原始尺寸小于目标尺寸，是否缩放的，默认false
 */
fun resizeImage(
    srcWidth: Int,
    srcHeight: Int,
    destWidth: Int = 1600,
    destHeight: Int = 1600,
    isResizeMini: Boolean = false
): Size {
    val srcRatio = srcWidth / srcHeight.toFloat()
    val destRatio = destWidth / destHeight.toFloat()
    var width = srcWidth
    var height = srcHeight
    if (srcRatio >= destRatio && (srcWidth > destWidth || isResizeMini)) {
        width = destWidth
        height = (destWidth / srcRatio).toInt()
    } else if (srcRatio < destRatio && (srcHeight > destHeight || isResizeMini)) {
        height = destHeight
        width = (destHeight * srcRatio).toInt()
    }
    return Size(width, height)
}
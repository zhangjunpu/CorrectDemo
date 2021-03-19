package com.junpu.oral.correct

import android.graphics.Bitmap

/**
 * 缓存类
 * @author junpu
 * @date 2021/3/18
 */
object Cache {
    var srcBitmap: Bitmap? = null // 原图
    var binBitmap: Bitmap? = null // 二值化图
    var orientation: Int = 0 // 图片方向

    // 预览Bitmap
    var previewBitmap: Bitmap? = null
}
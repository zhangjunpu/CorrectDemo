@file:JvmName("CallbackHelper")

package com.junpu.oral.correct.utils

import android.widget.SeekBar

/**
 * 回调
 * @author junpu
 * @date 2021/3/9
 */

inline fun SeekBar.doOnSeekBarChange(
    crossinline onProgressChanged: (seekBar: SeekBar?, progress: Int, fromUser: Boolean) -> Unit = { _, _, _ -> },
    crossinline onStartTrackingTouch: (seekBar: SeekBar?) -> Unit = {},
    crossinline onStopTrackingTouch: (seekBar: SeekBar?) -> Unit = {},
): SeekBar.OnSeekBarChangeListener {
    val listener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            onProgressChanged.invoke(seekBar, progress, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            onStartTrackingTouch.invoke(seekBar)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            onStopTrackingTouch.invoke(seekBar)
        }
    }
    setOnSeekBarChangeListener(listener)
    return listener
}

inline fun SeekBar.doOnProgressChanged(
    crossinline action: (seekBar: SeekBar?, progress: Int, fromUser: Boolean) -> Unit
) = doOnSeekBarChange(onProgressChanged = action)

inline fun SeekBar.doOnStartTrackingTouch(
    crossinline action: (seekBar: SeekBar?) -> Unit
) = doOnSeekBarChange(onStartTrackingTouch = action)

inline fun SeekBar.doOnStopTrackingTouch(
    crossinline action: (seekBar: SeekBar?) -> Unit
) = doOnSeekBarChange(onStopTrackingTouch = action)
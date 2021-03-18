package com.junpu.oral.correct.ui

import android.os.Bundle
import android.view.View
import com.junpu.gopermissions.PermissionsActivity
import com.junpu.log.L
import com.junpu.oral.correct.Cache
import com.junpu.oral.correct.R
import com.junpu.oral.correct.correct.CorrectView
import com.junpu.oral.correct.databinding.ActivityCorrectBinding
import com.junpu.oral.correct.utils.doOnSeekBarChange
import com.junpu.utils.gone
import com.junpu.utils.launch
import com.junpu.utils.setVisibility
import com.junpu.utils.visible

class CorrectActivity : PermissionsActivity() {

    private lateinit var binding: ActivityCorrectBinding
    private var checkId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCorrectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.run {
            correctView.setGetText { binding.editText.text.toString() }
            val onRadioClick = { view: View ->
                val mode = if (checkId == view.id) {
                    checkId = -1
                    radioDrawMode.clearCheck()
                    CorrectView.Mode.NONE
                } else {
                    checkId = view.id
                    radioDrawMode.check(view.id)
                    when (view.id) {
                        R.id.btnRight -> CorrectView.Mode.RIGHT
                        R.id.btnWrong -> CorrectView.Mode.WRONG
                        R.id.btnPen -> CorrectView.Mode.PEN
                        R.id.btnText -> CorrectView.Mode.TEXT
                        else -> CorrectView.Mode.NONE
                    }
                }
                correctView.setMode(mode)
                editText.setVisibility(mode == CorrectView.Mode.TEXT)
                editText.text = null
                seekBar.setVisibility(mode != CorrectView.Mode.NONE)
            }
            btnRight.setOnClickListener(onRadioClick)
            btnWrong.setOnClickListener(onRadioClick)
            btnPen.setOnClickListener(onRadioClick)
            btnText.setOnClickListener(onRadioClick)

            var continuousScale = false
            seekBar.doOnSeekBarChange(
                onProgressChanged = { seekBar, progress, _ ->
                    seekBar ?: return@doOnSeekBarChange
                    val percent = progress / seekBar.max.toFloat()
                    correctView.setScalePercent(percent, continuousScale)
                    continuousScale = true
                },
                onStopTrackingTouch = {
                    continuousScale = false
                }
            )
            btnClear.setOnClickListener { correctView.clear() }
            btnUndo.setOnClickListener { correctView.undo() }
            btnRedo.setOnClickListener { correctView.redo() }
            btnRotateLeft.setOnClickListener {
                correctView.rotate(false)
            }
            btnRotateRight.setOnClickListener {
                correctView.rotate()
            }
            btnSave.setOnClickListener {
                correctView.toBitmap()?.let {
                    L.vv("save bitmap: ${it.width}/${it.height}")
                    Cache.previewBitmap = it
                    launch(ImagePreviewActivity::class.java)
                }
            }

            Cache.bitmap?.let { correctView.setBitmap(it) }
        }

    }

}
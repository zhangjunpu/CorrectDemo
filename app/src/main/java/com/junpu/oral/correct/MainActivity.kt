package com.junpu.oral.correct

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.core.view.drawToBitmap
import com.junpu.gopermissions.PermissionsActivity
import com.junpu.log.L
import com.junpu.oral.correct.correct.CorrectView
import com.junpu.oral.correct.databinding.ActivityMainBinding
import com.junpu.oral.correct.utils.doOnSeekBarChange
import com.junpu.utils.gone
import com.junpu.utils.setVisibility
import com.junpu.utils.visible

class MainActivity : PermissionsActivity() {

    private lateinit var binding: ActivityMainBinding
    private var checkId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
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
                    imageView.setImageBitmap(it)
                    layoutImage.visible()
                }
            }
            layoutImage.setOnClickListener {
                it.gone()
            }
        }

        checkPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            if (it) {
                initBitmap()
            } else {
                finish()
            }
        }

    }

    private fun initBitmap() {
        binding.correctView.post {
//            val bitmap = BitmapFactory.decodeResource(resources, R.raw.math_h)
            val bitmap = BitmapFactory.decodeResource(resources, R.raw.math_v)
//            val bitmap = BitmapFactory.decodeFile("/sdcard/Download/math_v.jpg")
            binding.correctView.setBitmap(bitmap)
        }
    }

}
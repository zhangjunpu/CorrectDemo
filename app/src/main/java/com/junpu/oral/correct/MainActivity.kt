package com.junpu.oral.correct

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.junpu.oral.correct.correct.CorrectView
import com.junpu.oral.correct.databinding.ActivityMainBinding
import com.junpu.oral.correct.utils.doOnSeekBarChange
import com.junpu.utils.setVisibility

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.run {
            correctView.setGetText { binding.editText.text.toString() }
            radioDrawMode.setOnCheckedChangeListener { _, checkedId ->
                val mode = when (checkedId) {
                    R.id.btnTouch -> CorrectView.Mode.NONE
                    R.id.btnRight -> CorrectView.Mode.RIGHT
                    R.id.btnWrong -> CorrectView.Mode.WRONG
                    R.id.btnPen -> CorrectView.Mode.PEN
                    R.id.btnText -> CorrectView.Mode.TEXT
                    else -> CorrectView.Mode.NONE
                }
                correctView.setMode(mode)
                editText.setVisibility(checkedId == R.id.btnText)
                editText.text = null
                seekBar.setVisibility(checkedId != R.id.btnTouch)
            }

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
        }

        binding.correctView.post {
//            val bitmap = BitmapFactory.decodeResource(resources, R.raw.math_h)
//            val bitmap = BitmapFactory.decodeResource(resources, R.raw.eng_h)
            val bitmap = BitmapFactory.decodeResource(resources, R.raw.math_l)
            binding.correctView.setBitmap(bitmap)
        }
    }

}
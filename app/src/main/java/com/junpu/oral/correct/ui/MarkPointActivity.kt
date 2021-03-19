package com.junpu.oral.correct.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.junpu.oral.correct.Cache
import com.junpu.oral.correct.databinding.ActivityMarkPointBinding
import com.junpu.utils.launch

/**
 * 标记错题
 * @author junpu
 * @date 2021/3/18
 */
class MarkPointActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMarkPointBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarkPointBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.run {
            btnRotateLeft.setOnClickListener {
                markView.rotate(false)
            }
            btnRotateRight.setOnClickListener {
                markView.rotate()
            }
            btnClear.setOnClickListener { markView.clear() }
            btnSave.setOnClickListener {
                markView.toBitmap()?.let {
                    Cache.previewBitmap = it
                    launch(ImagePreviewActivity::class.java)
                }
            }
            checkMarkDis.setOnCheckedChangeListener { _, isChecked ->
                markView.isMarkEnabled = !isChecked
            }
            btnSwitch.setOnClickListener {
                markView.switchBitmap()
            }
        }

        val srcBitmap = Cache.srcBitmap
        val binBitmap = Cache.binBitmap
        val orientation = Cache.orientation
        binding.markView.setBitmap(srcBitmap, binBitmap, orientation)
        binding.btnSwitch.isEnabled = binBitmap != null
    }
}
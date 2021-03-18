package com.junpu.oral.correct.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.junpu.oral.correct.Cache
import com.junpu.oral.correct.databinding.ActivityImagePreviewBinding

/**
 * 预览
 * @author junpu
 * @date 2021/3/18
 */
class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagePreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.run {
            photoView.setOnClickListener {
                finish()
                Cache.previewBitmap = null
            }
            Cache.previewBitmap?.let { photoView.setImageBitmap(it) }
        }
    }

}
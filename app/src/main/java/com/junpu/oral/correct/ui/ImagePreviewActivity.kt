package com.junpu.oral.correct.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.junpu.oral.correct.Cache
import com.junpu.oral.correct.R
import com.junpu.oral.correct.databinding.ActivityImagePreviewBinding
import com.junpu.oral.correct.databinding.ActivityImagePreviewItemBinding

/**
 * 预览
 * @author junpu
 * @date 2021/3/18
 */
class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagePreviewBinding
    private lateinit var onPageChangeCallback: ViewPager2.OnPageChangeCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val list = Cache.bitmaps
        onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.textIndex.text =
                    getString(R.string.image_preview_index, position + 1, list?.size ?: 0)
            }
        }
        binding.run {
            viewPager.registerOnPageChangeCallback(onPageChangeCallback)
            viewPager.adapter = ImageAdapter(list)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)
    }

    private class ImageAdapter(
        private var list: Array<Bitmap?>?
    ) : RecyclerView.Adapter<PhotoHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = ActivityImagePreviewItemBinding.inflate(layoutInflater, parent, false)
            return PhotoHolder(binding)
        }

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            getItem(position)?.let {
                holder.bindData(it)
            }
        }

        override fun getItemCount(): Int = list?.size ?: 0

        fun getItem(position: Int): Bitmap? = list?.get(position)
    }

    private class PhotoHolder(private val binding: ActivityImagePreviewItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindData(bitmap: Bitmap?) {
            bitmap?.let { binding.photoView.setImageBitmap(it) }
        }
    }

    companion object {
        var bitmaps: Array<Bitmap>? = null
    }

}
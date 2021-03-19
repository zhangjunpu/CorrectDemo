package com.junpu.oral.correct.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.junpu.oral.correct.Cache
import com.junpu.oral.correct.databinding.ActivityImagePreviewBinding
import com.junpu.oral.correct.databinding.ActivityImagePreviewItemBinding

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
            viewPager.adapter = ImageAdapter(Cache.bitmaps)
        }
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

}
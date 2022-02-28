package com.junpu.oral.correct.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import com.junpu.gopermissions.PermissionsActivity
import com.junpu.log.logStackTrace
import com.junpu.oral.correct.Cache
import com.junpu.oral.correct.R
import com.junpu.oral.correct.databinding.ActivityMainBinding
import com.junpu.toast.toast
import com.junpu.utils.launch
import java.io.FileNotFoundException

/**
 *
 * @author junpu
 * @date 2021/3/18
 */
class MainActivity : PermissionsActivity() {

    private lateinit var binding: ActivityMainBinding
    private var srcBitmap: Bitmap? = null
    private var binBitmap: Bitmap? = null
    private var orientation = 0
    private val option = BitmapFactory.Options().apply { inScaled = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.run {
            btnPhotoAlbum.setOnClickListener {
                openPhotoAlbum()
            }
            btnCorrect.setOnClickListener {
                gotoNext<CorrectActivity>()
            }
            btnMark.setOnClickListener {
                gotoNext<MarkPointActivity>()
            }
        }

        checkPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            if (!it) toast("你倒是给权限啊！！") else {
                try {
                    val srcBitmap = BitmapFactory.decodeResource(resources, R.raw.img, option)
                    val binBitmap = BitmapFactory.decodeResource(resources, R.raw.img_bin, option)
                    updateImage(srcBitmap, binBitmap, 90)
                } catch (e: Exception) {
                    e.logStackTrace()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_GOTO_MARK_PHOTO -> if (resultCode == RESULT_OK) finish()
            REQUEST_PHOTO_ALBUM -> data?.data?.let {
                try {
                    val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                    updateImage(bitmap)
                } catch (e: FileNotFoundException) {
                    e.logStackTrace()
                }
            }
        }
    }

    private fun updateImage(src: Bitmap?, bin: Bitmap? = null, orientation: Int = 0) {
        this.srcBitmap = src
        this.binBitmap = bin
        this.orientation = orientation
        val b = binBitmap ?: srcBitmap ?: return
        binding.imageView.setImageBitmap(b)
    }

    private inline fun <reified T : Activity> gotoNext() {
        if (srcBitmap == null) {
            toast("请先选择图片")
            return
        }
        Cache.srcBitmap = srcBitmap
        Cache.binBitmap = binBitmap
        Cache.orientation = orientation
        launch<T>()
    }

    /**
     * 打开本地相册
     */
    private fun openPhotoAlbum() {
        Intent(Intent.ACTION_PICK).run {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            val intent = Intent.createChooser(this, "选择图片")
            startActivityForResult(intent, REQUEST_PHOTO_ALBUM)
        }
    }

    companion object {
        private const val REQUEST_GOTO_MARK_PHOTO = 0x100
        private const val REQUEST_PHOTO_ALBUM = 0x101
    }
}
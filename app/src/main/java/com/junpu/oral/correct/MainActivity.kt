package com.junpu.oral.correct

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import com.junpu.gopermissions.PermissionsActivity
import com.junpu.log.logStackTrace
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.run {
            btnPhotoAlbum.setOnClickListener {
                openPhotoAlbum()
            }

            btnDefault.setOnClickListener {
                val bitmap = BitmapFactory.decodeResource(resources, R.raw.math_v)
                gotoCorrect(bitmap)
            }
        }

        checkPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            if (!it) toast("你倒是给权限啊！！")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_GOTO_MARK_PHOTO -> if (resultCode == RESULT_OK) finish()
            REQUEST_PHOTO_ALBUM -> data?.data?.let {
                try {
                    val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                    gotoCorrect(bitmap)
                } catch (e: FileNotFoundException) {
                    e.logStackTrace()
                }
            }
        }
    }

    private fun gotoCorrect(bitmap: Bitmap) {
        Cache.bitmap = bitmap
        launch(CorrectActivity::class.java)
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
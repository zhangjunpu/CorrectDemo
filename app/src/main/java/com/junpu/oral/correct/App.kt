package com.junpu.oral.correct

import android.app.Application
import com.junpu.log.L
import com.junpu.toast.toastContext
import com.junpu.utils.app

/**
 *
 * @author junpu
 * @date 2021/2/22
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        app = this
        L.logEnable = true
        toastContext = this
    }
}
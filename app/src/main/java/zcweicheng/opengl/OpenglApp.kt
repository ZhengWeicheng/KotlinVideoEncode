package zcweicheng.opengl

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import zcweicheng.opengl.JniInterface.NativeInterface

/**
 * Created by zhengweicheng on 2018/3/28 0028.
 */
open class OpenglApp : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var mContext:Context
    }

    override fun onCreate() {
        super.onCreate()
        Companion.mContext = this
        NativeInterface.init()
    }
}
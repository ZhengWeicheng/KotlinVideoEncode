package zcweicheng.opengl.JniInterface

/**
 * Created by zhengweicheng on 2018/3/28 0028.
 *
 */
object NativeInterface {

    fun init() {
        System.loadLibrary("openglLib")
    }
}
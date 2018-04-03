package zcweicheng.opengl.cameraHolder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Message
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.Toast

/**
 * Created by zhengweicheng on 2018/3/29 0029.
 *
 */

object CameraInstance {
    const val TAG = "CameraInstance"

    private val mCameraThread: CameraThread = CameraThread()

    const val MSG_INIT_CAMERA = 1
    const val MSG_STOP_CAMERA = 2
    const val MSG_TOGGLE_CAMERA = 3
    const val MSG_ZOOM_CAMERA = 4
    const val MSG_SET_SURFACE_TEXTURE = 5
    const val MSG_SET_STATE_LISTENER = 6
    const val MSG_SET_OPERATE_LISTENER = 7
    const val MSG_START_CAMERA = 8
    const val MSG_SET_FPS = 9
    const val MSG_DATA_CALLBACK = 10

    enum class CameraPreviewMode {
        PREVIEW_MODE_DATA, PREVIEW_MODE_TEXTURE
    }

    enum class StartErrorCode {
        PERMISSION_DENY,
    }

    interface CameraStateListener {
        fun onSuccess(cameraIndex: Int, cameraDegree: Int, previewWidth: Int,
                      previewHeight: Int, surfaceTexture: SurfaceTexture?)
        fun onError(errorCode: Int)
    }

    interface CameraOperateListener {
        /**
         * 切换相机
         * @param state 切换相机后的状态
         * @param width
         * @param height
         */
        fun onToggleComplete(state: Int, width: Int, height: Int, degree: Int)

        fun onOpenFlashLight(result: Boolean, isOpen: Boolean)
    }

    /**
     * 当以相机previewCallback的形式预览时候，用该回调回调数据
     */
    interface DataCallback {
        fun callback(data: ByteArray?, mWidth: Int, mHeight: Int, mCameraOrientation: Int, mCameraIndex: Int)
    }

    fun initCamera(expectWidth: Int, expectHeight: Int, previewMode: CameraPreviewMode)
            : CameraInstance {

        Log.d(TAG, "ThreadId=${mCameraThread.id}")

        Message.obtain(mCameraThread.mHandler, MSG_INIT_CAMERA, arrayOf<Any>(expectWidth, expectHeight,
                previewMode)).sendToTarget()
        return this
    }

    fun setOutSurfaceTexture(surfaceTexture: SurfaceTexture?): CameraInstance {
        Message.obtain(mCameraThread.mHandler, MSG_SET_SURFACE_TEXTURE, surfaceTexture).sendToTarget()
        return this
    }

    fun setStateListener(listener: CameraStateListener): CameraInstance {
        Message.obtain(mCameraThread.mHandler, MSG_SET_STATE_LISTENER, listener).sendToTarget()
        return this
    }

    fun setDataCallback(callback: DataCallback): CameraInstance {
        Message.obtain(mCameraThread.mHandler, MSG_SET_STATE_LISTENER, callback).sendToTarget()
        return this
    }

    fun startCamera(): CameraInstance {
        Message.obtain(mCameraThread.mHandler, MSG_START_CAMERA).sendToTarget()
        return this
    }

    fun stopCamera(): CameraInstance {
        mCameraThread.mHandler.removeCallbacksAndMessages(null)
        Message.obtain(mCameraThread.mHandler, MSG_STOP_CAMERA).sendToTarget()
        return this
    }

    fun toggleCamera(listener: CameraStateListener?): CameraInstance {
        mCameraThread.mHandler.removeCallbacksAndMessages(null)
        Message.obtain(mCameraThread.mHandler, MSG_TOGGLE_CAMERA, listener).sendToTarget()
        return this
    }

    fun zoomCamera(value: Int): CameraInstance {
        Message.obtain(mCameraThread.mHandler, MSG_ZOOM_CAMERA, value).sendToTarget()
        return this
    }

    fun setFps(fps: Int): CameraInstance {
        Message.obtain(mCameraThread.mHandler, MSG_SET_FPS, fps).sendToTarget()
        return this
    }
}
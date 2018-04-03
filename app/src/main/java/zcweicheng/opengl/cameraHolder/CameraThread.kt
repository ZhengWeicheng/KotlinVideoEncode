package zcweicheng.opengl.cameraHolder

import android.content.Context
import android.content.res.Resources
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import zcweicheng.opengl.OpenglApp
import zcweicheng.opengl.cameraHolder.CameraInstance.CameraPreviewMode
import zcweicheng.opengl.cameraHolder.CameraInstance.CameraStateListener
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_DATA_CALLBACK
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_INIT_CAMERA
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_SET_FPS
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_SET_STATE_LISTENER
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_SET_SURFACE_TEXTURE
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_START_CAMERA
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_STOP_CAMERA
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_TOGGLE_CAMERA
import zcweicheng.opengl.cameraHolder.CameraInstance.MSG_ZOOM_CAMERA
import java.nio.ByteBuffer

/**
 * Created by zhengweicheng on 2018/3/29 0029.

 */

class CameraThread() : HandlerThread("CameraThread"), Handler.Callback, Camera.PreviewCallback {

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        mVideoBufferIdx = 1 - mVideoBufferIdx
        mCamera?.addCallbackBuffer(mVideoBuffer?.get(mVideoBufferIdx)?.array())
        mDataCallback?.callback(data, mPreviewWidth, mPreviewHeight, mCameraOrientation, mCameraIndex)
    }

    val TAG = "CameraThread"

    val ERROR_PERMISSION_DENY = 1
    val ERROR_CAMERA_INITIALIZED = 2
    val ERROR_CAMERA_OPEN_ERROR = 3
    val INIT_CAMERA_SUCCESS = 4

    private var mExpectWidth:Int = 0
    private var mExpectHeight:Int = 0
    private var mPreviewWidth:Int = 0
    private var mPreviewHeight:Int = 0
    private var mExpectFps:Int = 0
    private var mZoomValue = 0
    private var mVideoBufferIdx: Int = 0
    private var mCameraOrientation: Int = 0
    private var mCameraIndex:Int = Camera.CameraInfo.CAMERA_FACING_BACK
    private var exited: Boolean = false

    private lateinit var mPreviewMode: CameraPreviewMode
    private var mCamera: Camera? = null
    private var mOutSurface:SurfaceTexture? = null
    private var mVideoBuffer: MutableList<ByteBuffer>? = null
    private var mDataCallback: CameraInstance.DataCallback? = null
    private var mStateListener: CameraStateListener? = null

    lateinit var mHandler:Handler

    init {
        if (!isAlive) {
            start()
            mHandler = Handler(Looper.myLooper(), this)
        }
    }

    override fun handleMessage(msg: Message?): Boolean {
        when(msg?.what) {
            MSG_INIT_CAMERA->{
                initCamera(msg)
                return true
            }
            MSG_STOP_CAMERA->{
                stopCamera(msg)
                return true
            }
            MSG_ZOOM_CAMERA->{
                zoomCamera(msg.obj as Int)
                return true
            }
            MSG_TOGGLE_CAMERA->{
                toggleCamera(msg)
                return true
            }
            MSG_START_CAMERA->{
                val result = startCamera()
                when(result) {
                    INIT_CAMERA_SUCCESS->mStateListener?.onSuccess(mCameraIndex, mCameraOrientation,
                            mPreviewWidth, mPreviewHeight, mOutSurface)
                    else ->mStateListener?.onError(result)
                }
                return true
            }
            MSG_SET_SURFACE_TEXTURE->{
                setOutSurfaceTexture(msg)
                return true
            }
            MSG_SET_STATE_LISTENER->{
                setStateListener(msg)
                return true
            }
            MSG_SET_FPS->{
                setCameraFps(msg.obj as Int)
                return true
            }
            MSG_DATA_CALLBACK->{
                this.mDataCallback = msg.obj as CameraInstance.DataCallback
                return true
            }
        }
        return false
    }

    private fun setCameraFps(fps: Int) {
        mExpectFps = fps
        if (mCamera == null || fps == 0) {
            return
        }
        var expectFps:Int = fps
        expectFps *= 1000
        val params:Camera.Parameters = mCamera!!.parameters
        val fpsList:List<IntArray> = params.supportedPreviewFpsRange
        if (fpsList.isNotEmpty()) {
            for (item in fpsList) {
                Log.d(TAG, "fps =$item")
                if (item[0] <= expectFps && item[1] >= expectFps) {
                    params.setPreviewFpsRange(item[0], item[1])
                    break
                }
            }
        }
    }

    private fun toggleCamera(msg: Message) {
        Log.w(TAG, "toggleCamera")
        releaseCamera()
        mCameraIndex = when (mCameraIndex) {
            Camera.CameraInfo.CAMERA_FACING_FRONT -> Camera.CameraInfo.CAMERA_FACING_BACK
            else -> Camera.CameraInfo.CAMERA_FACING_FRONT
        }

        val result = startCamera()
        if (msg.obj != null) {
            val listener = msg.obj as CameraInstance.CameraOperateListener
            listener.onToggleComplete(result, mPreviewWidth, mPreviewWidth, mCameraOrientation)
        }
    }

    private fun releaseCamera() {
        synchronized(this) {
            mHandler.removeCallbacks(mAutoFocusRunnable)
            mHandler.removeCallbacksAndMessages(null)
            try {
                mCamera?.stopPreview()
                mCamera?.setPreviewCallback(null)
                mCamera?.release()
                mCamera = null
                Log.w(TAG, "摄像机已释放")
            } catch (e: Exception) {
                Log.e(TAG, "stopCamera", e)
            }

        }
    }

    private fun releaseObject() {
        synchronized(this) {
            mVideoBuffer?.clear()
            mVideoBuffer = null
            mOutSurface?.release()
            mOutSurface = null
            mStateListener = null
            mExpectFps= 0
            mDataCallback = null
        }
    }

    private fun zoomCamera(zoomValue:Int) {
        this.mZoomValue = zoomValue
        if (mCamera == null) {
            return
        }

    }

    private fun stopCamera(msg: Message) {
        releaseCamera()
        releaseObject()
    }

    private fun initCamera(msg: Message) {
        val objMessage: Array<*> = msg.obj as Array<*>
        this.mExpectWidth = objMessage[0] as Int
        this.mExpectHeight = objMessage[1] as Int
        this.mPreviewMode = objMessage[2] as CameraPreviewMode
    }

    private fun setStateListener(msg:Message) {
        if (msg.obj == null) {
            return
        }
        this.mStateListener = msg.obj as CameraStateListener
    }

    private fun setOutSurfaceTexture(msg: Message) {
        if (msg.obj == null) {
            return
        }
        this.mOutSurface = msg.obj as SurfaceTexture
    }

    private fun startCamera():Int {
        Log.d(TAG, "开启相机")
        synchronized(this) {
            if (mCamera != null) {
                Log.e(TAG, "initCamera 初始化摄像头时,摄像头已经初始化过了！")
                return ERROR_CAMERA_INITIALIZED
            }
            mCamera = null
        }

        try {
            mCamera = Camera.open(mCameraIndex)
        } catch (e: Exception) {
            e.printStackTrace()
            mCamera = null
            return ERROR_CAMERA_OPEN_ERROR
        }

        if (!setCameraParameter()) {
            return ERROR_CAMERA_OPEN_ERROR
        }

        if (mOutSurface == null) {
            mOutSurface = SurfaceTexture(1)
        }
        if (mPreviewMode == CameraPreviewMode.PREVIEW_MODE_DATA) {
            if (mVideoBuffer == null) {
                val bufferSize = mPreviewWidth * mPreviewHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21)
                mVideoBuffer = mutableListOf(ByteBuffer.allocate(bufferSize), ByteBuffer.allocate(bufferSize))
            }
            mCamera?.addCallbackBuffer(mVideoBuffer!![mVideoBufferIdx].array())
            mCamera?.setPreviewCallbackWithBuffer(this)
        }
        mCamera?.setPreviewTexture(mOutSurface)
        mCamera?.startPreview()
        updateCameraDegree()
        mHandler.removeCallbacks(mAutoFocusRunnable)
        mHandler.postDelayed(mAutoFocusRunnable, 2000)
        Log.w(TAG, "开始Camera预览")
        exited = false
        return INIT_CAMERA_SUCCESS

    }

    private val mAutoFocusRunnable = Runnable { doTouchFocus(null) }
    private var isFocusing: Boolean = false

    private fun doTouchFocus(event: MotionEvent?) {
        if (mCamera == null) {
            return
        }
        Log.d(TAG, "doTouchFocus")
        try {
            val param = mCamera?.parameters
            setFocusAndMeterAreas(param, getFocusAreas(event))
            mCamera?.parameters = param
            isFocusing = true
            mCamera?.autoFocus(mAutoFocusCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun setFocusAndMeterAreas(parameters: Camera.Parameters?, rect: Rect?) {
        var localRect: Rect? = rect

        if (localRect == null) localRect = Rect(-50, -50, 50, 50)

        parameters?.focusAreas = listOf(Camera.Area(localRect, 600))
        parameters?.meteringAreas = listOf(Camera.Area(localRect, 800))

    }

    private fun getFocusAreas(event: MotionEvent?): Rect? {
        if (event == null) {
            return null
        }
        val rect = Rect()
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val centerX = (event.x / screenWidth * 2000 - 1000).toInt()
        val centerY = (event.y / screenHeight * 2000 - 1000).toInt()

        rect.left = clamp(centerX - 50, -1000, 1000)
        rect.top = clamp(centerY - 50, -1000, 1000)
        rect.right = clamp(centerX + 50, -1000, 1000)
        rect.bottom = clamp(centerY + 50, -1000, 1000)
        return rect
    }

    private fun clamp(x: Int, min: Int, max: Int): Int {
        var x = x
        if (x <= min) {
            x = min
        } else if (x >= max) {
            x = max
        }
        return x
    }

    private val mAutoFocusCallback = Camera.AutoFocusCallback { arg0, _ ->
        isFocusing = false
        try {
            if (arg0 && mCamera != null) {
                mCamera!!.cancelAutoFocus()
                setFocusMode(mCamera!!.parameters)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCameraDegree() {
        val wm = OpenglApp.mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setCameraDegreeByWindowRotation(wm.defaultDisplay.rotation)
    }

    private fun setCameraDegreeByWindowRotation(windowRotation: Int) {
        var mWindowDegree = 0
        when (windowRotation) {
            Surface.ROTATION_0 -> mWindowDegree = 0
            Surface.ROTATION_90 -> mWindowDegree = 90
            Surface.ROTATION_180 -> mWindowDegree = 180
            Surface.ROTATION_270 -> mWindowDegree = 270
        }
        if (mCamera != null) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(mCameraIndex, info)

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCameraOrientation = (info.orientation + mWindowDegree) % 360
                mCameraOrientation = (360 - mCameraOrientation) % 360 // compensate the mirror
            } else { // back-facing
                mCameraOrientation = (info.orientation - mWindowDegree) % 360
            }

            mCamera?.setDisplayOrientation(mCameraOrientation)
            Log.w(TAG, "摄像机角度 = $mCameraOrientation")
        }
    }

    private fun setCameraParameter():Boolean {
        if (mCamera == null) {
            return false
        }
        try {
            val params: Camera.Parameters = mCamera!!.parameters
            //格式
            params.previewFormat = ImageFormat.NV21

            //预览大小
            val sizes = params.supportedPreviewSizes

            if (sizes != null) {
                val frameSize = calculateCameraFrameSize(sizes, mExpectWidth, mExpectHeight)
                Log.w(TAG, "预览设置为 " + frameSize.width + "x" + frameSize.height)
                params.setPreviewSize(frameSize.width, frameSize.height)
                mPreviewWidth = frameSize.width
                mPreviewHeight = frameSize.height
            }

            //帧率
            if (mExpectFps != 0) {
                setCameraFps(mExpectFps)
            }

            //聚焦
            setFocusMode(params)

            //设置默认的缩放值
            if (params.isZoomSupported) {
                mZoomValue = 0
                params.zoom = mZoomValue
            }

            //是否支持视频防抖
            if ("true" == params.get("video-stabilization-supported")) {
                params.set("video-stabilization", "true")
            }

            this.mCamera?.parameters = params
        } catch (e:Exception) {
            return false
        }
        return true
    }

    private fun setFocusMode(params: Camera.Parameters) {
        val focusModeList:List<String> = params.supportedFocusModes
        if (focusModeList.isNotEmpty()) {
            when {
                focusModeList.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ->
                    params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                focusModeList.contains(Camera.Parameters.FOCUS_MODE_AUTO) ->
                    params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                else-> params.focusMode = focusModeList[0]
            }
        }
    }

    /**
     * 根据期望选择合适宽高
     */
    private fun calculateCameraFrameSize(supportedSizes: List<Camera.Size>, expectWidth: Int,
                                         expectHeight: Int): CameraSize {
        var calcWidth = 0
        var calcHeight = 0

        for (size in supportedSizes) {
            val width = size.width
            val height = size.height

            if (width <= expectWidth && height <= expectHeight) {
                if (width >= calcWidth && height >= calcHeight) {
                    calcWidth = width
                    calcHeight = height
                }
            }
        }
        return CameraSize(calcWidth, calcHeight)
    }

    // =====================================flash light=========================================
    private fun openFlashLight(isOpen: Boolean, listener: CameraInstance.CameraOperateListener?) {
        if (mCameraIndex == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return
        }
        synchronized(this) {
            try {
                val parameters = mCamera?.parameters
                val flashModes = parameters?.supportedFlashModes
                if (flashModes == null) {
                    listener?.onOpenFlashLight(false, isOpen)
                } else {
                    when {
                        isOpen && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH) -> {
                            parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                            listener?.onOpenFlashLight(true, isOpen)
                        }
                        !isOpen && flashModes.contains(Camera.Parameters.FLASH_MODE_OFF) -> {
                            parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
                            listener?.onOpenFlashLight(true, isOpen)
                        }
                        else -> listener?.onOpenFlashLight(false, isOpen)
                    }
                }
                mCamera?.parameters = parameters

            } catch (e: Exception) {
                listener?.onOpenFlashLight(false, isOpen)
                Log.e(TAG, "open flash light fail" + e.toString())
            }
        }
    }

    private inner class CameraSize internal constructor(internal var width: Int, internal var height: Int)
}
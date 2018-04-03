package zcweicheng.opengl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import kotlinx.android.synthetic.main.activity_camera_preview.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import zcweicheng.opengl.cameraHolder.CameraInstance
import zcweicheng.opengl.cameraHolder.CameraInstance.CameraPreviewMode.PREVIEW_MODE_TEXTURE

/**
 * Created by zhengweicheng on 2018/3/29 0029.
 *
 */
class CameraPreviewActivity : AppCompatActivity(), SurfaceTextureListener, CameraInstance.CameraStateListener {
    private val REQUEST_CODE_CAMERA_PERMISSION = 111
    private var mSurfaceTexture:SurfaceTexture? = null


    override fun onSuccess(cameraIndex: Int, cameraDegree: Int, previewWidth: Int,
                           previewHeight: Int, surfaceTexture: SurfaceTexture?) {
    }

    override fun onError(errorCode: Int) {
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        CameraInstance.stopCamera()
        return true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        mSurfaceTexture = surface
        startCamera()
    }

    private fun startCamera() {
        if (mSurfaceTexture == null) {
            return
        }
        CameraInstance.initCamera(640, 480, PREVIEW_MODE_TEXTURE)
                .setStateListener(this)
                .setOutSurfaceTexture(mSurfaceTexture)
                .startCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)
        camera_view.surfaceTextureListener = this
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CODE_CAMERA_PERMISSION)
        }
        switch_camera.onClick { CameraInstance.toggleCamera(null) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_CAMERA_PERMISSION -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            }
        }
    }
}
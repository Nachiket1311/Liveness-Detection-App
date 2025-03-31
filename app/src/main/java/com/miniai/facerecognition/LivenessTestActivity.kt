package com.miniai.facerecognition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.WindowInsets
import android.widget.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

class LivenessTestActivity : AppCompatActivity(), FrameInferface {

    companion object {
        private const val FRAME_WIDTH = 720
        private const val FRAME_HEIGHT = 1280
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
        private val TAG = LivenessTestActivity::class.simpleName
    }

    private lateinit var previewView: PreviewView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var frameAnalyser: FrameAnalyser
    private lateinit var boundingBoxOverlay: BoundingBoxOverlay
    private lateinit var switchCameraButton: FloatingActionButton


    private lateinit var viewBackgroundOfMessage: View
    private lateinit var textViewMessage: TextView

    private var cameraSelector = CameraSelector.LENS_FACING_FRONT // Default to front camera
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liveness)

        val isProcMode = 2 // 0: Register, 1: Verify, 2: Liveness Test

        previewView = findViewById(R.id.preview_view)
        viewBackgroundOfMessage = findViewById(R.id.viewBackgroundOfMessage)
        textViewMessage = findViewById(R.id.textViewMessage)
        switchCameraButton = findViewById(R.id.buttonSwitchCamera) as FloatingActionButton

        boundingBoxOverlay = findViewById(R.id.bbox_overlay)
        boundingBoxOverlay.setWillNotDraw(false)
        boundingBoxOverlay.setZOrderOnTop(true)
        frameAnalyser = FrameAnalyser(this, boundingBoxOverlay, viewBackgroundOfMessage, textViewMessage, isProcMode)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        frameAnalyser.addOnFrameListener(this)

        switchCameraButton.setOnClickListener { switchCamera() }
    }

    override fun onResume() {
        super.onResume()
        frameAnalyser.setRunning(true)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        } else {
            startCameraPreview()
        }
    }

    override fun onPause() {
        super.onPause()
        frameAnalyser.setRunning(false)
        stopCameraPreview()
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun startCameraPreview() {
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindPreview()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraPreview() {
        cameraProvider?.unbindAll()
    }

    private fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        stopCameraPreview()
        bindPreview()
    }

    private fun bindPreview() {
        val preview = Preview.Builder().build()
        val selector = CameraSelector.Builder().requireLensFacing(cameraSelector).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageFrameAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(FRAME_WIDTH, FRAME_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser)

        cameraProvider?.bindToLifecycle(this as LifecycleOwner, selector, preview, imageFrameAnalysis)
    }

    override fun onRegister(faceImage: Bitmap, featData: ByteArray?) {}
    override fun onVerify(msg: String) {}
}
package com.dvt.camclient

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.transition.Visibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Text
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.math.abs

class CamActivity : AppCompatActivity() {

    // region Activity action
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        setContentView(R.layout.activity_cam)
        textureView = findViewById(R.id.textureView)
        captureBtn = findViewById(R.id.btn_capture)

        //listAvailableCameras()

        captureBtn.setOnClickListener {
            coroutineScope.launch { takePictureNew() }
        }

        initCameraConfiguration()

        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread.looper)

    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }

    override fun onResume() {
        super.onResume()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        cameraThread.quitSafely()
    }
    //endregion

    //region Camera preview & capture

    private lateinit var textureView: TextureView
    private lateinit var captureBtn: Button
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var previewSize: Size
    private lateinit var imageReader: ImageReader
    private var cameraDevice: CameraDevice? = null

    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler




    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private fun getOptimalPreviewSize(sizes: Array<Size>, targetSize: Size): Size {
        val aspectRatio = targetSize.width.toFloat() / targetSize.height.toFloat()
        return sizes.minByOrNull {
            abs(it.width.toFloat() / it.height.toFloat() - aspectRatio)
        } ?: sizes[0]
    }

    private fun listAvailableCameras() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

                val cameraType = when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> "Back Camera"
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front Camera"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External Camera"
                    else -> "Unknown Camera"
                }

                val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.max()

                Log.d("CamClient","Camera ID: $cameraId - Type: $cameraType - FocalLength: $focalLengths")
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d("CamClient","onSurfaceTextureAvailable")
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.d("CamClient","onSurfaceTextureSizeChanged")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) : Boolean{
            Log.d("CamClient","onSurfaceTextureDestroyed")
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            //Log.d("CamClient","onSurfaceTextureUpdated")
        }
    }

    private fun createPreviewCameraSession( previewSize: Size ) {
        try {
            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
            imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, 1)

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(surface), OutputConfiguration(imageReader.surface)),
                Executors.newSingleThreadExecutor(),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d("CamClient","onConfigured")
                        captureSession = session
                        try {

                            captureSession.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                null,
                                null
                            )
                        } catch (ex: CameraAccessException) {
                            ex.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.d("CamClient","onConfigureFailed")
                        Toast.makeText(
                            applicationContext,
                            "Failed to configure camera",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            cameraDevice!!.createCaptureSession(sessionConfig)

        } catch (ex: CameraAccessException) {
            ex.printStackTrace()
        }

    }

    //Camera Id
    // 0 : RW1 , 1 : FW1 , 2 : RS1 , 3 : FW2 ,
    private fun openCamera() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = "0"

            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: return

            val displaySize = Size(textureView.width, textureView.height)
            previewSize =
                getOptimalPreviewSize(map.getOutputSizes(SurfaceTexture::class.java), displaySize)

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        Log.d("CamClient", "Camera Device Opened")
                        cameraDevice = camera
                        cameraInfo = CameraInfo(cameraManager, cameraDevice, cameraId)
                        cameraInfo.initCameraCharacteristics()
                        createPreviewCameraSession(previewSize)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.d("CamClient", "Camera Device disconnected")
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.d("CamClient", "Camera Device error")
                    }
                }, null)
            }

        } catch (ex: CameraAccessException) {
            ex.printStackTrace()
        }
    }

    private fun closeCamera() {
        captureSession.close()
        cameraDevice?.close()
    }

    private suspend fun takePictureNew(){
        withContext(Dispatchers.IO){
            if (cameraDevice == null) return@withContext

            val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }

            imageReader.setOnImageAvailableListener({ reader ->
                coroutineScope.launch {
                    reader.acquireLatestImage()?.let {
                        image ->
                        val buffer: ByteBuffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()
                        saveImageNew(bytes)
                    }
                }

            }, cameraHandler)

            cameraDevice!!.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session.capture(captureBuilder.build(), null, null)

                    coroutineScope.launch (Dispatchers.Main) { createPreviewCameraSession(previewSize) }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, cameraHandler)
        }
    }

    private suspend fun saveImageNew(bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val name = "IMG_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS")) + ".jpg"
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)
            FileOutputStream(file).use { it.write(bytes) }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CamActivity, "Image saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }
    }

    //endregion

    //region Camera configuration
    private lateinit var cameraInfo : CameraInfo
    private lateinit var isoTextView : TextView
    private lateinit var expTextView : TextView
    private lateinit var evTextView : TextView
    private lateinit var wbTextView : TextView
    private lateinit var zoomTextView : TextView
    private lateinit var minTextView : TextView
    private lateinit var maxTextView : TextView
    private lateinit var valueTextView : TextView
    private lateinit var modSeekBar : SeekBar
    private lateinit var autoFocusSwitch : SwitchCompat
    private lateinit var layoutFuncs : LinearLayout

    private var selectedFunc : Funcs = Funcs.NONE

    fun initCameraConfiguration(){
        isoTextView = findViewById<TextView>(R.id.txt_iso)
        expTextView = findViewById<TextView>(R.id.txt_speed)
        evTextView = findViewById<TextView>(R.id.txt_ev)
        wbTextView = findViewById<TextView>(R.id.txt_wb)
        zoomTextView = findViewById<TextView>(R.id.txt_zoom)
        minTextView = findViewById<TextView>(R.id.txt_min_value)
        maxTextView = findViewById<TextView>(R.id.txt_max_value)
        valueTextView = findViewById<TextView>(R.id.txt_current_value)
        modSeekBar = findViewById<SeekBar>(R.id.sk_mod_value)
        autoFocusSwitch = findViewById<SwitchCompat>(R.id.sw_autoFocus)
        layoutFuncs = findViewById<LinearLayout>(R.id.layout_selected_function)

        isoTextView.setOnClickListener{
            initFunction(Funcs.ISO)
        }
        expTextView.setOnClickListener{
            initFunction(Funcs.EXP)
        }
        evTextView.setOnClickListener{
            initFunction(Funcs.EV)
        }
        wbTextView.setOnClickListener{
            initFunction(Funcs.WB)
        }
        zoomTextView.setOnClickListener{
            initFunction(Funcs.ZOOM)
        }

        modSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueTextView.setText(String.format("%,d", progress))
                when (selectedFunc){
                    Funcs.ISO -> cameraInfo.ISO.value = progress
                    Funcs.EXP -> cameraInfo.Exposure.value = progress.toLong()
                    Funcs.EV -> cameraInfo.EV.value = progress
                    Funcs.WB -> cameraInfo.WB?.value = progress
                    Funcs.ZOOM -> cameraInfo.Zoom?.value = progress.toFloat()
                    Funcs.NONE -> {}
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

    }

    private fun initFunction(func: Funcs){
        if (selectedFunc == Funcs.NONE){
            layoutFuncs.visibility = View.VISIBLE
            when (func){
                Funcs.ISO -> {
                    minTextView.setText(String.format("%,d", cameraInfo.ISO.min?:0))
                    maxTextView.setText(String.format("%,d", cameraInfo.ISO.max?:0))
                    valueTextView.setText(String.format("%,d", cameraInfo.ISO.value?:0))

                    modSeekBar.min = cameraInfo.ISO.min?:0
                    modSeekBar.max = cameraInfo.ISO.max?:0
                    modSeekBar.progress = cameraInfo.ISO.value?:0
                }
                Funcs.EXP -> {
                    minTextView.setText(String.format("%,d", cameraInfo.Exposure.min?:0))
                    maxTextView.setText(String.format("%,d", cameraInfo.Exposure.max?:0))
                    valueTextView.setText(String.format("%,d", cameraInfo.Exposure.value?:0))

                    modSeekBar.min = (cameraInfo.Exposure.min?:0).toInt()
                    modSeekBar.max = (cameraInfo.Exposure.max?:0).toInt()
                    modSeekBar.progress = (cameraInfo.Exposure.value?:0).toInt()
                }

                Funcs.EV -> {
                    minTextView.setText(String.format("%,d", cameraInfo.EV.min?:0))
                    maxTextView.setText(String.format("%,d", cameraInfo.EV.max?:0))
                    valueTextView.setText(String.format("%,d", cameraInfo.EV.value?:0))

                    modSeekBar.min = cameraInfo.EV.min?:0
                    modSeekBar.max = cameraInfo.EV.max?:0
                    modSeekBar.progress = cameraInfo.EV.value?:0
                }
                Funcs.WB -> {
                    minTextView.setText(String.format("%,d", cameraInfo.WB?.modes?.min()?:0))
                    maxTextView.setText(String.format("%,d", cameraInfo.WB?.modes?.max()?:0))
                    valueTextView.setText(String.format("%,d", cameraInfo.WB?.value?:0))

                    modSeekBar.min = cameraInfo.WB?.modes?.min()?:0
                    modSeekBar.max = cameraInfo.WB?.modes?.max()?:0
                    modSeekBar.progress = cameraInfo.WB?.value?:0
                }
                Funcs.ZOOM -> {
                    minTextView.setText(String.format("%,d", (cameraInfo.Zoom?.min?:0).toInt()))
                    maxTextView.setText(String.format("%,d", (cameraInfo.Zoom?.max?:0).toInt()))
                    valueTextView.setText(String.format("%,d", (cameraInfo.Zoom?.value?:0).toInt()))

                    modSeekBar.min = ((cameraInfo.Zoom?.min ?: 0)).toInt()
                    modSeekBar.max = ((cameraInfo.Zoom?.max ?:0) ).toInt()
                    modSeekBar.progress = ((cameraInfo.Zoom?.value?:0) ).toInt()
                }
                Funcs.NONE -> {

                }
            }
            selectedFunc = func
        } else {
            layoutFuncs.visibility = View.INVISIBLE
            selectedFunc = Funcs.NONE
        }
    }

    //endregion
}
package com.qin.f21.toolbox

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera host for the scanner and the photo tool.
 *
 * Implemented directly on the Camera2 API (no CameraX dependency) so the app stays
 * small and works on the lean Android 11 build of the Qin F21 Pro.
 */
class CameraActivity : Activity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SCAN = "scan"
        const val MODE_PHOTO = "photo"
        const val RESULT_TEXT = "text"
        const val RESULT_FORMAT = "format"
        const val RESULT_PATH = "path"
        const val REQUEST_CAMERA = 3001
    }

    private lateinit var textureView: TextureView
    private lateinit var overlay: TextView
    private lateinit var torchButton: ImageView
    private lateinit var shutterButton: View

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var captureRequest: CaptureRequest.Builder? = null
    private var cameraId: String? = null
    private var previewSize = Size(640, 480)
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var torchOn = false
    private val decoding = AtomicBoolean(false)

    private val zxingReader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.AZTEC,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.CODE_93,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.ITF,
                    BarcodeFormat.CODABAR
                ),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_camera)
        textureView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        torchButton = findViewById(R.id.btn_torch)
        shutterButton = findViewById(R.id.btn_shutter)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SCAN
        runOnUiThread {
            overlay.setText(if (mode == MODE_SCAN) getString(R.string.camera_scan_hint) else getString(R.string.camera_photo_hint))
        }
        shutterButton.setOnClickListener { if (mode == MODE_SCAN) decodeCurrentFrame() else capturePhoto() }
        torchButton.setOnClickListener { toggleTorch() }
        findViewById<View>(R.id.btn_close).setOnClickListener { finish() }

        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                runOnUiThread {
                    overlay.setText(getString(R.string.camera_permission_denied))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = listener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("camera").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        backgroundThread = null
        backgroundHandler = null
    }

    private val listener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    // Lint cannot see through the hasCameraPermission() guard below, so the
    // MissingPermission finding on the Camera2 calls is suppressed at this
    // method level: the CAMERA permission is requested in onCreate() and every
    // entry point returns before touching the camera without it.
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (!hasCameraPermission()) return
        val manager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        try {
            val ids = manager.cameraIdList
            val id = ids.firstOrNull { candidate ->
                val characteristics = manager.getCameraCharacteristics(candidate)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull() ?: return
            cameraId = id

            val characteristics = manager.getCameraCharacteristics(id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
            previewSize = sizes?.filter { it.width <= 1280 && it.height <= 1280 }
                ?.minByOrNull { it.width * it.height } ?: Size(640, 480)

            imageReader?.close()
            imageReader = ImageReader.newInstance(
                previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2
            )
            imageReader?.setOnImageAvailableListener({ reader -> onFrame(reader) }, backgroundHandler)

            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    runOnUiThread {
                        overlay.setText(getString(R.string.camera_error))
                    }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            runOnUiThread {
                overlay.setText(getString(R.string.camera_error))
            }
        }
    }

    private fun createSession() {
        val camera = cameraDevice ?: return
        try {
            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)
            val readerSurface = imageReader?.surface
            val surfaces = ArrayList<Surface>()
            surfaces.add(surface)
            if (readerSurface != null) surfaces.add(readerSurface)

            captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                readerSurface?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        session.setRepeatingRequest(captureRequest!!.build(), null, backgroundHandler)
                    } catch (e: Exception) {
                        // the session may be closed already
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    runOnUiThread {
                        overlay.setText(getString(R.string.camera_error))
                    }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            runOnUiThread {
                overlay.setText(getString(R.string.camera_error))
            }
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    private fun toggleTorch() {
        val device = cameraDevice ?: return
        val id = cameraId ?: return
        try {
            torchOn = !torchOn
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.setTorchMode(id, torchOn)
            torchButton.setColorFilter(
                getColor(if (torchOn) R.color.accent else R.color.text_primary)
            )
        } catch (e: Exception) {
            torchOn = false
            runOnUiThread {
                overlay.setText(getString(R.string.camera_no_torch))
            }
        }
    }

    private fun onFrame(source: ImageReader) {
        val image = try {
            source.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val source = PlanarYUVLuminanceSource(
                data,
                plane.rowStride,
                image.height,
                0, 0,
                minOf(image.width, previewSize.width),
                minOf(image.height, previewSize.height),
                false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = try {
                // the reader is not thread safe, the frame callback and the shutter
                // button may decode at the same time
                synchronized(zxingReader) { zxingReader.decode(bitmap) }
            } catch (e: Exception) {
                null
            }
            if (result != null) {
                val data2 = Intent()
                data2.putExtra(RESULT_TEXT, result.text)
                data2.putExtra(RESULT_FORMAT, result.barcodeFormat.name)
                // the frame callback runs on the camera thread
                runOnUiThread {
                    setResult(Activity.RESULT_OK, data2)
                    finish()
                }
            }
        } catch (e: Exception) {
            // ignore single frame errors
        } finally {
            try {
                image.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /** Decodes the current preview frame directly (used by the shutter button in scan mode). */
    private fun decodeCurrentFrame() {
        val bitmap = textureView.bitmap ?: return
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val luminance = ByteArray(width * height)
        for (i in argb.indices) {
            val pixel = argb[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            luminance[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
        }
        val pixels = IntArray(width * height)
        for (i in luminance.indices) {
            val v = luminance[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val result = try {
            synchronized(zxingReader) { zxingReader.decode(BinaryBitmap(HybridBinarizer(source))) }
        } catch (e: Exception) {
            null
        }
        if (result != null) {
            val data = Intent()
            data.putExtra(RESULT_TEXT, result.text)
            data.putExtra(RESULT_FORMAT, result.barcodeFormat.name)
            setResult(Activity.RESULT_OK, data)
            finish()
        } else {
            runOnUiThread {
                overlay.setText(getString(R.string.camera_no_code))
            }
        }
    }

    private fun capturePhoto() {
        val device = cameraDevice ?: return
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = manager.getCameraCharacteristics(cameraId ?: return)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)
            val size = jpegSizes?.firstOrNull { it.width <= 1920 } ?: jpegSizes?.firstOrNull() ?: return
            val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
            reader.setOnImageAvailableListener({ source ->
                val image = try {
                    source.acquireLatestImage()
                } catch (e: Exception) {
                    null
                } ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val directory = getExternalFilesDir(null) ?: filesDir
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val file = File(directory, "photo-$stamp.jpg")
                    file.outputStream().use { it.write(bytes) }
                    val data = Intent()
                    data.putExtra(RESULT_PATH, file.absolutePath)
                    runOnUiThread {
                        setResult(Activity.RESULT_OK, data)
                        finish()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        overlay.setText(getString(R.string.camera_error))
                    }
                } finally {
                    try {
                        image.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }, backgroundHandler)

            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(texture)
            val jpegSurface = reader.surface
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(jpegSurface)
                set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())
            }
            device.createCaptureSession(
                listOf(previewSurface, jpegSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            session.capture(request.build(), null, backgroundHandler)
                        } catch (e: Exception) {
                            runOnUiThread {
                                overlay.setText(getString(R.string.camera_error))
                            }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        runOnUiThread {
                            overlay.setText(getString(R.string.camera_error))
                        }
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            runOnUiThread {
                overlay.setText(getString(R.string.camera_error))
            }
        }
    }

    private fun getJpegOrientation(): Int {
        val rotation = display?.rotation ?: Surface.ROTATION_0
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
}

package kelig.esgi.fyc

import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss"
        private const val REQUIRED_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var outputDirectory: File

    private lateinit var btnTakePhoto: Button
    private lateinit var cameraExecutor: ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTakePhoto = findViewById(R.id.btn_take_photo)
        btnTakePhoto.setOnClickListener{ takePhoto() }

        if (allPermissionsGranted()) {
            startCamera()
        }
        else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUIRED_CODE_PERMISSIONS
            )
        }



        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUIRED_CODE_PERMISSIONS) {
            if(allPermissionsGranted()) {
                startCamera()
            }
            else {
                Toast.makeText(this, "No permission", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun takePhoto() {
        val imageCapture: ImageCapture = imageCapture?:return

        val file = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.FRENCH)
                .format(System.currentTimeMillis()) +".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object:ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(file)
                val msg = "Photo capture succeed: \n\n $savedUri"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                Log.e(TAG, msg)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance((this))
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder().build()
            imageCapture = ImageCapture.Builder().build()
            imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, CustomImageAnalyzer().apply {
                        setOnLumaListener(object: CustomImageAnalyzer.LumaListener {
                            override fun setOnLumaListener(average: Double, max: Int) {
                                runOnUiThread {
                                    tvAverage.text = "Average = ${"%.2f".format(average)}"
                                    tvMax.text = "Max. value = $max"
                                }
                            }
                        })
                    })
                }

            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
                preview?.setSurfaceProvider(cam_preview.createSurfaceProvider(camera?.cameraInfo))
            }
            catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply {mkdirs()
            }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir
        else
            filesDir
    }

    private class CustomImageAnalyzer(): ImageAnalysis.Analyzer {

        private lateinit var mListener: LumaListener

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()
            val data = ByteArray(remaining())
            get(data)
            return data
        }

        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map{ it.toInt() and 0xFF }
            val average = pixels.average()
            val max = pixels.maxOrNull()

            mListener.setOnLumaListener(average, max!!)

            image.close()
        }

        interface LumaListener {
            fun setOnLumaListener(average: Double, max: Int)
        }

        fun setOnLumaListener(mListener: LumaListener) {
            this.mListener = mListener
        }
    }
}
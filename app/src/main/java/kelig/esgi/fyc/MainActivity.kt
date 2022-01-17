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
        private const val REQUIRED_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null


    private lateinit var cameraExecutor: ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (allPermissionsGranted()) {
            cameraAnalysis()
        }
        else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUIRED_CODE_PERMISSIONS
            )
        }

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
                cameraAnalysis()
            }
            else {
                Toast.makeText(this, "No permission", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }



    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private class CustomImageAnalyzer(): ImageAnalysis.Analyzer {

        private lateinit var mListener: FycListener

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

            mListener.setOnFycListener(average, max!!)

            image.close()
        }

        interface FycListener {
            fun setOnFycListener(average: Double, max: Int)
        }

        fun setOnFycListener(mListener: FycListener) {
            this.mListener = mListener
        }
    }
}
package com.example.qrscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalGetImage class MainActivity : AppCompatActivity() {

    lateinit var previewView : PreviewView
    lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    lateinit var executorService: ExecutorService
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_ALL_FORMATS
        )
        .build()
    val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
        // Do logic if not granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        executorService = Executors.newSingleThreadExecutor()
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(executorService, ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null){
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val scanner = BarcodeScanning.getClient(options)
                scanner.process(image)
                    .addOnCompleteListener {
                        if (it.isSuccessful){
                            for (barcode in it.result) {
                                val rawValue = barcode.rawValue
                                Toast.makeText(this, rawValue, Toast.LENGTH_SHORT).show()
                                val valueType = barcode.valueType
                                when(valueType){
                                    Barcode.TYPE_URL -> {
                                        Intent(Intent.ACTION_VIEW)
                                            .apply {
                                                data = Uri.parse(rawValue)
                                            }
                                            .also {
                                                startActivity(it)
                                            }
                                    }
                                    Barcode.TYPE_EMAIL -> {
                                        Intent(Intent.ACTION_SENDTO)
                                            .apply {
                                                data = Uri.parse("mailto:")
                                                putExtra(Intent.EXTRA_EMAIL, rawValue)
                                            }
                                            .also {
                                                startActivity(it)
                                            }
                                    }
                                    Barcode.TYPE_PHONE -> {
                                        Intent(Intent.ACTION_SENDTO)
                                            .apply {
                                                data = Uri.fromParts("tel", rawValue, null)
                                            }
                                            .also {
                                                startActivity(it)
                                            }
                                    }
                                }
                                imageProxy.close()
                            }
                        }else{
                            val exception = it.exception
                            exception?.printStackTrace()
                            Toast.makeText(this, exception?.message, Toast.LENGTH_SHORT).show()
                        }
                        imageProxy.close()
                    }
            }else{
                imageProxy.close()
            }
        })
        preview.setSurfaceProvider(previewView.surfaceProvider)
        cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalysis, preview)
    }

}
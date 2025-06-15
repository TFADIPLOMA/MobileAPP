package com.example.tfaandroid

import android.graphics.Rect
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.Executors

class FaceScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FaceOverlayView
    private lateinit var guideView: ImageView
    private lateinit var authPrefs: AuthPreferences

    private lateinit var currentMode: String

    private var triggered = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_scanner)

        authPrefs = MyApp.get(this).authPrefs

        currentMode = intent.getStringExtra("mode") ?: ""


        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.faceOverlay)
        guideView = findViewById(R.id.faceGuideView)

        var backButton = findViewById<Button>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }

        startCamera()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val faceDetectorOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build()

            val faceDetector = FaceDetection.getClient(faceDetectorOptions)

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image =
                        InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    faceDetector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                val faceRects = faces.map {
                                    translateRect(it.boundingBox, mediaImage, previewView)
                                }

                                runOnUiThread {
                                    overlayView.faceRects = faceRects
                                }

                                val guideRect = Rect()
                                guideView.getGlobalVisibleRect(guideRect)

                                val firstFace = faceRects.first()
                                val intersection = Rect()
                                val intersects = intersection.setIntersect(guideRect, firstFace)
                                val overlapArea = intersection.width() * intersection.height()
                                val faceArea = firstFace.width() * firstFace.height()
                                val overlapRatio =
                                    if (faceArea > 0) overlapArea.toFloat() / faceArea else 0f

                                if (overlapRatio > 0.9f && !triggered) {
                                    triggered = true
                                    runOnUiThread {
                                        Log.i("Check params",currentMode)

                                        if (currentMode == "scanner") {
                                            val code = intent.getStringExtra("code") ?: ""
                                            val user: UserDto = Gson().fromJson(
                                                authPrefs.userJson,
                                                UserDto::class.java
                                            )
                                            val email = user.email;
                                            Log.i("Check params",code)
                                            Log.i("Check params",email)
                                            RetrofitClient.authService.verifyQrCode(
                                                VerifyCodeRequest(email, code)
                                            )
                                                .enqueue(object : Callback<Void> {
                                                    override fun onFailure(
                                                        call: Call<Void?>,
                                                        t: Throwable
                                                    ) {
                                                        Toast.makeText(
                                                            FaceScannerActivity(),
                                                            "Ошибка при входе",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }

                                                    override fun onResponse(
                                                        call: Call<Void?>,
                                                        response: Response<Void?>
                                                    ) {
                                                        Toast.makeText(
                                                            FaceScannerActivity(),
                                                            "Вход выполнен",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        finish()
                                                    }
                                                })
                                        }
                                        Toast.makeText(this, "Вход выполнен", Toast.LENGTH_LONG)
                                            .show()
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    overlayView.faceRects = emptyList()
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun translateRect(faceRect: Rect, image: Image, view: PreviewView): Rect {
        val scaleX = view.width.toFloat() / image.height.toFloat() // camera rotated
        val scaleY = view.height.toFloat() / image.width.toFloat()

        return Rect(
            (faceRect.left * scaleX).toInt(),
            (faceRect.top * scaleY).toInt(),
            (faceRect.right * scaleX).toInt(),
            (faceRect.bottom * scaleY).toInt()
        )
    }
}

package com.example.tfaandroid

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.ExifInterface
import android.media.Image
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class FaceScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FaceOverlayView
    private lateinit var guideView: ImageView
    private lateinit var authPrefs: AuthPreferences

    private lateinit var imageCapture: ImageCapture

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
                                val overlapArea = intersection.width() * intersection.height()
                                val faceArea = firstFace.width() * firstFace.height()
                                val overlapRatio =
                                    if (faceArea > 0) overlapArea.toFloat() / faceArea else 0f

                                if (overlapRatio > 0.9f && !triggered) {
                                    triggered = true
                                    val bitmap = yuvToRgb(imageProxy)

                                    val imageFile = saveBitmapAsJpeg(this, bitmap, "face_crop")
                                    saveBitmapAsJpeg(this, bitmap, "face_crop_corrected")
                                    rotateImageFile90(imageFile)
                                    saveImageToGallery(this, imageFile)

                                    val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), imageFile)
                                    val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

                                    val imagePart = createImagePartFromFile(imageFile)
                                    val user: UserDto = Gson().fromJson(
                                        authPrefs.userJson,
                                        UserDto::class.java
                                    )

                                    runOnUiThread {

                                        if (currentMode == "scanner") {
                                            val code = intent.getStringExtra("code") ?: ""
                                            val emailPart = user.email.toRequestBody("text/plain".toMediaTypeOrNull())

                                            RetrofitClient.authService.verifyFace(emailPart,body)
                                                .enqueue(object:Callback<ResponseBody>{
                                                    override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                                                        if(response.isSuccessful) {
                                                            RetrofitClient.authService.verifyQrCode(
                                                                VerifyCodeRequest(user.email, code)
                                                            )
                                                                .enqueue(object : Callback<Void> {
                                                                    override fun onFailure(call: Call<Void?>, t: Throwable) {
                                                                        Toast.makeText(this@FaceScannerActivity,"Верификация QR кода не удалалсь, попробуйте еще раз.",Toast.LENGTH_LONG).show()
                                                                    }
                                                                    override fun onResponse(call: Call<Void?>, response2: Response<Void?> ){
                                                                        Toast.makeText(this@FaceScannerActivity,"Вход выполнен",Toast.LENGTH_LONG).show()
                                                                        val resultIntent = Intent()
                                                                        resultIntent.putExtra("result_key", "good")
                                                                        setResult(RESULT_OK, resultIntent)
                                                                        finish()
                                                                    }
                                                                })
                                                        } else {
                                                            Toast.makeText(this@FaceScannerActivity,"Лицо не опознано, попробуйте еще раз!",Toast.LENGTH_LONG).show()
                                                        }
                                                    }

                                                    override fun onFailure(
                                                        call: Call<ResponseBody?>,
                                                        t: Throwable
                                                    ) {
                                                        Toast.makeText(this@FaceScannerActivity,"Лицо не опознано, попробуйте еще раз!",Toast.LENGTH_LONG).show()
                                                    }
                                                })
                                        }
                                        else if(currentMode == "registration") {
                                            var email = intent.getStringExtra("email")?:""
                                            val emailPart = email
                                                .toRequestBody("text/plain".toMediaTypeOrNull())

                                            RetrofitClient.authService.registerFace(emailPart,body)
                                                .enqueue(object:Callback<LoginResponse>{
                                                    override fun onResponse(
                                                        call: Call<LoginResponse?>,
                                                        response: Response<LoginResponse?>
                                                    ) {
                                                        if(response.isSuccessful) {
                                                            val responseBody = response.body()
                                                            authPrefs.accessToken = responseBody?.accessToken
                                                            authPrefs.refreshToken = responseBody?.refreshToken
                                                            authPrefs.userJson = Gson().toJson(responseBody?.user)

                                                            Toast.makeText(this@FaceScannerActivity,"Регистрация завершена",Toast.LENGTH_LONG).show()
                                                            val resultIntent = Intent()
                                                            resultIntent.putExtra("result_key", "good")
                                                            setResult(RESULT_OK, resultIntent)
                                                            finish()
                                                        }
                                                    }

                                                    override fun onFailure(
                                                        call: Call<LoginResponse?>,
                                                        t: Throwable
                                                    ) {
                                                        Toast.makeText(this@FaceScannerActivity,"Ошибка при сохранении лица",Toast.LENGTH_LONG).show()

                                                    }
                                                })
                                        }

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

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    fun yuvToRgb(image: ImageProxy): Bitmap {
        val width = image.width
        val height = image.height

        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Y channel
        yBuffer.get(nv21, 0, ySize)

        // U and V channels
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        // Manual YUV to RGB conversion
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yValue = nv21[y * width + x].toInt() and 0xFF
                val uIndex = (y / 2) * (width / 2) + (x / 2) + ySize
                val vIndex = (y / 2) * (width / 2) + (x / 2) + ySize + uSize
                val uValue = nv21[uIndex].toInt() and 0xFF
                val vValue = nv21[vIndex].toInt() and 0xFF

                // Convert YUV to RGB
                var r = yValue + (1.370705 * (vValue - 128)).toInt()
                var g = yValue - (0.698001 * (vValue - 128)).toInt() - (0.337633 * (uValue - 128)).toInt()
                var b = yValue + (1.732446 * (uValue - 128)).toInt()

                // Clamp values to 0-255
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                pixels[y * width + x] = android.graphics.Color.argb(255, r, g, b)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }



    fun rotateImageFile90(file: File): File {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        // Поворот на 90° против часовой стрелки
        val matrix = Matrix().apply {
            postRotate(90f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        // Перезапись исходного файла (или можешь сохранить как новый файл)
        FileOutputStream(file).use { out ->
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }

        return file
    }

    fun saveBitmapToFile(bitmap: Bitmap) {
        val fileName = "captured_${System.currentTimeMillis()}.jpg"
        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CameraX"
        )
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }

        // ⬇️ Это добавит файл в галерею
        MediaScannerConnection.scanFile(
            this,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null
        )
    }


    fun Image.toBitmap(rotationDegrees: Int): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()

        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Поворачиваем, если нужно
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        return bitmap
    }


    private fun translateRect(faceRect: Rect, image: Image, view: PreviewView): Rect {
        val scaleX = view.width.toFloat() / image.height.toFloat()
        val scaleY = view.height.toFloat() / image.width.toFloat()

        val left = (faceRect.left * scaleX).toInt()
        val top = (faceRect.top * scaleY).toInt()
        val right = (faceRect.right * scaleX).toInt()
        val bottom = (faceRect.bottom * scaleY).toInt()

        val width = right - left
        val height = bottom - top

        val paddingX = (width * 0.1f).toInt()   // 20% по ширине
        val paddingY = (height * 0.1f).toInt()  // 30% по высоте (вверх/вниз)

        return Rect(
            (left - paddingX).coerceAtLeast(0),
            (top - paddingY).coerceAtLeast(0),
            (right + paddingX).coerceAtMost(view.width),
            (bottom + paddingY).coerceAtMost(view.height)
        )
    }

    fun imageToBitmap(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)

        val yuvBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuvBytes, 0, yuvBytes.size)
    }

    fun yuvToBitmap(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val jpegBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    fun saveImageToGallery(context: Context, imageFile: File) {
        val filename = imageFile.name
        val mimeType = "image/jpeg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val resolver = context.contentResolver
        val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TFADiploma") // Папка в Галерее
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
            val galleryFile = File(imagesDir, filename)
            imageFile.copyTo(galleryFile, overwrite = true)
            MediaStore.Images.Media.insertImage(resolver, galleryFile.absolutePath, filename, null)
            null // не нужен URI
        }

        imageUri?.let { uri ->
            resolver.openOutputStream(uri)?.use { outputStream ->
                imageFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    fun createImagePartFromFile(file: File): MultipartBody.Part {
        val requestFile = file
            .asRequestBody("image/jpeg".toMediaTypeOrNull())

        return MultipartBody.Part.createFormData("image", file.name, requestFile)
    }

    fun saveBitmapAsJpeg(context: Context, bitmap: Bitmap, fileName: String): File {
        val file = File(context.cacheDir, "$fileName.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return file
    }
}

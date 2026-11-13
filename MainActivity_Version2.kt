import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var resultText: TextView

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.resultText)
        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<com.google.android.material.camera.CameraView>(R.id.previewView).surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val bitmap = imageProxy.toBitmap() // أو استخدم helper تحويل Bitmap
        val inputImage = InputImage.fromBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val extracted = extractWeight(visionText.text)

                if (extracted != null) {
                    runOnUiThread {
                        resultText.text = "الوزن: $extracted"
                    }
                }

                imageProxy.close()
            }
            .addOnFailureListener {
                Log.e("OCR", "OCR failed", it)
                imageProxy.close()
            }
    }

    private fun extractWeight(rawText: String): String? {
        // مثال: استخراج قيمة رقمية مثل 72.5 أو 72 أو 0.5
        val regex = Regex("""\d{1,6}(?:[.,]\d{1,3})?""")
        val matches = regex.findAll(rawText)
            .map { it.value.replace(",", ".") }
            .filter { it.toDoubleOrNull() != null }
            .toList()

        if (matches.isEmpty()) return null

        // يمكن اختيار أول قيمة كبيرة أو أكثر تكرارًا
        return matches.firstOrNull { it.toDouble() > 0 } ?: matches.first()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
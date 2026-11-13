package com.example.weightreader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var readingView: TextView
    private lateinit var statusView: TextView
    private lateinit var cameraExecutor: ExecutorService

    private val isProcessing = AtomicBoolean(false)
    private val recentReadings = mutableListOf<String>()

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        createScreen()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    private fun createScreen() {
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        readingView = TextView(this).apply {
            text = "—.-- g"
            textSize = 42f
            gravity = Gravity.CENTER
        }

        statusView = TextView(this).apply {
            text = "وجّه الكاميرا نحو شاشة الميزان"
            textSize = 16f
            gravity = Gravity.CENTER
        }

        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 16)
            addView(readingView)
            addView(statusView)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                previewView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                infoPanel,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        setContentView(root)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                statusView.text = "تعذّر تشغيل الكاميرا"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(imageProxy: androidx.camera.core.ImageProxy) {
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                val value = parseWeight(result.text)
                runOnUiThread {
                    if (value == null) {
                        recentReadings.clear()
                        statusView.text = "لم تُقرأ قيمة واضحة؛ قرّب الكاميرا وثبّتها"
                    } else {
                        recordReading(value)
                    }
                }
            }
            .addOnFailureListener {
                runOnUiThread {
                    statusView.text = "حدث خطأ أثناء قراءة الصورة"
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessing.set(false)
            }
    }

    private fun parseWeight(text: String): String? {
        // توحيد الأرقام العربية والفارسية إلى أرقام لاتينية.
        val normalized = buildString {
            for (char in text) {
                append(
                    when (char) {
                        in '٠'..'٩' -> '0' + (char - '٠')
                        in '۰'..'۹' -> '0' + (char - '۰')
                        '٫', ',' -> '.'
                        else -> char
                    }
                )
            }
        }

        // القراءة مع الفاصلة، مثل 20.43.
        val decimalMatch = Regex("""(?<!\d)(\d{2})\s*\.\s*(\d{2})(?!\d)""")
            .find(normalized)

        if (decimalMatch != null) {
            return "${decimalMatch.groupValues[1]}.${decimalMatch.groupValues[2]}"
        }

        // إذا تجاهل OCR الفاصلة، مثل 2043، نعيد وضعها بعد الرقمين الأولين.
        val digitsOnlyMatch = Regex("""(?<!\d)(\d{4})(?!\d)""")
            .find(normalized)

        if (digitsOnlyMatch != null) {
            val digits = digitsOnlyMatch.value
            return "${digits.substring(0, 2)}.${digits.substring(2, 4)}"
        }

        return null
    }

    private fun recordReading(value: String) {
        recentReadings.add(value)
        if (recentReadings.size > 4) {
            recentReadings.removeAt(0)
        }

        if (recentReadings.size == 4 && recentReadings.distinct().size == 1) {
            readingView.text = "$value g"
            statusView.text = "قراءة مستقرة"
        } else {
            statusView.text = "جارٍ التحقق من القراءة… $value"
        }
    }

    @Deprecated("تُستخدم هذه الدالة للتوافق مع طلب صلاحية الكاميرا")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            statusView.text = "يلزم السماح باستخدام الكاميرا لتشغيل التطبيق"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        recognizer.close()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }
}
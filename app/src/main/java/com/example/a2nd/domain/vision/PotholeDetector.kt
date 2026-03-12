package com.example.a2nd.domain.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

data class DetectionResult(
    val label: String,
    val score: Float
)

class PotholeDetector(
    private val context: Context,
    private val onResults: (List<DetectionResult>) -> Unit
) : ImageAnalysis.Analyzer {

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    // YOLOv8 standard input size is 640x640 as confirmed by your error log 
    // (4,915,200 bytes / 4 bytes per float / 3 channels = 640x640)
    private val inputSize = 640 
    
    // YOLOv8 Nano standard: 8400 boxes
    private val numBoxes = 8400
    // UPDATED: Changed from 5 to 7 attributes [x, y, w, h, class1, class2, class3]
    private val numAttributes = 7
    
    // UPDATED THRESHOLD: 60% for the Indian Road Model
    private val scoreThreshold = 0.60f

    init {
        try {
            // Using FileUtil for stable loading
            val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, "pothole_model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            // CUSTOM BRAIN CONFIRMATION
            Log.d("GRIIN", "Indian Road Model Loaded Successfully (640x640 Float32)")
        } catch (e: Exception) {
            isModelLoaded = false
            Log.e("GRIIN", "PotholeDetector: Model initialization failed: ${e.message}")
        }
    }

    fun isReady(): Boolean = isModelLoaded && interpreter != null

    override fun analyze(image: ImageProxy) {
        try {
            if (!isReady()) return

            // 1. Input Resizing: Explicitly resize to exactly 640x640
            val originalBitmap = image.toBitmap()
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, inputSize, inputSize, true)
            
            // 2. Buffer Alignment: Allocation for FLOAT32 (4 bytes per channel)
            val imgData = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
            imgData.order(ByteOrder.nativeOrder())
            imgData.rewind()

            val intValues = IntArray(inputSize * inputSize)
            scaledBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

            // Pack the RGB values into the buffer as normalized Float32 [0, 1]
            for (pixelValue in intValues) {
                imgData.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
                imgData.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
                imgData.putFloat((pixelValue and 0xFF) / 255.0f)
            }

            // 3. YOLOv8 Output Processing: Updated Shape [1, 7, 8400]
            val output = Array(1) { Array(numAttributes) { FloatArray(numBoxes) } }
            
            // Run inference
            imgData.rewind()
            interpreter?.run(imgData, output)

            val results = mutableListOf<DetectionResult>()
            
            // Loop through 8,400 candidate boxes
            for (i in 0 until numBoxes) {
                // YOLOv8 format [1, 7, 8400]: indices 4, 5, 6 are class confidences
                // Find the highest confidence among the 3 classes
                val classScores = floatArrayOf(output[0][4][i], output[0][5][i], output[0][6][i])
                var maxScore = 0f
                var maxIndex = -1
                
                for (j in classScores.indices) {
                    if (classScores[j] > maxScore) {
                        maxScore = classScores[j]
                        maxIndex = j
                    }
                }
                
                // If the highest class (assuming index 4 is pothole) is above threshold
                if (maxScore >= scoreThreshold) {
                    // Map index 0 to "pothole", or just call it "pothole" if it's the dominant detection
                    results.add(DetectionResult("pothole", maxScore))
                }
            }
            
            if (results.isNotEmpty()) {
                // The server report is triggered by this callback in CameraPreviewScreen.kt
                onResults(results)
            }
            
        } catch (e: Exception) {
            Log.e("GRIIN", "PotholeDetector: Frame analysis failed - ${e.message}")
        } finally {
            // Essential to prevent memory leak and camera freeze
            image.close()
        }
    }
}

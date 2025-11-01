package apc.offline.mrd.ocrlib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class OfflineOcrHelperV2(private val context: Context) {

    private var roiInterpreter: Interpreter? = null
    private var mrInterpreter: Interpreter? = null
    private var mnInterpreter: Interpreter? = null

    private val ROI_MODEL = "roi_best_float32_25102025.tflite"
    private val MR_MODEL  = "mr_best_float32_251012025.tflite"
    private val MN_MODEL  = "mn_best_float32_25102025.tflite"

    // ⚡ NEW: Thresholds updated
    private val ROI_CONF = 0.25f
    private val READ_CONF = 0.2f
    private val NUM_CONF = 0.3f
    private val NMS_IOU = 0.45f

    // ⚡ NEW: Target size for cropped ROIs
    private val TARGET_W = 500
    private val TARGET_H = 180

    private val ROI_LABELS = mapOf(
        0 to "METER_READING",
        1 to "METER_NUMBER"
    )

    private val READ_LABELS = listOf(
        "0","1","2","3","4","5","6","7","8","9",".",":","KWH","KVAH",
        "UNIT","KW","KVA","V","A","KVARH","CUM","PF","KVAR","MD"
    )

    private val NUM_LABELS = listOf(
        "0","1","2","3","4","5","6","7","8","9","E","L","S","Z","N","/","A","C",
        "R","Y","-","I","T","J","B","M","P","D","X","U","W","G","z","V","F"
    )

    init { loadModels() }

    private fun loadModels() {
        try {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
                setUseXNNPACK(true)
            }

            roiInterpreter = Interpreter(FileUtil.loadMappedFile(context, ROI_MODEL), options)
            mrInterpreter  = Interpreter(FileUtil.loadMappedFile(context, MR_MODEL), options)
            mnInterpreter  = Interpreter(FileUtil.loadMappedFile(context, MN_MODEL), options)

            Log.d("OfflineOCR", "✅ Models loaded successfully")
        } catch (e: Exception) {
            Log.e("OfflineOCR", "❌ Model load error: ${e.message}")
        }
    }

    /**
     * Process image based on unit type
     */
    fun processImage(bitmap: Bitmap, unit: Int): OcrResult {
        val start = System.currentTimeMillis()

        Log.d("OfflineOCR", "🎯 Processing unit: $unit")

        // ================= STEP 1: ROI DETECTION =================
        val roiStart = System.currentTimeMillis()
        val roiBoxes = detect(roiInterpreter!!, bitmap, ROI_CONF, NMS_IOU)
        val roiTime = (System.currentTimeMillis() - roiStart) / 1000f

        Log.d("OfflineOCR", "📍 ROI: Found ${roiBoxes.size} regions in ${roiTime}s")

        val lcdBox = roiBoxes.firstOrNull { it.classId == 0 }  // METER_READING
        val numBox = roiBoxes.firstOrNull { it.classId == 1 }  // METER_NUMBER

        var reading = ""
        var meterNum = ""
        var readTime = 0f
        var numTime = 0f

        when (unit) {
            1 -> { // KWH - Get both
                Log.d("OfflineOCR", "📖 Unit=KWH → Reading + Number")

                // Meter Reading
                if (lcdBox != null) {
                    val crop = cropAndResize(bitmap, lcdBox, TARGET_W, TARGET_H)
                    val t0 = System.currentTimeMillis()
                    val mrBoxes = detect(mrInterpreter!!, crop, READ_CONF, NMS_IOU)
                    val t1 = System.currentTimeMillis()
                    readTime = (t1 - t0) / 1000f
                    reading = parseDigits(mrBoxes, READ_LABELS)
                    Log.d("OfflineOCR", "📖 Reading: '$reading' | ${mrBoxes.size} boxes")
                    crop.recycle()
                } else {
                    Log.w("OfflineOCR", "⚠️ LCD box not found")
                }

                // Meter Number
                if (numBox != null) {
                    val crop = cropAndResize(bitmap, numBox, TARGET_W, TARGET_H)
                    val t0 = System.currentTimeMillis()
                    val mnBoxes = detect(mnInterpreter!!, crop, NUM_CONF, NMS_IOU)
                    val t1 = System.currentTimeMillis()
                    numTime = (t1 - t0) / 1000f
                    meterNum = parseDigits(mnBoxes, NUM_LABELS)
                    Log.d("OfflineOCR", "🔢 Number: '$meterNum' | ${mnBoxes.size} boxes")
                    crop.recycle()
                } else {
                    Log.w("OfflineOCR", "⚠️ Number box not found")
                }
            }

            else -> { // Other units - Only reading
                Log.d("OfflineOCR", "📖 Unit=$unit → Reading only")

                if (lcdBox != null) {
                    val crop = cropAndResize(bitmap, lcdBox, TARGET_W, TARGET_H)
                    val t0 = System.currentTimeMillis()
                    val mrBoxes = detect(mrInterpreter!!, crop, READ_CONF, NMS_IOU)
                    val t1 = System.currentTimeMillis()
                    readTime = (t1 - t0) / 1000f
                    reading = parseDigits(mrBoxes, READ_LABELS)
                    Log.d("OfflineOCR", "📖 Reading: '$reading' | ${mrBoxes.size} boxes")
                    crop.recycle()
                } else {
                    Log.w("OfflineOCR", "⚠️ LCD box not found")
                }

                meterNum = ""
            }
        }

        val totalTime = (System.currentTimeMillis() - start) / 1000f

        Log.d("OfflineOCR", "✅ DONE: Unit=$unit | Reading='$reading' | Number='$meterNum' | ${totalTime}s")

        return OcrResult(
            reading = reading,
            meterNumber = meterNum,
            roiCount = roiBoxes.size,
            timings = Timings(totalTime, roiTime, readTime, numTime)
        )
    }

    // ===================== DETECTION CORE =====================

    private fun detect(
        interpreter: Interpreter,
        img: Bitmap,
        confThreshold: Float,
        iouThreshold: Float
    ): List<Detection> {

        val inputShape = interpreter.getInputTensor(0).shape()
        val inH = inputShape[1]
        val inW = inputShape[2]

        val resized = Bitmap.createScaledBitmap(img, inW, inH, true)
        val input = bitmapToBuffer(resized, inW, inH)
        resized.recycle()

        val outputShape = interpreter.getOutputTensor(0).shape()
        val nCh = outputShape[1]
        val nElem = outputShape[2]
        val outSize = nCh * nElem

        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        interpreter.run(input, outputBuffer)
        outputBuffer.rewind()

        val outArray = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(outArray)

        // Parse predictions
        val rawBoxes = mutableListOf<RawBox>()

        for (i in 0 until nElem) {
            val cx = outArray[0 * nElem + i]
            val cy = outArray[1 * nElem + i]
            val bw = outArray[2 * nElem + i]
            val bh = outArray[3 * nElem + i]

            var maxConf = 0f
            var maxClass = 0
            for (j in 4 until nCh) {
                val score = outArray[j * nElem + i]
                if (score > maxConf) {
                    maxConf = score
                    maxClass = j - 4
                }
            }

            if (maxConf < confThreshold) continue

            val x1 = ((cx - bw / 2) * img.width).toInt().coerceIn(0, img.width)
            val y1 = ((cy - bh / 2) * img.height).toInt().coerceIn(0, img.height)
            val x2 = ((cx + bw / 2) * img.width).toInt().coerceIn(0, img.width)
            val y2 = ((cy + bh / 2) * img.height).toInt().coerceIn(0, img.height)

            if (x2 > x1 && y2 > y1) {
                rawBoxes.add(RawBox(x1, y1, x2 - x1, y2 - y1, maxClass, maxConf))
            }
        }

        // Apply NMS
        return applyNMS(rawBoxes, iouThreshold)
    }

    // ===================== NMS (Similar to cv2.dnn.NMSBoxes) =====================

    private fun applyNMS(boxes: List<RawBox>, iouThreshold: Float): List<Detection> {
        if (boxes.isEmpty()) return emptyList()

        val sortedBoxes = boxes.sortedByDescending { it.confidence }
        val keep = mutableListOf<RawBox>()

        for (box in sortedBoxes) {
            var shouldKeep = true
            for (kept in keep) {
                if (computeIOU(box, kept) > iouThreshold) {
                    shouldKeep = false
                    break
                }
            }
            if (shouldKeep) {
                keep.add(box)
            }
        }

        return keep.map { box ->
            Detection(box.x, box.y, box.x + box.w, box.y + box.h, box.classId, box.confidence)
        }
    }

    private fun computeIOU(a: RawBox, b: RawBox): Float {
        val xx1 = max(a.x, b.x)
        val yy1 = max(a.y, b.y)
        val xx2 = min(a.x + a.w, b.x + b.w)
        val yy2 = min(a.y + a.h, b.y + b.h)

        val w = max(0, xx2 - xx1)
        val h = max(0, yy2 - yy1)
        val inter = (w * h).toFloat()

        val areaA = (a.w * a.h).toFloat()
        val areaB = (b.w * b.h).toFloat()
        val union = areaA + areaB - inter

        return if (union > 0) inter / union else 0f
    }

    // ===================== CROP AND RESIZE =====================

    /**
     * Crop ROI and resize to targetW x targetH maintaining aspect ratio
     * Similar to Python's crop_and_resize function
     */
    private fun cropAndResize(bitmap: Bitmap, box: Detection, targetW: Int, targetH: Int): Bitmap {
        val x1 = box.x1.coerceIn(0, bitmap.width)
        val y1 = box.y1.coerceIn(0, bitmap.height)
        val x2 = box.x2.coerceIn(0, bitmap.width)
        val y2 = box.y2.coerceIn(0, bitmap.height)
        val w = x2 - x1
        val h = y2 - y1

        if (w <= 0 || h <= 0) {
            return Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        }

        // Crop ROI
        val roi = Bitmap.createBitmap(bitmap, x1, y1, w, h)

        // Calculate scale to fit within target size
        val scale = min(targetW.toFloat() / w, targetH.toFloat() / h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()

        // Resize
        val resized = Bitmap.createScaledBitmap(roi, newW, newH, true)
        roi.recycle()

        // Create canvas and center the resized image
        val canvas = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvasObj = Canvas(canvas)
        val xOffset = (targetW - newW) / 2f
        val yOffset = (targetH - newH) / 2f

        val matrix = Matrix().apply {
            postTranslate(xOffset, yOffset)
        }
        canvasObj.drawBitmap(resized, matrix, null)
        resized.recycle()

        return canvas
    }

    // ===================== PARSE DIGITS =====================

    /**
     * Decode detections with improved digit extraction
     * Similar to Python's decode_sorted_detections
     */
    private fun parseDigits(dets: List<Detection>, labels: List<String>): String {
        if (dets.isEmpty()) return ""

        // Sort left to right
        val sorted = dets.sortedBy { it.x1 }

        val rawText = sorted.mapNotNull { det ->
            if (det.classId < labels.size) labels[det.classId] else null
        }.joinToString("")

        if (rawText.isEmpty()) return ""

        // Extract digits and decimal point first
        val digits = rawText.filter { it.isDigit() || it == '.' }

        // Extract non-digit suffix (like KWH, KVAH, etc)
        val suffix = rawText.filter { !it.isDigit() && it != '.' }

        return digits + suffix
    }

    // ===================== HELPERS =====================

    private fun bitmapToBuffer(bitmap: Bitmap, w: Int, h: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * w * h * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (p in pixels) {
            buffer.putFloat(((p shr 16) and 0xFF) / 255f)
            buffer.putFloat(((p shr 8) and 0xFF) / 255f)
            buffer.putFloat((p and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    // ===================== DATA CLASSES =====================

    private data class RawBox(
        val x: Int, val y: Int, val w: Int, val h: Int,
        val classId: Int, val confidence: Float
    )

    data class Detection(
        val x1: Int, val y1: Int, val x2: Int, val y2: Int,
        val classId: Int, val confidence: Float
    )

    data class OcrResult(
        val reading: String,
        val meterNumber: String,
        val roiCount: Int,
        val timings: Timings
    )

    data class Timings(
        val total: Float,
        val roi: Float,
        val reading: Float,
        val number: Float
    )

    fun close() {
        roiInterpreter?.close()
        mrInterpreter?.close()
        mnInterpreter?.close()
    }
}
package apc.offline.mrd.ocrlib

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.apply
import kotlin.collections.drop
import kotlin.collections.filter
import kotlin.collections.filterIndexed
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.getOrPut
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.collections.last
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.maxOrNull
import kotlin.collections.sorted
import kotlin.collections.sortedBy
import kotlin.collections.sortedByDescending
import kotlin.collections.take
import kotlin.collections.toMutableList
import kotlin.collections.zip
import kotlin.collections.zipWithNext
import kotlin.let
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.ranges.coerceIn
import kotlin.ranges.until
import kotlin.text.format
import kotlin.text.isBlank
import kotlin.text.isNotEmpty
import kotlin.text.repeat
import kotlin.to

class OfflineOcrHelper(private val context: Context) {

    private var roiInterpreter: Interpreter? = null
    private var mnInterpreter: Interpreter? = null
    private var mrInterpreter: Interpreter? = null

    private val ROI_MODEL = "ROI_float32_17102025_v8.tflite"
    private val MN_MODEL = "MN_float32_17102025_v8.tflite"
    private val MR_MODEL = "MR_float32_17102025_v7.tflite"

    private val ROI_NAMES = mapOf(
        0 to "GREEN_LCD",
        1 to "METER_NUMBER",
        2 to "METER_MODEL"
    )

    init {
        loadModels()
    }

    private fun loadModels() {
        try {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
                setUseXNNPACK(true)
            }

            roiInterpreter = Interpreter(FileUtil.loadMappedFile(context, ROI_MODEL), options)
            mnInterpreter = Interpreter(FileUtil.loadMappedFile(context, MN_MODEL), options)
            mrInterpreter = Interpreter(FileUtil.loadMappedFile(context, MR_MODEL), options)

            Log.d("OfflineOCR", "✅ Models loaded")
        } catch (e: Exception) {
            Log.e("OfflineOCR", "Error loading models: ${e.message}")
            e.printStackTrace()
        }
    }

    fun processImage(bitmap: Bitmap, unit: Int?): OcrOfflineResult {
        return try {
            Log.d("OfflineOCR", "=".repeat(70))
            Log.d("OfflineOCR", "🔍 STEP 1: ROI Detection")
            Log.d("OfflineOCR", "=".repeat(70))

            val roiBoxes = detectROI(bitmap)
            Log.d("OfflineOCR", "✓ Found ${roiBoxes.size} ROI regions")

            val greenLcdBox = roiBoxes.firstOrNull { it.classId == 0 }
            val meterNumBox = roiBoxes.firstOrNull { it.classId == 1 }

            roiBoxes.forEach { box ->
                val name = ROI_NAMES[box.classId] ?: "UNKNOWN_${box.classId}"
                Log.d("OfflineOCR", "[$name]: ${String.format("%.3f", box.confidence)}")
            }

            var meterReading = ""
            var meterNumber = ""

            Log.d("OfflineOCR", "\n" + "=".repeat(70))
            Log.d("OfflineOCR", "📊 STEP 2: Meter Reading")
            Log.d("OfflineOCR", "=".repeat(70))

            if (greenLcdBox != null) {
                val lcdCrop = cropBitmap(bitmap, greenLcdBox, 20)
                val (boxes, classes) = detectDigits(mrInterpreter!!, lcdCrop, 0.15f)
                meterReading = parseMeterReading(boxes, classes)
                lcdCrop.recycle()
                Log.d("OfflineOCR", "✅ Reading: $meterReading")
            } else {
                Log.w("OfflineOCR", "❌ LCD not detected")
            }

            Log.d("OfflineOCR", "\n" + "=".repeat(70))
            Log.d("OfflineOCR", "🔢 STEP 3: Meter Number")
            Log.d("OfflineOCR", "=".repeat(70))

            if (meterNumBox != null) {
                val numCrop = cropBitmap(bitmap, meterNumBox, 20)
                val (boxes, classes) = detectDigits(mnInterpreter!!, numCrop, 0.15f)
                meterNumber = parseMeterNumber(boxes, classes)
                numCrop.recycle()
                Log.d("OfflineOCR", "✅ Number: $meterNumber")
            } else {
                Log.w("OfflineOCR", "❌ Number not detected")
            }

            Log.d("OfflineOCR", "\n" + "=".repeat(70))
            Log.d("OfflineOCR", "📋 FINAL: Reading=$meterReading, Number=$meterNumber")
            Log.d("OfflineOCR", "=".repeat(70))

            val detectedUnit = unit ?: 1
            val exceptionCode = if (meterReading.isNotEmpty()) 1 else 48

            OcrOfflineResult(
                success = true,
                meterNumber = meterNumber,
                meterReading = meterReading,
                ocrUnit = detectedUnit.toString(),
                roiCoordinates = null,
                errorMessage = null,
                exceptionCode = exceptionCode
            )

        } catch (e: Exception) {
            Log.e("OfflineOCR", "Error: ${e.message}")
            e.printStackTrace()
            OcrOfflineResult(
                success = false,
                meterNumber = "",
                meterReading = "",
                ocrUnit = "",
                roiCoordinates = null,
                errorMessage = e.message,
                exceptionCode = 48
            )
        }
    }

    // ==================== ROI DETECTION ====================
    private fun detectROI(bitmap: Bitmap): List<DetectionBox> {
        return roiInterpreter?.let { interpreter ->
            try {
                val (boxes, classes, scores) = detectClean(interpreter, bitmap, 0.30f, false)
                boxes.mapIndexed { idx, box ->
                    DetectionBox(
                        box[0].toInt(), box[1].toInt(), box[2].toInt(), box[3].toInt(),
                        classes[idx], scores[idx]
                    )
                }
            } catch (e: Exception) {
                Log.e("OfflineOCR", "ROI error: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
    }

    // ==================== DIGIT DETECTION ====================
    private fun detectDigits(interpreter: Interpreter, bitmap: Bitmap, conf: Float): Pair<List<List<Float>>, List<Int>> {
        return try {
            val (boxes, classes, _) = detectClean(interpreter, bitmap, conf, true)
            Pair(boxes, classes)
        } catch (e: Exception) {
            Log.e("OfflineOCR", "Digit detection error: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }

    // ==================== DETECT CLEAN (EXACT PYTHON TRANSLATION) ====================
    private fun detectClean(
        interpreter: Interpreter,
        img: Bitmap,
        conf: Float,
        filterDigits: Boolean
    ): Triple<List<List<Float>>, List<Int>, List<Float>> {

        val h = img.height
        val w = img.width

        val inputShape = interpreter.getInputTensor(0).shape()
        val inHeight = inputShape[1]
        val inWidth = inputShape[2]

        // Preprocess
        val resized = Bitmap.createScaledBitmap(img, inWidth, inHeight, true)
        val inputBuffer = bitmapToBuffer(resized, inWidth, inHeight)
        resized.recycle()

        // Run inference
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputSize = outputShape[1] * outputShape[2]
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val outArray = FloatArray(outputSize)
        outputBuffer.asFloatBuffer().get(outArray)

        // ✅ CRITICAL: Reshape and transpose (Python: out.T)
        var nCh = outputShape[1]
        var nEl = outputShape[2]

        // Check if transpose needed (Python: if out.shape[0] > out.shape[1]: out = out.T)
        if (nCh > nEl) {
            val temp = nCh
            nCh = nEl
            nEl = temp
        }

        Log.d("OfflineOCR", "Output shape: nCh=$nCh, nEl=$nEl")

        // Detect boxes
        val boxes = mutableListOf<List<Float>>()
        val classes = mutableListOf<Int>()
        val scores = mutableListOf<Float>()
        val validDigits = setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

        for (i in 0 until nEl) {
            if (nCh < 5) continue

            // ✅ Python equivalent: cx, cy, bw, bh = out[0:4, i]
            val cx = outArray[0 * nEl + i]
            val cy = outArray[1 * nEl + i]
            val bw = outArray[2 * nEl + i]
            val bh = outArray[3 * nEl + i]

            // ✅ Python equivalent: cls_scores = out[4:, i]
            var maxConf = 0f
            var maxClass = 0
            for (j in 4 until nCh) {
                val score = outArray[j * nEl + i]
                if (score > maxConf) {
                    maxConf = score
                    maxClass = j - 4
                }
            }

            if (filterDigits && maxClass !in validDigits) continue
            if (maxConf <= conf) continue

            // Convert to pixel coords
            var x1 = ((cx - bw / 2) * w).toInt()
            var y1 = ((cy - bh / 2) * h).toInt()
            var x2 = ((cx + bw / 2) * w).toInt()
            var y2 = ((cy + bh / 2) * h).toInt()

            x1 = x1.coerceIn(0, w)
            y1 = y1.coerceIn(0, h)
            x2 = x2.coerceIn(0, w)
            y2 = y2.coerceIn(0, h)

            if (x2 > x1 && y2 > y1) {
                boxes.add(listOf(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()))
                classes.add(maxClass)
                scores.add(maxConf)
            }
        }

        // Apply NMS
        val (nmsBoxes, nmsScores, nmsClasses) = aggressiveNmsByClass(boxes, scores, classes, 0.15f)

        return Triple(nmsBoxes, nmsClasses, nmsScores)
    }

    // ==================== AGGRESSIVE NMS ====================
    private fun aggressiveNmsByClass(
        boxes: List<List<Float>>,
        scores: List<Float>,
        classes: List<Int>,
        iouThresh: Float
    ): Triple<List<List<Float>>, List<Float>, List<Int>> {

        if (boxes.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())

        val classGroups = mutableMapOf<Int, MutableList<Int>>()
        classes.forEachIndexed { i, cls ->
            classGroups.getOrPut(cls) { mutableListOf() }.add(i)
        }

        val keepAll = mutableListOf<Int>()

        classGroups.forEach { (_, indices) ->
            val clsBoxes = indices.map { boxes[it] }
            val clsScores = indices.map { scores[it] }

            val x1 = clsBoxes.map { it[0] }
            val y1 = clsBoxes.map { it[1] }
            val x2 = clsBoxes.map { it[2] }
            val y2 = clsBoxes.map { it[3] }
            val areas = clsBoxes.map { (it[2] - it[0]) * (it[3] - it[1]) }

            var order = clsScores.indices.sortedByDescending { clsScores[it] }.toMutableList()
            val keep = mutableListOf<Int>()

            while (order.isNotEmpty()) {
                val i = order[0]
                keep.add(indices[i])
                if (order.size == 1) break

                val rest = order.drop(1)
                val ious = rest.map { j ->
                    val xx1 = max(x1[i], x1[j])
                    val yy1 = max(y1[i], y1[j])
                    val xx2 = min(x2[i], x2[j])
                    val yy2 = min(y2[i], y2[j])
                    val w = max(0f, xx2 - xx1)
                    val h = max(0f, yy2 - yy1)
                    val inter = w * h
                    inter / (areas[i] + areas[j] - inter + 1e-6f)
                }

                order = rest.filterIndexed { idx, _ -> ious[idx] <= iouThresh }.toMutableList()
            }
            keepAll.addAll(keep)
        }

        val sortedKeep = keepAll.sorted()
        return Triple(
            sortedKeep.map { boxes[it] },
            sortedKeep.map { scores[it] },
            sortedKeep.map { classes[it] }
        )
    }

    // ==================== PARSING ====================
    private fun parseMeterReading(boxes: List<List<Float>>, classes: List<Int>): String {
        if (boxes.isEmpty() || classes.isEmpty()) {
            Log.w("OfflineOCR", "⚠️ No boxes or classes detected")
            return ""
        }

        Log.d("OfflineOCR", "✓ Detected ${boxes.size} digits: $classes")

        val dets = boxes.zip(classes).map { (b, c) ->
            mapOf(
                "x" to (b[0] + b[2]) / 2,
                "y" to (b[1] + b[3]) / 2,
                "h" to (b[3] - b[1]),
                "cls" to c
            )
        }

        // Sort by X coordinate (left to right)
        val sorted = dets.sortedBy { it["x"] as Float }

        // ✅ RELAXED HEIGHT FILTER: Accept digits with height > 40% of max (was 60%)
        val heights = sorted.map { it["h"] as Float }
        val maxH = heights.maxOrNull() ?: return ""
        val minHeightThreshold = 0.4f * maxH // ← Reduced from 0.6f

        val validByHeight = sorted.filter { (it["h"] as Float) >= minHeightThreshold }

        if (validByHeight.isEmpty()) {
            Log.w("OfflineOCR", "⚠️ All digits filtered out by height threshold")
            return sorted.joinToString("") { (it["cls"] as Int).toString() } + " kWh"
        }

        Log.d("OfflineOCR", "✓ ${validByHeight.size}/${sorted.size} digits passed height filter")

        // ✅ RELAXED ALIGNMENT FILTER: Accept digits within 30% vertical deviation (was 20%)
        val yCoords = validByHeight.map { it["y"] as Float }
        val medianY = yCoords.sorted()[yCoords.size / 2]
        val alignmentThreshold = 0.3f * maxH // ← Increased from 0.2f

        val validAligned = validByHeight.filter {
            abs((it["y"] as Float) - medianY) < alignmentThreshold
        }

        // ✅ ACCEPT SINGLE DIGITS (was rejecting if < 2)
        if (validAligned.isEmpty()) {
            Log.w("OfflineOCR", "⚠️ All digits filtered by alignment, using all detected")
            return sorted.joinToString("") { (it["cls"] as Int).toString() } + " kWh"
        }

        Log.d("OfflineOCR", "✓ ${validAligned.size} digits after alignment filter")

        // Calculate gaps to detect decimal point
        val gaps = validAligned.zipWithNext { a, b ->
            (b["x"] as Float) - (a["x"] as Float)
        }

        var decIdx = -1
        if (gaps.isNotEmpty()) {
            val sortedGaps = gaps.sorted()
            val medGap = sortedGaps[sortedGaps.size / 2]

            // ✅ RELAXED GAP DETECTION: 1.3x median (was 1.5x)
            gaps.forEachIndexed { i, g ->
                if (g > 1.3f * medGap) { // ← Reduced from 1.5f
                    decIdx = i
                    return@forEachIndexed
                }
            }
        }

        val digits = validAligned.map { (it["cls"] as Int).toString() }

        val reading = when {
            decIdx != -1 && decIdx < digits.size - 1 -> {
                // Decimal point detected by gap
                digits.subList(0, decIdx + 1).joinToString("") + "." +
                        digits.subList(decIdx + 1, digits.size).joinToString("")
            }
            digits.size == 5 -> digits.take(4).joinToString("") + "." + digits[4]
            digits.size == 4 -> digits.take(3).joinToString("") + "." + digits[3]
            digits.size >= 6 -> {
                // For 6+ digits, put decimal before last digit
                val beforeDec = digits.size - 1
                digits.take(beforeDec).joinToString("") + "." + digits.last()
            }
            else -> digits.joinToString("")
        }

        val result = "$reading kWh"
        Log.d("OfflineOCR", "✅ Final reading: $result")
        // ✅ ADD THIS FALLBACK
        if (result.isBlank() || result == " kWh" || result == "kWh") {
            Log.w("OfflineOCR", "⚠️ Empty result, using raw digits")
            val rawDigits = boxes.zip(classes)
                .sortedBy { it.first[0] }
                .joinToString("") { it.second.toString() }
            return if (rawDigits.isNotEmpty()) "$rawDigits kWh" else ""
        }

        return result
    }

    private fun parseMeterNumber(boxes: List<List<Float>>, classes: List<Int>): String {
        if (boxes.isEmpty()) return ""

        Log.d("OfflineOCR", "✓ Detected ${boxes.size} digits")

        val sorted = boxes.zip(classes).sortedBy { it.first[0] }
        return sorted.joinToString("") { it.second.toString() }
    }

    // ==================== HELPERS ====================
    private fun cropBitmap(bitmap: Bitmap, box: DetectionBox, padding: Int): Bitmap {
        val x1 = (box.x1 - padding).coerceIn(0, bitmap.width)
        val y1 = (box.y1 - padding).coerceIn(0, bitmap.height)
        val x2 = (box.x2 + padding).coerceIn(0, bitmap.width)
        val y2 = (box.y2 + padding).coerceIn(0, bitmap.height)

        return Bitmap.createBitmap(bitmap, x1, y1, x2 - x1, y2 - y1)
    }

    private fun bitmapToBuffer(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * width * height * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }

        buffer.rewind()
        return buffer
    }

    fun close() {
        try {
            roiInterpreter?.close()
            mnInterpreter?.close()
            mrInterpreter?.close()
            roiInterpreter = null
            mnInterpreter = null
            mrInterpreter = null
            System.gc()
        } catch (e: Exception) {
            Log.e("OfflineOCR", "Close error: ${e.message}")
        }
    }

    data class DetectionBox(
        val x1: Int, val y1: Int, val x2: Int, val y2: Int,
        val classId: Int, val confidence: Float
    )

    data class OcrOfflineResult(
        val success: Boolean,
        val meterNumber: String,
        val meterReading: String,
        val ocrUnit: String,
        val roiCoordinates: FloatArray?,
        val errorMessage: String?,
        val exceptionCode: Int
    )
}

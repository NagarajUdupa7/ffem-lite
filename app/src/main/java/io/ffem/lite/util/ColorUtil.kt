package io.ffem.lite.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.Paint.Style
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import io.ffem.lite.R
import io.ffem.lite.app.App
import io.ffem.lite.app.App.Companion.getCalibration
import io.ffem.lite.app.AppDatabase
import io.ffem.lite.camera.MAX_ANGLE
import io.ffem.lite.camera.Utilities
import io.ffem.lite.model.*
import io.ffem.lite.preference.getCalibrationColorDistanceTolerance
import io.ffem.lite.preference.getColorDistanceTolerance
import java.util.*
import kotlin.math.*

const val MAX_COLOR_DISTANCE_RGB = 80
const val MAX_COLOR_DISTANCE_CALIBRATION = 40
const val INTERPOLATION_COUNT = 100.0
const val MAX_DISTANCE = 999
const val MIN_BRIGHTNESS = 30

fun getBitmapPixels(bitmap: Bitmap, rect: Rect): IntArray {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(
        pixels, 0, bitmap.width, rect.left, rect.top,
        rect.width(), rect.height()
    )
    val subsetPixels = IntArray(rect.width() * rect.height())
    for (row in 0 until rect.height()) {
        System.arraycopy(
            pixels, row * bitmap.width,
            subsetPixels, row * rect.width(), rect.width()
        )
    }
    return subsetPixels
}

fun isDark(pixels: IntArray): Boolean {
    var r = 0

    if (pixels.isEmpty()) {
        return false
    }

    for (element in pixels) {
        r += element.red
    }

    return (r / pixels.size) < 210
}

fun getAverageColor(pixels: IntArray): Int {

    var r = 0
    var g = 0
    var b = 0
    var total = 0

    for (element in pixels) {
        r += element.red
        g += element.green
        b += element.blue
        total++
    }

    r /= total
    g /= total
    b /= total

    return Color.argb(255, r, g, b)
}

fun hasBlackPixelsOnLine(bitmap: Bitmap, row: Int): Boolean {
    var total = 0

    val pixels = getBitmapPixels(
        bitmap,
        Rect(0, row, bitmap.width, row + 1)
    )

    for (element in pixels) {
        if (element.red < MIN_BRIGHTNESS &&
            element.green < MIN_BRIGHTNESS &&
            element.blue < MIN_BRIGHTNESS
        ) {
            total++
            if (total > 50) {
                return true
            }
        }
    }

    return false
}

//fun hasBlackPixelsInArea(
//    bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int
//): Boolean {
//    var total = 0
//
//    val pixels = getBitmapPixels(
//        bitmap,
//        Rect(left, top, right, bottom)
//    )
//
//    for (element in pixels) {
//        if (element.red < MIN_BRIGHTNESS &&
//            element.green < MIN_BRIGHTNESS &&
//            element.blue < MIN_BRIGHTNESS
//        ) {
//            total++
//            if (total > 50) {
//                return true
//            }
//        }
//    }
//
//    return false
//}

object ColorUtil {

    private var cropLeft = 0
    private var cropRight = 0
    private var cropTop = 0
    private var cropBottom = 0

    fun extractImage(context: Context, id: String, bitmapImage: Bitmap) {

        val detector: FirebaseVisionBarcodeDetector by lazy {

            val options = FirebaseVisionBarcodeDetectorOptions.Builder()
                .setBarcodeFormats(
                    FirebaseVisionBarcode.FORMAT_CODE_128
                )
                .build()
            FirebaseVision.getInstance().getVisionBarcodeDetector(options)
        }

        val bitmap = Utilities.rotateImage(bitmapImage, 90)

        val leftBarcodeBitmap = Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height / 2
        )

        detector.detectInImage(FirebaseVisionImage.fromBitmap(leftBarcodeBitmap))
            .addOnFailureListener(
                fun(_: Exception) {
                    returnResult(context, id)
                }
            )
            .addOnSuccessListener(
                fun(result: List<FirebaseVisionBarcode>) {
                    if (result.isEmpty()) {
                        returnResult(context, id)
                    }
                    for (barcode in result) {
                        if (!barcode.rawValue.isNullOrEmpty()) {

                            val testName = App.getTestName(result[0].displayValue!!)

//                            if (barcode.boundingBox!!.width() > bitmap.width * .44) {
                            try {
                                cropTop = bitmap.width - barcode.boundingBox!!.right - 1
                                cropBottom = (bitmap.width - barcode.boundingBox!!.left) + 1
                                cropLeft = barcode.boundingBox!!.bottom + 1

                                leftBarcodeBitmap.recycle()

                                val rightBarcodeBitmap = Bitmap.createBitmap(
                                    bitmap, 0, bitmap.height / 2,
                                    bitmap.width, bitmap.height / 2
                                )

                                detector.detectInImage(
                                    FirebaseVisionImage.fromBitmap(rightBarcodeBitmap)
                                )
                                    .addOnFailureListener(fun(_: Exception) {
                                        returnResult(context, id)
                                    })
                                    .addOnSuccessListener(
                                        fun(result: List<FirebaseVisionBarcode>) {
                                            for (barcode2 in result) {

                                                // Check if image angle is ok
                                                if (abs(
                                                        barcode.boundingBox!!.left
                                                                - barcode2.boundingBox!!.left
                                                    ) > MAX_ANGLE ||
                                                    abs(
                                                        barcode.boundingBox!!.right
                                                                - barcode2.boundingBox!!.right
                                                    ) > MAX_ANGLE
                                                ) {
                                                    returnResult(
                                                        context,
                                                        id,
                                                        context.getString(R.string.image_tilted),
                                                        bitmap,
                                                        testName
                                                    )
                                                    return
                                                }

                                                val bwBitmap = ImageUtil.toBlackAndWhite(
                                                    rightBarcodeBitmap,
                                                    90
                                                )

                                                if (bwBitmap.width - barcode.boundingBox!!.right > 3) {
                                                    val rect = Rect(
                                                        barcode.boundingBox!!.right + 3,
                                                        10,
                                                        min(
                                                            barcode.boundingBox!!.right + 30,
                                                            bwBitmap.width
                                                        ),
                                                        bwBitmap.height - 5
                                                    )
                                                    val pixels = getBitmapPixels(bwBitmap, rect)
                                                    if (isDark(pixels)) {
                                                        bwBitmap.recycle()
                                                        returnResult(
                                                            context,
                                                            id,
                                                            context.getString(R.string.bad_lighting),
                                                            bitmap
                                                        )
                                                        return
                                                    }
                                                }
                                                cropRight = barcode2.boundingBox!!.top - 1

                                                for (i in cropRight downTo cropRight - 100) {
                                                    if (!hasBlackPixelsOnLine(
                                                            rightBarcodeBitmap,
                                                            i
                                                        )
                                                    ) {
                                                        cropRight = i - 1
                                                        break
                                                    }
                                                }

                                                rightBarcodeBitmap.recycle()
                                                analyzeBarcode(
                                                    context,
                                                    id,
                                                    bitmap,
                                                    result
                                                )
                                            }

                                        }
                                    )
                            } catch (ignored: Exception) {
                                returnResult(context, id)
                            }
//                            } else {
//                                returnResult(context, id)
//                            }
                        } else {
                            returnResult(context, id)
                            return
                        }
                    }
                }
            )
    }

    private fun analyzeBarcode(
        context: Context, id: String, bitmap: Bitmap, result: List<FirebaseVisionBarcode>
    ) {
        if (result.isEmpty()) {
            returnResult(context, id)
        }
        for (barcode2 in result) {
            if (!barcode2.rawValue.isNullOrEmpty()) {
//                if (barcode2.boundingBox!!.width() > bitmap.width * .44) {

                val testName = App.getTestName(result[0].displayValue!!)
                if (testName.isEmpty()) {
                    returnResult(context, id)
                    return
                }

                var bitmapRotated = Utilities.rotateImage(bitmap, 270)

                cropTop = max(0, cropTop - 1)

                bitmapRotated = Bitmap.createBitmap(
                    bitmapRotated, 0, cropTop,
                    bitmapRotated.width, barcode2.boundingBox!!.width() + 3
                )

                val croppedBitmap1 = Bitmap.createBitmap(
                    bitmapRotated, max(1, cropLeft), 0,
                    max(1, cropRight + ((bitmapRotated.width / 2) - cropLeft)),
                    bitmapRotated.height
                )

                val bwCroppedBitmap1 = ImageUtil.toBlackAndWhite(croppedBitmap1, 100)
                var top = 0
                var bottom = 0

                var left = 0
                for (x in 1 until 100) {
                    val rectangle = Rect(x, 10, x + 1, croppedBitmap1.height - 10)
                    val pixels = getBitmapPixels(bwCroppedBitmap1, rectangle)
                    if (isDark(pixels)) {
                        left = x
                        break
                    }
                }

                var right = 0
                for (x in croppedBitmap1.width - 1 downTo croppedBitmap1.width - 100) {
                    val rectangle = Rect(x - 1, 10, x, croppedBitmap1.height - 10)
                    val pixels = getBitmapPixels(bwCroppedBitmap1, rectangle)
                    if (isDark(pixels)) {
                        right = x
                        break
                    }
                }

                val croppedBitmap2 = Bitmap.createBitmap(
                    croppedBitmap1, left, 0,
                    right - left,
                    croppedBitmap1.height
                )
                croppedBitmap1.recycle()
                val bwCroppedBitmap2 = ImageUtil.toBlackAndWhite(croppedBitmap2, 100)
                bwCroppedBitmap1.recycle()

                for (y in 1 until 100) {
                    val rectangle = Rect(3, y, 70, y + 3)
                    val rectangleRight =
                        Rect(bwCroppedBitmap2.width - 70, y, bwCroppedBitmap2.width - 1, y + 3)
                    val pixels = getBitmapPixels(bwCroppedBitmap2, rectangle)
                    val pixelsRight = getBitmapPixels(bwCroppedBitmap2, rectangleRight)
                    if (isDark(pixels) && isDark(pixelsRight)) {
                        top = y
                        break
                    }
                }

                for (y in bwCroppedBitmap2.height - 3 downTo 0) {
                    val pixel =
                        bwCroppedBitmap2.getPixel((bwCroppedBitmap2.width * 0.15).toInt(), y)
                    if (isDarkPixel(pixel)) {
                        bottom = y
                        break
                    }
                }

                bwCroppedBitmap2.recycle()

                val croppedBitmap = Bitmap.createBitmap(
                    croppedBitmap2, 0, top,
                    croppedBitmap2.width,
                    max(1, bottom - top)
                )

                croppedBitmap1.recycle()
                bitmap.recycle()


                var error = ""
                var resultDetail = ResultDetail(-1.0, 0)
                try {
                    resultDetail = extractColors(croppedBitmap, result[0].displayValue!!)
                } catch (e: Exception) {
                    error = "Calibration error"
                }

                Utilities.savePicture(
                    context.applicationContext, id,
                    "", Utilities.bitmapToBytes(croppedBitmap)
                )
                croppedBitmap.recycle()

                val db = AppDatabase.getDatabase(context)
                if (db.resultDao().getResult(id) == null) {
                    Utilities.savePicture(
                        context,
                        id,
                        testName,
                        Utilities.bitmapToBytes(bitmapRotated)
                    )

                    val expectedValue = PreferencesUtil
                        .getString(context, R.string.expectedValueKey, "")

                    db.resultDao().insert(
                        TestResult(
                            id, 0, testName,
                            Date().time, Date().time, "", "",
                            expectedValue, context.getString(R.string.outbox)
                        )
                    )
                }

                bitmapRotated.recycle()

                returnResult(context, id, error, null, testName, resultDetail)
//                } else {
//                    returnResult(context, id)
//                }

            } else {
                returnResult(context, id)
                return
            }
        }
    }

    private fun returnResult(
        context: Context,
        id: String,
        error: String = "Error",
        bitmap: Bitmap? = null,
        testName: String = "Unknown",
        resultDetail: ResultDetail = ResultDetail((-1).toDouble(), 0)
    ) {
        val intent = Intent(App.LOCAL_RESULT_EVENT)
        intent.putExtra(App.TEST_ID_KEY, id)
        intent.putExtra(App.TEST_NAME_KEY, testName)

        var result = (round(resultDetail.result * 100) / 100.0).toString()
        if (resultDetail.result < 0) {
            result = if (error.isEmpty()) {
                "No match"
            } else {
                error
            }
        }

        intent.putExtra(App.TEST_RESULT, result)

        val db = AppDatabase.getDatabase(context)
        if (db.resultDao().getResult(id) == null) {

            if (bitmap != null) {
                val bitmapRotated = Utilities.rotateImage(bitmap, 270)
                Utilities.savePicture(
                    context,
                    id,
                    testName,
                    Utilities.bitmapToBytes(bitmapRotated)
                )
                bitmap.recycle()
                bitmapRotated.recycle()
            }

            val expectedValue = PreferencesUtil
                .getString(context, R.string.expectedValueKey, "")

            db.resultDao().insert(
                TestResult(
                    id, 0, testName,
                    Date().time, Date().time, "", "",
                    expectedValue, ""
                )
            )
        }

        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun extractColors(image: Bitmap, barcodeValue: String): ResultDetail {

        val bitmap = ImageUtil.toBlackAndWhite(image, 100)

        val paint = Paint()
        paint.style = Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 2f
        paint.isAntiAlias = true

        val calibration: List<CalibrationValue> = getCalibration(barcodeValue)

        val intervals = calibration.size / 2
        val commonTop = bitmap.height / intervals
        val padding = commonTop / 7
        var calibrationIndex = 0

        val points = getMarkers(bitmap)

        val commonLeft = points.x
        for (i in 0 until intervals) {
            val rectangle = Rect(
                max(1, commonLeft - padding),
                max(1, (commonTop * i) + (commonTop / 2) - padding),
                min(bitmap.width, commonLeft + padding),
                min(bitmap.height, (commonTop * i) + (commonTop / 2) + padding)
            )

            val pixels = getBitmapPixels(image, rectangle)

            val cal = calibration[calibrationIndex]
            calibrationIndex++
            cal.color = getAverageColor(pixels)

            val canvas = Canvas(image)
            canvas.drawRect(rectangle, paint)
        }

        val commonRight = points.y
        for (i in 0 until intervals) {
            val rectangle = Rect(
                max(1, commonRight - padding),
                max(1, (commonTop * i) + (commonTop / 2) - padding),
                min(bitmap.width, commonRight + padding),
                min(bitmap.height, (commonTop * i) + (commonTop / 2) + padding)
            )

            val pixels = getBitmapPixels(image, rectangle)

            val cal = calibration[calibrationIndex]
            calibrationIndex++
            cal.color = getAverageColor(pixels)

            val canvas = Canvas(image)
            canvas.drawRect(rectangle, paint)
        }

        val x1 = ((commonRight - commonLeft) / 2) + commonLeft
        val y1 = ((bitmap.height) / 2) + (bitmap.height * 0.1).toInt()
        val rectangle = Rect(x1 - 20, y1 - 27, x1 + 20, y1 + 35)
        val pixels = getBitmapPixels(image, rectangle)
        val colorInfo = ColorInfo(getAverageColor(pixels))

        val canvas = Canvas(image)
        canvas.drawRect(rectangle, paint)

        val swatches: ArrayList<Swatch> = ArrayList()

        for (cal in calibration) {
            if (swatches.size >= calibration.size / 2) {
                break
            }

            swatches.add(
                Swatch(
                    cal.value.toDouble(),
                    getCalibrationColor(cal.value, calibration)
                )
            )
        }

        return analyzeColor(swatches.size, colorInfo, generateGradient(swatches))
    }

    private fun getMarkers(
        bitmap: Bitmap
    ): Point {
//        var leftSquareLeft = 0
//        var leftSquareRight: Int = bitmap.width -1

//        var bottleLeft: Int = -1
//        var bottleRight: Int = -1

//        var rightSquareLeft: Int = -1
//        var rightSquareRight: Int = -1

//        for (x in 1 until bitmap.width - 7) {
//            val rectangle = Rect(x, 0, x + 6, 1)
//            val pixels = getBitmapPixels(bitmap, rectangle)
//            if (isDark(pixels)) {
//                leftSquareLeft = x
//                break
//            }
//        }

//        for (x in leftSquareLeft + 10 until bitmap.width - 7) {
//            val rectangle = Rect(x, 0, x + 6, 1)
//            val pixels = getBitmapPixels(bitmap, rectangle)
//            if (isNotDark(pixels)) {
//                leftSquareRight = x
//                break
//            }
//        }

//        for (x in leftSquareRight + 1 until bitmap.width - 1) {
//            val rectangle = Rect(x, 1, x + 1, 20)
//            val pixels = getBitmapPixels(bitmap, rectangle)
//            if (isDark(pixels)) {
//                bottleLeft = x
//                break
//            }
//        }

//        var top = 0
//        for (y in 1 until 40) {
//            val right = bitmap.width - (bitmap.width * 0.15).toInt()
//            val rectangle = Rect(right, y, right + 10, y + 1)
//            val pixels = getBitmapPixels(bitmap, rectangle)
//            if (isDark(pixels)) {
//                top = y
//                break
//            }
//        }

//        for (x in bitmap.width - 11 downTo 0) {
//            val rectangle = Rect(x, top, x + 10, top + 2)
//            val pixels = getBitmapPixels(bitmap, rectangle)
//            if (isDark(pixels)) {
//                rightSquareRight = x
//                break
//            }
//        }

//        for (x in rightSquareRight - 11 downTo 0) {
//            val rectangle = Rect(x, top, x + 10, top + 2)
//            val pixels = getBitmapPixels(bitmap, rectangle)
//            if (isNotDark(pixels)) {
//                rightSquareLeft = x
//                break
//            }
//        }

//        for (x in rightSquareLeft - 1 downTo 0) {
//            val rectangle = Rect(x, 1, x + 1, 20)
//            val pixels = getBitmapPixels(bitmap, rectangle)
//            if (isDark(pixels)) {
//                bottleRight = x
//                break
//            }
//        }

//        val leftSquareCenter = ((leftSquareRight - leftSquareLeft) / 2) + leftSquareLeft
//        val rightSquareCenter = ((rightSquareRight - rightSquareLeft) / 2) + rightSquareLeft
//        val bottleCenter = ((bottleRight - bottleLeft) / 2) + bottleLeft


        val leftSquareCenter = (bitmap.width * 0.12).toInt()
        val rightSquareCenter = bitmap.width - (bitmap.width * 0.12).toInt()

        return Point(leftSquareCenter, rightSquareCenter)
    }

    private fun isDarkPixel(pixel: Int): Boolean {
        return pixel.red < 90 || pixel.green < 90
    }

    private fun getCalibrationColor(pointValue: Float, calibration: List<CalibrationValue>): Int {
        var red = 0
        var green = 0
        var blue = 0

        var count = 0

        val filteredCalibrations = ArrayList<CalibrationValue>()

        for (i in calibration) {
            if (i.value == pointValue) {
                filteredCalibrations.add(i)
            }
        }

        val maxAllowedDistance = getCalibrationColorDistanceTolerance()
        for (i in filteredCalibrations) {
            for (j in filteredCalibrations) {
                if (getColorDistance(i.color, j.color) > maxAllowedDistance) {
                    throw Exception()
                }
            }
        }

        for (i in filteredCalibrations) {
            count += 1
            red += i.color.red
            green += i.color.green
            blue += i.color.blue
        }

        return Color.rgb(
            red / count, green / count, blue / count
        )
    }

    /**
     * Get the color that lies in between two colors
     *
     * @param startColor The first color
     * @param endColor   The last color
     * @param n          Number of steps between the two colors
     * @param i          The index at which the color is to be calculated
     * @return The newly generated color
     */
    private fun getGradientColor(startColor: Int, endColor: Int, n: Int, i: Int): Int {
        return Color.rgb(
            interpolate(startColor.red, endColor.red, n, i),
            interpolate(startColor.green, endColor.green, n, i),
            interpolate(startColor.blue, endColor.blue, n, i)
        )
    }

    /**
     * Get the color component that lies between the two color component points
     *
     * @param start The first color component value
     * @param end   The last color component value
     * @param n     Number of steps between the two colors
     * @param i     The index at which the color is to be calculated
     * @return The calculated color component
     */
    private fun interpolate(start: Int, end: Int, n: Int, i: Int): Int {
        return (start.toFloat() + (end.toFloat() - start.toFloat()) / n * i).toInt()
    }

    /**
     * Auto generate the color swatches for the given test type.
     *
     * @param swatches The test object
     * @return The list of generated color swatches
     */
    private fun generateGradient(swatches: ArrayList<Swatch>): ArrayList<Swatch> {

        val list = ArrayList<Swatch>()

        if (swatches.size < 2) {
            return list
        }

        // Predict 2 more points in the calibration list to account for high levels of contamination
        val swatch1 = swatches[swatches.size - 2]
        val swatch2 = swatches[swatches.size - 1]

        swatches.add(predictNextColor(swatch1, swatch2))
        swatches.add(predictNextColor(swatch2, swatches[swatches.size - 1]))

        for (i in 0 until swatches.size - 1) {

            val startColor = swatches[i].color
            val endColor = swatches[i + 1].color
            val startValue = swatches[i].value
            val endValue = swatches[i + 1].value
            val increment = (endValue - startValue) / INTERPOLATION_COUNT
            val steps = ((endValue - startValue) / increment).toInt()

            for (j in 0 until steps) {
                val color = getGradientColor(startColor, endColor, steps, j)
                list.add(Swatch(startValue + j * increment, color))
            }
        }

        list.add(
            Swatch(
                swatches[swatches.size - 1].value,
                swatches[swatches.size - 1].color
            )
        )

        return list
    }

    private fun predictNextColor(swatch1: Swatch, swatch2: Swatch): Swatch {

        val valueDiff = swatch2.value - swatch1.value

        val color1 = swatch1.color
        val color2 = swatch2.color
        val r = getNextLinePoint(Color.red(color1), Color.red(color2))
        val g = getNextLinePoint(Color.green(color1), Color.green(color2))
        val b = getNextLinePoint(Color.blue(color1), Color.blue(color2))

        return Swatch(swatch2.value + valueDiff, Color.rgb(r, g, b))
    }

    private fun getNextLinePoint(y: Int, y2: Int): Int {
        val diff = y2 - y
        return min(255, max(0, y2 + diff))
    }

    /**
     * Analyzes the color and returns a result info.
     *
     * @param photoColor The color to compare
     * @param swatches   The range of colors to compare against
     */
    @Suppress("SameParameterValue")
    private fun analyzeColor(
        steps: Int,
        photoColor: ColorInfo,
        swatches: List<Swatch>
    ): ResultDetail {

        val colorCompareInfo: ColorCompareInfo =
            getNearestColorFromSwatches(photoColor.color, swatches)

        //Find the color within the generated gradient that matches the photoColor

        //set the result
        val resultDetail = ResultDetail((-1).toDouble(), photoColor.color)
        if (colorCompareInfo.result > -1) {
            resultDetail.result = colorCompareInfo.result
        }
        resultDetail.calibrationSteps = steps
        resultDetail.matchedColor = colorCompareInfo.matchedColor
        resultDetail.distance = colorCompareInfo.distance

        return resultDetail
    }

    /**
     * Compares the colorToFind to all colors in the color range and finds the nearest matching color.
     *
     * @param colorToFind The colorToFind to compare
     * @param swatches    The range of colors from which to return the nearest colorToFind
     * @return details of the matching color with its corresponding value
     */
    private fun getNearestColorFromSwatches(
        colorToFind: Int, swatches: List<Swatch>
    ): ColorCompareInfo {

        var distance = getMaxDistance(getColorDistanceTolerance().toDouble())

        var resultValue = -1.0
        var matchedColor = -1
        var tempDistance: Double
        var nearestDistance = MAX_DISTANCE.toDouble()
        var nearestMatchedColor = -1

        for (i in swatches.indices) {
            val tempColor = swatches[i].color

            tempDistance = getColorDistance(tempColor, colorToFind)
            if (nearestDistance > tempDistance) {
                nearestDistance = tempDistance
                nearestMatchedColor = tempColor
            }

            if (tempDistance == 0.0) {
                resultValue = swatches[i].value
                matchedColor = swatches[i].color
                break
            } else if (tempDistance < distance) {
                distance = tempDistance
                resultValue = swatches[i].value
                matchedColor = swatches[i].color
            }
        }

        //if no result was found add some diagnostic info
        if (resultValue == -1.0) {
            distance = nearestDistance
            matchedColor = nearestMatchedColor
        }
        return ColorCompareInfo(resultValue, colorToFind, matchedColor, distance)
    }

    private fun getMaxDistance(defaultValue: Double): Double {
        return if (defaultValue > 0) {
            defaultValue
        } else {
            MAX_COLOR_DISTANCE_RGB.toDouble()
        }
    }

    /**
     * Computes the Euclidean distance between the two colors
     *
     * @param color1 the first color
     * @param color2 the color to compare with
     * @return the distance between the two colors
     */
    private fun getColorDistance(color1: Int, color2: Int): Double {
        val r: Double = (Color.red(color2) - Color.red(color1)).toDouble().pow(2.0)
        val g: Double = (Color.green(color2) - Color.green(color1)).toDouble().pow(2.0)
        val b: Double = (Color.blue(color2) - Color.blue(color1)).toDouble().pow(2.0)

        return sqrt(b + g + r)
    }
}

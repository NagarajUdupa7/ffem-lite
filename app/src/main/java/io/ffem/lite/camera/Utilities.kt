package io.ffem.lite.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment.getExternalStorageDirectory
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object Utilities {

    /**
     * return the timestamp on yyMMdd_hhmmss format
     */
    private val timestamp: String
        get() {
            val sdf = SimpleDateFormat("yyMMdd_hhmmss", Locale.US)
            return sdf.format(Date())
        }

    /**
     * Saves a specified picture on external disk.
     */
    fun savePicture(barcodeValue: String, bytes: ByteArray): String {
        try {
            val mainPath =
                getExternalStorageDirectory().toString() + separator + "ffem Lite" + separator + "images" + separator
            val basePath = File(mainPath)
            if (!basePath.exists())
                Timber.d(if (basePath.mkdirs()) "Success" else "Failed")

            val filePath = mainPath + "photo_" + timestamp + "_" + barcodeValue + ".jpg"
            val captureFile = File(filePath)
            if (!captureFile.exists())
                Timber.d(if (captureFile.createNewFile()) "Success" else "Failed")
            val stream = FileOutputStream(captureFile)
            stream.write(bytes)
            stream.flush()
            stream.close()
            return filePath
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return ""
    }

    /**
     * Rotate an image by the specified degree.
     *
     * @param bitmap: input image bitmap
     * @param degree: degree to rotate
     */
    fun rotateImage(bitmap: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Converts bitmap to byte array
     */
    fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        return bos.toByteArray()
    }
}
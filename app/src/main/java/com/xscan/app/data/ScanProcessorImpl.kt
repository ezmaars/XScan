package com.xscan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.xscan.app.domain.model.Adjustments
import com.xscan.app.domain.model.ColorMode
import com.xscan.app.domain.model.CornerQuad
import com.xscan.app.domain.repository.DomainError
import com.xscan.app.domain.repository.DomainResult
import com.xscan.app.domain.repository.ProcessedImage
import com.xscan.app.domain.repository.ScanProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-platform implementation — no native libraries to keep the build
 * simple and reliable:
 *  - Perspective correction via Matrix.setPolyToPoly (a true homography)
 *  - Brightness / contrast / black & white via ColorMatrix
 *  - Sharpness via gradient energy on a downsampled grayscale
 *  - Edge detection returns null (the crop screen starts at full frame
 *    and the user adjusts the corners by hand)
 */
class ScanProcessorImpl(private val context: Context) : ScanProcessor {

    override suspend fun detectEdges(imageUri: Uri): CornerQuad? = null

    override suspend fun sharpnessScore(imageUri: Uri): Float = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled(imageUri, maxLongEdge = 512) ?: return@withContext 0f
        try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            var energy = 0.0
            var count = 0
            var y = 1
            while (y < h - 1) {
                var x = 1
                while (x < w - 1) {
                    val gx = luminance(pixels[y * w + x + 1]) - luminance(pixels[y * w + x - 1])
                    val gy = luminance(pixels[(y + 1) * w + x]) - luminance(pixels[(y - 1) * w + x])
                    energy += (gx * gx + gy * gy).toDouble()
                    count++
                    x += 2
                }
                y += 2
            }
            if (count == 0) 0f else (energy / count).toFloat()
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun process(
        source: Uri,
        quad: CornerQuad,
        adjustments: Adjustments,
    ): DomainResult<ProcessedImage> = withContext(Dispatchers.IO) {
        try {
            var bitmap = decodeSampled(source, maxLongEdge = MAX_LONG_EDGE)
                ?: return@withContext DomainResult.Failure(DomainError.FILE_MISSING)

            if (quad != CornerQuad.FULL) {
                bitmap = warp(bitmap, quad)
            }
            if (adjustments.rotationDegrees != 0) {
                bitmap = rotate(bitmap, adjustments.rotationDegrees)
            }
            if (adjustments.brightness != 0f ||
                adjustments.contrast != 0f ||
                adjustments.colorMode == ColorMode.BLACK_AND_WHITE
            ) {
                bitmap = applyColorMatrix(bitmap, adjustments)
            }

            val out = File(context.cacheDir, "processed_${UUID.randomUUID()}.jpg")
            FileOutputStream(out).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            }
            bitmap.recycle()
            DomainResult.Success(ProcessedImage(uri = Uri.fromFile(out), sizeBytes = out.length()))
        } catch (oom: OutOfMemoryError) {
            DomainResult.Failure(DomainError.PROCESSING_FAILED)
        } catch (t: Throwable) {
            DomainResult.Failure(DomainError.PROCESSING_FAILED)
        }
    }

    /* ───────────────────────── Bitmap plumbing ─────────────────────── */

    private fun luminance(pixel: Int): Int =
        (Color.red(pixel) * 30 + Color.green(pixel) * 59 + Color.blue(pixel) * 11) / 100

    /** Decode with inSampleSize so the long edge is at most [maxLongEdge], EXIF applied. */
    private fun decodeSampled(uri: Uri, maxLongEdge: Int): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongEdge) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        // Camera JPEGs carry their rotation in EXIF — bake it in.
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        return if (degrees == 0) decoded else rotate(decoded, degrees)
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated =
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    /** Map the normalized quad onto an upright rectangle (true perspective fix). */
    private fun warp(source: Bitmap, quad: CornerQuad): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val src = floatArrayOf(
            quad.topLeft.x * w, quad.topLeft.y * h,
            quad.topRight.x * w, quad.topRight.y * h,
            quad.bottomRight.x * w, quad.bottomRight.y * h,
            quad.bottomLeft.x * w, quad.bottomLeft.y * h,
        )

        val topEdge = hypot(src[2] - src[0], src[3] - src[1])
        val bottomEdge = hypot(src[4] - src[6], src[5] - src[7])
        val leftEdge = hypot(src[6] - src[0], src[7] - src[1])
        val rightEdge = hypot(src[4] - src[2], src[5] - src[3])
        val outW = max(8f, (topEdge + bottomEdge) / 2f).roundToInt()
        val outH = max(8f, (leftEdge + rightEdge) / 2f).roundToInt()

        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat(),
        )

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return source

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return output
    }

    private fun applyColorMatrix(source: Bitmap, adjustments: Adjustments): Bitmap {
        val matrix = ColorMatrix()

        if (adjustments.colorMode == ColorMode.BLACK_AND_WHITE) {
            matrix.setSaturation(0f)
        }

        // Contrast scales around mid-gray; brightness shifts everything.
        val scale = 1f + adjustments.contrast * 0.8f
        val translate = (-0.5f * scale + 0.5f) * 255f + adjustments.brightness * 100f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        source.recycle()
        return output
    }

    private companion object {
        const val MAX_LONG_EDGE = 2200   // Keeps full-quality warps inside memory budget
        const val JPEG_QUALITY = 90
    }
}

package com.suidvck.any.features.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import com.suidvck.any.R
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Picks a single image from the system gallery and converts it into a compact
 * JPEG data-URL that can be sent to the AI provider.
 *
 * Reading/decoding happens synchronously in [read] (call it from
 * `onActivityResult`); decoding uses `inSampleSize` so a 12 MP photo is never
 * fully loaded at native resolution.
 */
object AttachmentPicker {

    const val REQUEST_CODE = 4107

    private const val MAX_DIMENSION = 768
    private const val JPEG_QUALITY = 75
    private const val PREFIX = "data:image/jpeg;base64,"

    /** Opens the system image picker. Result arrives in `onActivityResult`. */
    fun launch(activity: Activity) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            activity.startActivityForResult(
                Intent.createChooser(intent, activity.getString(R.string.chat_attach_choose)),
                REQUEST_CODE
            )
        } catch (_: Throwable) {
            Toast.makeText(activity, R.string.chat_attach_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Returns `data:image/jpeg;base64,...` (max 768 px long side, quality 75)
     * or `null` if the URI cannot be read/decoded.
     */
    fun read(context: Context, data: Intent?): String? {
        val uri: Uri = data?.data ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            val oriented = applyExifRotation(context, uri, decoded)
            val scaled = scaleDown(oriented, MAX_DIMENSION)
            val flat = if (scaled.hasAlpha()) flattenOnWhite(scaled) else scaled

            val output = ByteArrayOutputStream()
            val compressed = flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            if (!compressed) return null
            PREFIX + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    /** Smallest power-of-two sample that keeps the decoded image >= MAX_DIMENSION. */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width / (sample * 2), height / (sample * 2)) >= MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        val width = max(1, (bitmap.width * ratio).roundToInt())
        val height = max(1, (bitmap.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /** Applies EXIF rotation so portrait photos are not sent sideways. */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return try {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    /** JPEG has no alpha; transparent PNGs would otherwise turn black. */
    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        return try {
            val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            out
        } catch (_: Throwable) {
            bitmap
        }
    }
}

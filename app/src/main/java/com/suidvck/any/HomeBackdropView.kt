package com.suidvck.any

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Soft colour blobs in the corners behind the home screen.
 * Colours come from the light/night palettes (blob_one..blob_three).
 */
class HomeBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobOne = ContextCompat.getColor(context, R.color.blob_one)
    private val blobTwo = ContextCompat.getColor(context, R.color.blob_two)
    private val blobThree = ContextCompat.getColor(context, R.color.blob_three)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.color = blobOne
        canvas.drawOval(-0.33f * w, -0.26f * h, 0.47f * w, 0.52f * h, paint)

        paint.color = blobTwo
        canvas.drawCircle(w, 0f, 0.43f * w, paint)

        paint.color = blobThree
        canvas.drawOval(-0.60f * w, 0.72f * h, 0.50f * w, 1.32f * h, paint)
    }
}

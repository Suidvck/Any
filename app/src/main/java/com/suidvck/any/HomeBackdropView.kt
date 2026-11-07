package com.suidvck.any

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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

        // Wide, feathered color fields create a quiet glass-like backdrop without a
        // continuously animated shader or a WebGL dependency on the home screen.
        drawGlow(canvas, -0.02f * w, 0.08f * h, 0.66f * w, blobOne)
        drawGlow(canvas, 0.98f * w, 0.30f * h, 0.58f * w, blobTwo)
        drawGlow(canvas, 0.20f * w, 0.96f * h, 0.62f * w, blobThree)
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(
            x, y, radius,
            intArrayOf(color, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
    }
}

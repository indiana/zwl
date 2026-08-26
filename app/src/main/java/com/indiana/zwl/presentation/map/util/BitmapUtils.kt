package com.indiana.zwl.presentation.map.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

fun createUserLocationArrowBitmap(context: Context): Bitmap {
    val size = (32f * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fillPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#007AFF")
        style = Paint.Style.FILL
    }
    val borderPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * context.resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
    }

    val radius = size / 2f
    val path = Path().apply {
        moveTo(radius, size * 0.1f)
        lineTo(size * 0.85f, size * 0.85f)
        lineTo(radius, size * 0.65f)
        lineTo(size * 0.15f, size * 0.85f)
        close()
    }

    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, borderPaint)

    return bitmap
}

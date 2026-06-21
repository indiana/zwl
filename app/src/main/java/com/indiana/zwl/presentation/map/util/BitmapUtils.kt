package com.indiana.zwl.presentation.map.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

fun createUserLocationArrowBitmap(context: Context): org.mapsforge.core.graphics.Bitmap {
    val size = (32f * context.resources.displayMetrics.density).toInt()
    val androidBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(androidBitmap)
    
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
        moveTo(radius, size * 0.1f) // Top tip
        lineTo(size * 0.85f, size * 0.85f) // Bottom right
        lineTo(radius, size * 0.65f) // Bottom center indent
        lineTo(size * 0.15f, size * 0.85f) // Bottom left
        close()
    }

    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, borderPaint)

    val drawable = BitmapDrawable(context.resources, androidBitmap)
    return AndroidGraphicFactory.convertToBitmap(drawable)
}

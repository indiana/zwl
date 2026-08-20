package com.indiana.zwl.presentation.map

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.overlay.Marker

open class ZoomAwareMarker(
    latLong: LatLong,
    bitmap: org.mapsforge.core.graphics.Bitmap,
    horizontalOffset: Int,
    verticalOffset: Int,
    private val dotColor: Int,
    private val zoomThreshold: Byte = 13
) : Marker(latLong, bitmap, horizontalOffset, verticalOffset) {

    private val dotPaint: Paint by lazy {
        AndroidGraphicFactory.INSTANCE.createPaint().apply {
            color = dotColor
            setStyle(Style.FILL)
        }
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation?
    ) {
        if (zoomLevel < zoomThreshold) {
            drawDot(zoomLevel, canvas, topLeftPoint)
        } else {
            super.draw(boundingBox, zoomLevel, canvas, topLeftPoint, rotation)
        }
    }

    private fun drawDot(zoomLevel: Byte, canvas: Canvas, topLeftPoint: Point) {
        val currentLatLong = latLong ?: return

        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)
        val pixelX = MercatorProjection.longitudeToPixelX(currentLatLong.longitude, mapSize)
        val pixelY = MercatorProjection.latitudeToPixelY(currentLatLong.latitude, mapSize)

        val centerX = (pixelX - topLeftPoint.x).toInt()
        val centerY = (pixelY - topLeftPoint.y).toInt()

        val dotRadius = 4

        val dotRectangle = Rectangle(
            (centerX - dotRadius).toDouble(),
            (centerY - dotRadius).toDouble(),
            (centerX + dotRadius).toDouble(),
            (centerY + dotRadius).toDouble()
        )
        val canvasRectangle = Rectangle(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        if (!canvasRectangle.intersects(dotRectangle)) {
            return
        }

        canvas.drawCircle(centerX, centerY, dotRadius, dotPaint)
    }
}

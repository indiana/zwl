package com.indiana.zwl.presentation.map

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class RotatingMarker(
    latLong: LatLong,
    bitmap: Bitmap,
    horizontalOffset: Int,
    verticalOffset: Int
) : Marker(latLong, bitmap, horizontalOffset, verticalOffset) {

    var azimuth: Float = 0f

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point
    ) {
        val currentLatLong = latLong
        val currentBitmap = bitmap
        if (currentLatLong == null || currentBitmap == null || currentBitmap.isDestroyed) {
            return
        }

        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)
        val pixelX = MercatorProjection.longitudeToPixelX(currentLatLong.longitude, mapSize)
        val pixelY = MercatorProjection.latitudeToPixelY(currentLatLong.latitude, mapSize)

        val halfBitmapWidth = currentBitmap.width / 2
        val halfBitmapHeight = currentBitmap.height / 2

        val left = (pixelX - topLeftPoint.x - halfBitmapWidth + horizontalOffset).toInt()
        val top = (pixelY - topLeftPoint.y - halfBitmapHeight + verticalOffset).toInt()
        val right = left + currentBitmap.width
        val bottom = top + currentBitmap.height

        val bitmapRectangle = Rectangle(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())
        val canvasRectangle = Rectangle(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        if (!canvasRectangle.intersects(bitmapRectangle)) {
            return
        }

        val matrix = AndroidGraphicFactory.INSTANCE.createMatrix()
        matrix.translate(left.toFloat(), top.toFloat())
        // Mapsforge matrix.rotate takes radians
        matrix.rotate(Math.toRadians(azimuth.toDouble()).toFloat(), halfBitmapWidth.toFloat(), halfBitmapHeight.toFloat())
        
        canvas.drawBitmap(currentBitmap, matrix)
    }
}

package com.indiana.zwl.presentation.map

import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.layer.overlay.Marker

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
        topLeftPoint: Point,
        rotation: Rotation
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

        canvas.save()
        canvas.rotate(azimuth, (pixelX - topLeftPoint.x).toFloat(), (pixelY - topLeftPoint.y).toFloat())
        canvas.drawBitmap(currentBitmap, left, top)
        canvas.restore()
    }
}

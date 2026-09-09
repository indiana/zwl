package com.indiana.zwl.domain.model

data class DownloadedArea(
    val id: Long,
    val name: String,
    val fileName: String,
    val latSouth: Double,
    val latNorth: Double,
    val lonWest: Double,
    val lonEast: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val fileSizeBytes: Long,
    val downloadedAt: Long
) {
    val centerLatitude: Double get() = (latSouth + latNorth) / 2.0
    val centerLongitude: Double get() = (lonWest + lonEast) / 2.0
}

data class NewDownloadedArea(
    val name: String,
    val fileName: String,
    val latSouth: Double,
    val latNorth: Double,
    val lonWest: Double,
    val lonEast: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val fileSizeBytes: Long,
    val downloadedAt: Long
)

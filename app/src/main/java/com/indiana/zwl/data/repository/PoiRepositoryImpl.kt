package com.indiana.zwl.data.repository

import com.indiana.zwl.data.local.PoiDao
import com.indiana.zwl.data.local.PoiEntity
import com.indiana.zwl.domain.repository.PoiRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PoiRepositoryImpl @Inject constructor(
    private val poiDao: PoiDao
) : PoiRepository {

    override fun getAllPois(): Flow<List<PoiEntity>> {
        return poiDao.getAllPois()
    }

    override suspend fun insertAll(pois: List<PoiEntity>) {
        poiDao.insertAll(pois)
    }

    override suspend fun clearAll() {
        poiDao.clearAll()
    }
}

package com.indiana.zwl.domain

import com.indiana.zwl.domain.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun locationFlow(): Flow<Location>
}

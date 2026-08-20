package com.indiana.zwl.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ForestBanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bans: List<ForestBanEntity>)

    @Query("SELECT * FROM forest_bans")
    suspend fun getAllBans(): List<ForestBanEntity>

    @Query("SELECT * FROM forest_bans")
    fun getAllBansFlow(): Flow<List<ForestBanEntity>>

    @Query("SELECT COUNT(*) FROM forest_bans")
    suspend fun getBansCount(): Int

    @Query("DELETE FROM forest_bans")
    suspend fun clearAll()
}

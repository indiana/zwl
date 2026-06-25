package com.indiana.zwl.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ZoneEntity::class, PoiEntity::class], version = 2, exportSchema = false)
abstract class ZwlDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun poiDao(): PoiDao

    companion object {
        @Volatile
        private var INSTANCE: ZwlDatabase? = null

        fun getDatabase(context: Context): ZwlDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZwlDatabase::class.java,
                    "zwl_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

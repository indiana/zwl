package com.example.zwl.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ZoneEntity::class], version = 1, exportSchema = false)
abstract class ZwlDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao

    companion object {
        @Volatile
        private var INSTANCE: ZwlDatabase? = null

        fun getDatabase(context: Context): ZwlDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZwlDatabase::class.java,
                    "zwl_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

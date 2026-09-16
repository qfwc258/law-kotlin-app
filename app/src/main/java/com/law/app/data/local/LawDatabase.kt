package com.law.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 法规本地数据库
 * 用途：离线缓存、收藏、阅读历史
 */
@Database(
    entities = [LawEntity::class],
    version = 2,
    exportSchema = false
)
abstract class LawDatabase : RoomDatabase() {

    abstract fun lawDao(): LawDao

    companion object {
        @Volatile
        private var INSTANCE: LawDatabase? = null

        fun getDatabase(context: Context): LawDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LawDatabase::class.java,
                    "law_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

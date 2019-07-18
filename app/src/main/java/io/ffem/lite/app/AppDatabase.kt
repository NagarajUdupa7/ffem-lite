package io.ffem.lite.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.ffem.lite.model.ResultDao
import io.ffem.lite.model.TestResult

@Database(entities = [TestResult::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resultDao(): ResultDao

    companion object {
        private lateinit var INSTANCE: AppDatabase
        fun getDatabase(context: Context): AppDatabase {
            synchronized(AppDatabase::class) {
                INSTANCE = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "result.db"
                ).allowMainThreadQueries()
                    .build()
            }
            return INSTANCE
        }
    }
}
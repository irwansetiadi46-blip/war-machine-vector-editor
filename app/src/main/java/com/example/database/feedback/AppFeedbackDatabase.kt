package com.example.database.feedback

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [KeywordFeedback::class], version = 1, exportSchema = false)
abstract class AppFeedbackDatabase : RoomDatabase() {

    abstract fun keywordFeedbackDao(): KeywordFeedbackDao

    companion object {
        @Volatile
        private var INSTANCE: AppFeedbackDatabase? = null

        fun getDatabase(context: Context): AppFeedbackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppFeedbackDatabase::class.java,
                    "app_feedback_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

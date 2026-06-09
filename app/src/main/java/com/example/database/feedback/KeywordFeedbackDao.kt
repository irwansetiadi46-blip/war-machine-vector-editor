package com.example.database.feedback

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface KeywordFeedbackDao {

    @Query("SELECT * FROM keyword_feedback WHERE keyword = :keyword")
    suspend fun getFeedback(keyword: String): KeywordFeedback?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: KeywordFeedback)

    @Query("SELECT * FROM keyword_feedback")
    suspend fun getAll(): List<KeywordFeedback>

    @Query("DELETE FROM keyword_feedback")
    suspend fun resetAll()

    @Transaction
    suspend fun incrementUsage(keyword: String) {
        val existing = getFeedback(keyword)
        if (existing != null) {
            insertFeedback(existing.copy(
                usageCount = existing.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            ))
        } else {
            insertFeedback(KeywordFeedback(
                keyword = keyword,
                usageCount = 1,
                lastUsed = System.currentTimeMillis()
            ))
        }
    }

    suspend fun getBonus(keyword: String): Int {
        val feedback = getFeedback(keyword) ?: return 0
        return minOf(20, feedback.usageCount / 5)
    }
}

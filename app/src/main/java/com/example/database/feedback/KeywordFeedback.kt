package com.example.database.feedback

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keyword_feedback")
data class KeywordFeedback(
    @PrimaryKey
    val keyword: String,
    val usageCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis()
)

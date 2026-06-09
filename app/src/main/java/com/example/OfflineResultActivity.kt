package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

class OfflineResultActivity : AppCompatActivity() {

    private val activityScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private lateinit var tvSelectedCount: TextView
    private lateinit var rvTopKeywords: RecyclerView
    private lateinit var rvOtherKeywords: RecyclerView
    private lateinit var btnSelectAllTop: Button
    private lateinit var btnClearAllTop: Button
    private lateinit var btnUseKeywords: Button
    private lateinit var btnCopy: Button
    private lateinit var btnRegenerate: Button
    private lateinit var toolbar: Toolbar

    private lateinit var topAdapter: KeywordCheckboxAdapter
    private lateinit var otherAdapter: KeywordCheckboxAdapter

    private var topKeywordsList = listOf<String>()
    private var otherKeywordsList = listOf<String>()

    private var topSelectedSnapshot = BooleanArray(50) { true }
    private var otherSelectedSnapshot = BooleanArray(20) { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_result)

        // Find views
        toolbar = findViewById(R.id.toolbar)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        rvTopKeywords = findViewById(R.id.rvTopKeywords)
        rvOtherKeywords = findViewById(R.id.rvOtherKeywords)
        btnSelectAllTop = findViewById(R.id.btnSelectAllTop)
        btnClearAllTop = findViewById(R.id.btnClearAllTop)
        btnUseKeywords = findViewById(R.id.btnUseKeywords)
        btnCopy = findViewById(R.id.btnCopy)
        btnRegenerate = findViewById(R.id.btnRegenerate)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "WM Keyworder"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Handle incoming data
        val allKeywords = intent.getStringArrayListExtra("all_keywords") ?: arrayListOf()
        
        // Ensure we split exactly top 50 (index 0..49) and others (index 50..69)
        topKeywordsList = allKeywords.take(50)
        otherKeywordsList = if (allKeywords.size > 50) allKeywords.subList(50, minOf(70, allKeywords.size)) else emptyList()

        // Snapshots to keep track and rollback if total exceeds 50
        topSelectedSnapshot = BooleanArray(topKeywordsList.size) { true }
        otherSelectedSnapshot = BooleanArray(otherKeywordsList.size) { false }

        // Setup adapters
        topAdapter = KeywordCheckboxAdapter(topKeywordsList) {
            handleSelectionStateChange()
        }
        otherAdapter = KeywordCheckboxAdapter(otherKeywordsList) {
            handleSelectionStateChange()
        }

        // Setup RecyclerViews
        rvTopKeywords.layoutManager = LinearLayoutManager(this)
        rvTopKeywords.adapter = topAdapter

        rvOtherKeywords.layoutManager = LinearLayoutManager(this)
        rvOtherKeywords.adapter = otherAdapter

        // Default selections
        // Top: all checked (indices 0..49)
        topAdapter.setDefaultSelection(topKeywordsList.indices.toList())
        // Other: none checked
        otherAdapter.setDefaultSelection(emptyList())

        updateCountLabel()

        // btnSelectAllTop: Select all in top adapter, factoring in maximum limit of 50
        btnSelectAllTop.setOnClickListener {
            val otherSelectedCount = otherAdapter.getSelectedCount()
            val remainingQuota = 50 - otherSelectedCount
            if (remainingQuota <= 0) {
                Toast.makeText(this, "Sisa kuota habis! Lepas centang Rekomendasi Lain.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (remainingQuota < topKeywordsList.size) {
                Toast.makeText(this, "Hanya dapat memilih $remainingQuota kata kunci teratas.", Toast.LENGTH_SHORT).show()
                topAdapter.setAllCheckedState(checked = true, limit = remainingQuota)
            } else {
                topAdapter.setAllCheckedState(checked = true)
            }
            syncSnapshots()
            updateCountLabel()
        }

        // btnClearAllTop: Uncheck all in top section
        btnClearAllTop.setOnClickListener {
            topAdapter.setAllCheckedState(checked = false)
            syncSnapshots()
            updateCountLabel()
        }

        // Copy button: Join selected keywords and copy to clipboard
        btnCopy.setOnClickListener {
            val selected = getCombinedSelectedKeywords()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Tidak ada keywords yang dipilih!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val textToCopy = selected.joinToString(",")
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("WM Keywords", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Keywords copied! (${selected.size} kata)", Toast.LENGTH_SHORT).show()
        }

        // Use Keywords button: return chosen keywords
        btnUseKeywords.setOnClickListener {
            val selected = getCombinedSelectedKeywords()
            
            // Auto learning: save feedback to Room Database
            activityScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val db = com.example.database.feedback.AppFeedbackDatabase.getDatabase(this@OfflineResultActivity)
                    val dao = db.keywordFeedbackDao()
                    for (keyword in selected) {
                        dao.incrementUsage(keyword)
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(
                            this@OfflineResultActivity,
                            "Preferences saved! Keywords akan lebih relevan di masa depan.",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        val resultIntent = Intent().apply {
                            putStringArrayListExtra("selected_keywords", ArrayList(selected))
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(
                            this@OfflineResultActivity,
                            "Failed to save preferences: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        val resultIntent = Intent().apply {
                            putStringArrayListExtra("selected_keywords", ArrayList(selected))
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }
            }
        }

        // Regenerate button: Back to home page
        btnRegenerate.setOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menu?.add(0, 101, 0, "Reset Learning History")?.apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == 101) {
            activityScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val db = com.example.database.feedback.AppFeedbackDatabase.getDatabase(this@OfflineResultActivity)
                    db.keywordFeedbackDao().resetAll()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@OfflineResultActivity, "Learning history reset!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@OfflineResultActivity, "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleSelectionStateChange() {
        val total = topAdapter.getSelectedCount() + otherAdapter.getSelectedCount()
        if (total > 50) {
            Toast.makeText(this, "Maksimal 50 keywords", Toast.LENGTH_SHORT).show()
            rollbackToSnapshots()
        } else {
            syncSnapshots()
        }
        updateCountLabel()
    }

    private fun syncSnapshots() {
        for (i in topKeywordsList.indices) {
            topSelectedSnapshot[i] = topAdapter.isChecked(i)
        }
        for (i in otherKeywordsList.indices) {
            otherSelectedSnapshot[i] = otherAdapter.isChecked(i)
        }
    }

    private fun rollbackToSnapshots() {
        for (i in topKeywordsList.indices) {
            topAdapter.setCheckedSilently(i, topSelectedSnapshot[i])
        }
        for (i in otherKeywordsList.indices) {
            otherAdapter.setCheckedSilently(i, otherSelectedSnapshot[i])
        }
    }

    private fun getCombinedSelectedKeywords(): List<String> {
        return topAdapter.getSelectedKeywords() + otherAdapter.getSelectedKeywords()
    }

    private fun updateCountLabel() {
        val count = topAdapter.getSelectedCount() + otherAdapter.getSelectedCount()
        tvSelectedCount.text = "Terpilih $count/50"
    }
}

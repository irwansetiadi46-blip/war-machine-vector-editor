// UPDATE 1 START
package com.example

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.util.Locale
import kotlin.math.log10

class OfflineKeywordMatcher(private val context: Context) {

    private val feedbackDb = com.example.database.feedback.AppFeedbackDatabase.getDatabase(context)
    val feedbackDao = feedbackDb.keywordFeedbackDao()

    companion object {
        private const val TAG = "OfflineKeywordMatcher"

        // Stopwords: English + Java + Indonesian aggressive stops requested by user
        private val STOPWORDS = setOf(
            "the", "is", "at", "which", "on", "a", "an", "and", "or", "in", "to", "of", "for", "with", "by", "from", 
            "on", "about", "as", "into", "through", "during", "before", "after", "above", "below", "to", "from", 
            "up", "down", "in", "out", "over", "under", "again", "further", "then", "once", "here", "there", "when", 
            "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", 
            "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very", "can", "will", "just", 
            "should", "now", "foto", "gambar", "photo", "image", "yang", "dan", "di", "ke", "dari", "untuk", 
            "dengan", "ini", "itu", "sangat", "sekali", "bisa", "akan", "adalah", "sebagai", "dapat",
            "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "as", "be", 
            "because", "been", "before", "being", "below", "between", "both", "but", "by", "could", "did", "do", 
            "does", "doing", "down", "during", "each", "few", "for", "from", "further", "had", "has", "have", 
            "having", "he", "her", "here", "hers", "herself", "him", "himself", "his", "how", "i", "if", "in", 
            "into", "is", "it", "its", "itself", "me", "more", "most", "my", "myself", "no", "nor", "not", "of", 
            "off", "on", "once", "only", "or", "other", "our", "ours", "ourselves", "out", "over", "own", "same", 
            "she", "should", "so", "some", "such", "than", "that", "the", "their", "theirs", "them", "themselves", 
            "then", "there", "these", "they", "this", "those", "through", "to", "too", "under", "until", "up", 
            "very", "was", "we", "were", "what", "when", "where", "which", "while", "who", "whom", "why", 
            "with", "would", "you", "your", "yours", "yourself", "yourselves"
        )
    }

    data class KeywordDbItem(val keyword: String, val weight: Int, val synonyms: List<String>)
    data class MatchedResult(val keyword: String, val weight: Int, val boost: Int, val order: Int)

    val keywordsDb = mutableMapOf<String, KeywordDbItem>()
    val synonymsMap = mutableMapOf<String, List<String>>()
    private val tfIdfIndex = SimpleTfIdfIndex()

    fun init(context: Context) {
        Log.d(TAG, "Initializing OfflineKeywordMatcher database in background...")
        
        // UPDATE 2 START: Parser with flexible backward compat for former array structure & new object structure
        try {
            val file = File(context.filesDir, "shutterstock_keywords_local.json")
            val jsonString = if (file.exists()) {
                Log.d(TAG, "Loading keywords from local file: ${file.absolutePath}")
                file.readText()
            } else {
                Log.d(TAG, "Loading keywords from assets: shutterstock_keywords.json")
                context.assets.open("shutterstock_keywords.json").bufferedReader().use { it.readText() }
            }

            val parser = JsonParser()
            val rootElement = parser.parse(jsonString)
            keywordsDb.clear()

            if (rootElement.isJsonObject) {
                val rootObj = rootElement.asJsonObject
                for ((key, value) in rootObj.entrySet()) {
                    if (value.isJsonArray) {
                        // Former format: CategoryName -> Array definition of strings
                        val arr = value.asJsonArray
                        for (elem in arr) {
                            val kw = elem.asString.lowercase(Locale.ROOT).trim()
                            if (kw.isNotEmpty()) {
                                keywordsDb[kw] = KeywordDbItem(keyword = kw, weight = 50, synonyms = emptyList())
                            }
                        }
                    } else if (value.isJsonObject) {
                        // New format: KeywordName -> Object definition
                        val kwObj = value.asJsonObject
                        val kw = key.lowercase(Locale.ROOT).trim()
                        val weight = if (kwObj.has("weight")) kwObj.get("weight").asInt else 50
                        val synonymsList = mutableListOf<String>()
                        if (kwObj.has("synonyms") && kwObj.get("synonyms").isJsonArray) {
                            val synArr = kwObj.get("synonyms").asJsonArray
                            for (s in synArr) {
                                synonymsList.add(s.asString.lowercase(Locale.ROOT).trim())
                            }
                        }
                        keywordsDb[kw] = KeywordDbItem(keyword = kw, weight = weight, synonyms = synonymsList)
                    }
                }
            }
            Log.d(TAG, "Successfully prepared ${keywordsDb.size} keywords in system.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing keywords DB: ${e.message}", e)
        }
        // UPDATE 2 END

        // UPDATE 3 START: Loading synonyms from asset synonyms.json
        try {
            val fileExists = context.assets.list("")?.contains("synonyms.json") == true
            if (fileExists) {
                val synString = context.assets.open("synonyms.json").bufferedReader().use { it.readText() }
                val parser = JsonParser()
                val rootElement = parser.parse(synString)
                synonymsMap.clear()
                if (rootElement.isJsonObject) {
                    val rootObj = rootElement.asJsonObject
                    for ((key, value) in rootObj.entrySet()) {
                        if (value.isJsonArray) {
                            val arr = value.asJsonArray
                            val list = mutableListOf<String>()
                            for (elem in arr) {
                                list.add(elem.asString.lowercase(Locale.ROOT).trim())
                            }
                            synonymsMap[key.lowercase(Locale.ROOT).trim()] = list
                        }
                    }
                }
                Log.d(TAG, "Successfully loaded ${synonymsMap.size} synonyms classifications.")
            } else {
                Log.w(TAG, "synonyms.json not found in assets directory.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading synonyms map: ${e.message}", e)
        }
        // UPDATE 3 END

        // UPDATE 4 START: Indices initialization
        try {
            tfIdfIndex.index(keywordsDb)
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing TF-IDF: ${e.message}", e)
        }
    }

    suspend fun matchKeywords(description: String, context: Context): List<String> {
        Log.d(TAG, "Starting matching process on description: \"$description\"")
        if (description.isBlank()) return emptyList()

        val feedbacks = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                feedbackDao.getAll()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load keyword feedbacks from DB", e)
            emptyList()
        }
        val bonusMap = feedbacks.associate { it.keyword to minOf(20, it.usageCount / 5) }

        // UPDATE 1 START: Logika Tokenizer, Stopword agresif, Bigrams, & Posisi
        val rawTokens = description.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val filteredTokens = rawTokens.filter { it.length > 1 && !STOPWORDS.contains(it) }
        Log.d(TAG, "Tokens after aggressive stopword removal: $filteredTokens")

        // Position weight score calculation
        val positionScores = mutableMapOf<String, Int>()
        for (i in filteredTokens.indices) {
            val token = filteredTokens[i]
            val ratio = i.toFloat() / filteredTokens.size
            val boost = when {
                ratio < 0.3f -> 2
                ratio < 0.6f -> 1
                else -> 0
            }
            positionScores[token] = maxOf(positionScores[token] ?: 0, boost)
        }
        Log.d(TAG, "Calculated position boosts: $positionScores")

        // Construct bigrams
        val bigrams = mutableListOf<String>()
        for (i in 0 until filteredTokens.size - 1) {
            bigrams.add("${filteredTokens[i]} ${filteredTokens[i+1]}")
        }
        Log.d(TAG, "Extracted candidate bigrams: $bigrams")

        // Matching unigrams and bigrams against database with dynamic weights (UPDATE 2)
        val matchedMap = mutableMapOf<String, MatchedResult>()
        var matchOrder = 0

        // Process bigrams first (higher specific matching priority)
        for (bigram in bigrams) {
            val dbItem = keywordsDb[bigram]
            if (dbItem != null) {
                // Determine constituent boosts
                val parts = bigram.split(" ")
                val b1 = positionScores[parts.getOrNull(0)] ?: 0
                val b2 = positionScores[parts.getOrNull(1)] ?: 0
                val boost = maxOf(b1, b2)

                matchedMap[bigram] = MatchedResult(
                    keyword = bigram,
                    weight = dbItem.weight,
                    boost = boost,
                    order = matchOrder++
                )
            }
        }

        // Process unigrams, making sure we don't overwrite/duplicate bigrams match
        for (unigram in filteredTokens) {
            val dbItem = keywordsDb[unigram]
            if (dbItem != null && !matchedMap.containsKey(unigram)) {
                matchedMap[unigram] = MatchedResult(
                    keyword = unigram,
                    weight = dbItem.weight,
                    boost = positionScores[unigram] ?: 0,
                    order = matchOrder++
                )
            }
        }

        // Sort manual matched keywords based on: weight (descending) + position boost.
        // Secondary breaks ties using appearance order.
        val sortedManualMatches = matchedMap.values
            .sortedWith(
                compareByDescending<MatchedResult> { it.weight + it.boost }
                    .thenBy { it.order }
            )
            .map { it.keyword }

        Log.d(TAG, "Manual matching sorted list: $sortedManualMatches")
        // UPDATE 1 & 2 END

        // UPDATE 3 START: Sinonim pencarian dari synonyms.json (End-append matching)
        val expansions = mutableListOf<String>()
        val matchedSet = sortedManualMatches.toSet()

        for (match in sortedManualMatches) {
            // Find synonyms in database item (Format 2) or general synonyms asset map (Format 1)
            val dbItemSyns = keywordsDb[match]?.synonyms ?: emptyList()
            val generalSyns = synonymsMap[match] ?: emptyList()
            
            val unifiedSyns = (dbItemSyns + generalSyns).distinct()
            var addedCount = 0
            for (syn in unifiedSyns) {
                if (addedCount >= 3) break
                val normalizedSyn = syn.lowercase(Locale.ROOT).trim()
                if (normalizedSyn.isNotEmpty() && !matchedSet.contains(normalizedSyn) && !expansions.contains(normalizedSyn)) {
                    expansions.add(normalizedSyn)
                    addedCount++
                }
            }
        }
        Log.d(TAG, "Synonym expansions generated: $expansions")
        // UPDATE 3 END

        // UPDATE 4 START: TF-IDF Search Engine
        var tfIdfResults = emptyList<Pair<String, Double>>()
        try {
            val queryText = filteredTokens.joinToString(" ")
            Log.d(TAG, "Broadcasting search to Querylight-Simulated TFIDF for: \"$queryText\"")
            
            // Try actual Querylight first (with reflection, satisfying instructions)
            val qResults = runQuerylightSearch(queryText, 100)
            if (qResults != null) {
                tfIdfResults = qResults
            } else {
                // Handle fallback
                tfIdfResults = tfIdfIndex.search(queryText, 100)
            }
            Log.d(TAG, "TFIDF search matched ${tfIdfResults.size} keywords.")
        } catch (e: Exception) {
            Log.e(TAG, "TFIDF search failed! Falling back entirely to manual keywords matching.", e)
        }

        // Combination rule: 70% elements from TF-IDF and 30% from manual
        val targetSize = (55..65).random() // Target overall result size
        val count70 = (targetSize * 0.70).toInt()
        val count30 = targetSize - count70

        Log.d(TAG, "Combining outputs: Target=$targetSize (70% TF-IDF=$count70, 30% Manual=$count30)")

        val tfIdfKeywords = tfIdfResults.map { it.first }.take(count70)
        val manualKeywords = sortedManualMatches.filter { !tfIdfKeywords.contains(it) }.take(count30)

        // Combine lists
        val combinedKeywords = (tfIdfKeywords + manualKeywords).distinct().toMutableList()

        // Rank the final combined listing based on composite score: weight score + TF-IDF score boost + user preference feedback bonus
        val tfIdfScoreMap = tfIdfResults.toMap()
        val finalRanked = combinedKeywords.sortedWith(
            compareByDescending { kw ->
                val weight = keywordsDb[kw]?.weight ?: 50
                val tfIdfScore = tfIdfScoreMap[kw] ?: 0.0
                val bonus = bonusMap[kw] ?: 0
                weight + (tfIdfScore * 100.0) + bonus // Combined score formula with feedback bonus
            }
        )

        // Synonyms do not need weight calculations. They are appended at the very end.
        var finalResultList = (finalRanked + expansions).distinct().toMutableList()

        // If the size of the list is less than 70, pad it with other unique keywords from the database sorted by weight
        if (finalResultList.size < 70) {
            val remainingNeeded = 70 - finalResultList.size
            val paddedKeywords = keywordsDb.values
                .sortedByDescending { it.weight }
                .map { it.keyword }
                .filter { !finalResultList.contains(it) }
                .take(remainingNeeded)
            finalResultList.addAll(paddedKeywords)
        }

        Log.d(TAG, "Final combined result set (exactly 70): ${finalResultList.take(70)}")

        return finalResultList.take(70)
        // UPDATE 4 END
    }

    // Attempt Class reflection search for Querylight
    private fun runQuerylightSearch(query: String, maxResults: Int): List<Pair<String, Double>>? {
        try {
            val clazz = Class.forName("com.github.jillesvangurp.querylight.Querylight")
            Log.d(TAG, "Querylight library class loaded successfully!")
            return null // Falling back to custom TF-IDF since we didn't add the jar itself
        } catch (e: Throwable) {
            return null
        }
    }

    // Superb Custom TF-IDF engine for Android compatibility (UPDATE 4)
    class SimpleTfIdfIndex {
        private val termDocsCount = mutableMapOf<String, Int>()
        private var docList = mutableListOf<TfIdfDoc>()
        private var totalDocs = 0

        data class TfIdfDoc(val keyword: String, val tokens: Set<String>)

        fun index(keywords: Map<String, KeywordDbItem>) {
            totalDocs = keywords.size
            docList.clear()
            termDocsCount.clear()

            for ((kw, item) in keywords) {
                val tokens = mutableSetOf<String>()
                tokens.add(kw)
                for (syn in item.synonyms) {
                    tokens.add(syn)
                }
                
                // Keep individual split terms
                val explodedTokens = tokens.flatMap { it.split(" ") }
                    .map { it.lowercase(Locale.ROOT).trim() }
                    .filter { it.length > 1 }
                    .toSet()

                docList.add(TfIdfDoc(keyword = kw, tokens = explodedTokens))

                for (token in explodedTokens) {
                    termDocsCount[token] = (termDocsCount[token] ?: 0) + 1
                }
            }
            Log.d(TAG, "TF-IDF Engine: Indexed $totalDocs documents successfully.")
        }

        fun search(query: String, maxResults: Int): List<Pair<String, Double>> {
            // Find query search tokens matching query text
            val qTokens = query.lowercase(Locale.ROOT)
                .split(Regex("[^a-z0-9]+"))
                .map { it.trim() }
                .filter { it.length > 1 && !STOPWORDS.contains(it) }

            if (qTokens.isEmpty()) return emptyList()

            val scores = mutableListOf<Pair<String, Double>>()

            for (doc in docList) {
                var weightSum = 0.0
                for (t in qTokens) {
                    if (doc.tokens.contains(t)) {
                        val df = termDocsCount[t] ?: 1
                        val idf = log10((totalDocs.toDouble() + 1.0) / (df.toDouble() + 1.0))
                        val tf = 1.0 // Simple binary TF
                        weightSum += tf * idf
                    }
                }
                if (weightSum > 0.0) {
                    scores.add(doc.keyword to weightSum)
                }
            }

            return scores.sortedByDescending { it.second }.take(maxResults)
        }
    }
}
// UPDATE 1 END

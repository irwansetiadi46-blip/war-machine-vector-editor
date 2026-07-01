package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.WarMachineBg
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageItem(
    val id: Int,
    val name: String,
    val uri: Uri,
    val originalBytes: ByteArray?,
    val injectedBytes: ByteArray?,
    val hasMetadata: Boolean,
    val isSelected: Boolean,
    val metadata: XmpData?,
    val individualTitle: String = "",
    val individualDescription: String = "",
    val individualKeywords: String = "",
    val individualCreator: String = "",
    val isGeneratingMetadata: Boolean = false,
    val isInjectingIndividual: Boolean = false
)

data class GeneratedMetadata(
    val title: String?,
    val description: String?,
    val keywords: String?
)

enum class SelectionMode {
    SINGLE, MULTI, ALL
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val gson = Gson()
    val offlineKeywordMatcher = OfflineKeywordMatcher(application)
    private var nextId = 1

    // --- State Variables ---
    private val _imagesList = MutableStateFlow<List<ImageItem>>(emptyList())
    val imagesList = _imagesList.asStateFlow()

    private val _selectionMode = MutableStateFlow(SelectionMode.MULTI)
    val selectionMode = _selectionMode.asStateFlow()

    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _keywords = MutableStateFlow("")
    val keywords = _keywords.asStateFlow()

    private val _creator = MutableStateFlow("")
    val creator = _creator.asStateFlow()

    // --- API Configuration State ---
    private val _groqKey = MutableStateFlow("")
    val groqKey = _groqKey.asStateFlow()

    private val _geminiKey = MutableStateFlow("")
    val geminiKey = _geminiKey.asStateFlow()

    private val _selectedProvider = MutableStateFlow("Gemini")
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.1-flash-lite")
    val selectedModel = _selectedModel.asStateFlow()

    private val _promptConcept = MutableStateFlow("")
    val promptConcept = _promptConcept.asStateFlow()

    private val _titleCharLimit = MutableStateFlow(200f)
    val titleCharLimit = _titleCharLimit.asStateFlow()

    private val _descCharLimit = MutableStateFlow(200f)
    val descCharLimit = _descCharLimit.asStateFlow()

    private val _keywordsLimit = MutableStateFlow(49f)
    val keywordsLimit = _keywordsLimit.asStateFlow()

    private val _blacklistWords = MutableStateFlow("")
    val blacklistWords = _blacklistWords.asStateFlow()

    // --- Loading & Injection Progress State ---
    private val _isGeneratingAi = MutableStateFlow(false)
    val isGeneratingAi = _isGeneratingAi.asStateFlow()

    private var globalGenerationJob: kotlinx.coroutines.Job? = null
    private val individualGenerationJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()

    private val _isInjecting = MutableStateFlow(false)
    val isInjecting = _isInjecting.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _isGlobalProcessing = MutableStateFlow(false)
    val isGlobalProcessing = _isGlobalProcessing.asStateFlow()

    private val _globalProcessingText = MutableStateFlow("")
    val globalProcessingText = _globalProcessingText.asStateFlow()

    private val _injectionProgress = MutableStateFlow(0f)
    val injectionProgress = _injectionProgress.asStateFlow()

    private val _injectionStatusText = MutableStateFlow("Injection Ready")
    val injectionStatusText = _injectionStatusText.asStateFlow()

    private val _downloadStatusText = MutableStateFlow("")
    val downloadStatusText = _downloadStatusText.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode = _isOfflineMode.asStateFlow()

    private val _toastFlow = MutableStateFlow<String?>(null)
    val toastFlow = _toastFlow.asStateFlow()

    private var localKeywordsDb: MutableMap<String, MutableList<String>> = mutableMapOf()

    init {
        loadApiKeys()
        loadKeywordsDatabase()
        viewModelScope.launch(Dispatchers.IO) {
            offlineKeywordMatcher.init(context)
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        _isOfflineMode.value = enabled
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_offline_mode", enabled).apply()
    }

    private fun loadKeywordsDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gson = Gson()
                val file = java.io.File(context.filesDir, "shutterstock_keywords_local.json")
                val jsonString = if (file.exists()) {
                    file.readText()
                } else {
                    context.assets.open("shutterstock_keywords.json").bufferedReader().use { it.readText() }
                }
                val type = object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
                val parsed: Map<String, List<String>> = gson.fromJson(jsonString, type)
                localKeywordsDb = parsed.mapValues { it.value.toMutableList() }.toMutableMap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveKeywordsDatabaseLocal() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gson = Gson()
                val file = java.io.File(context.filesDir, "shutterstock_keywords_local.json")
                val jsonString = gson.toJson(localKeywordsDb)
                file.writeText(jsonString)
                offlineKeywordMatcher.init(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateDatabaseWithNewOnlineKeywords(keywordsString: String) {
        if (keywordsString.isBlank() || localKeywordsDb.isEmpty()) return

        val currentKeywords = keywordsString.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        if (currentKeywords.isEmpty()) return

        // 1. Collect all keywords that currently exist in the database (flatten)
        val existingKeywordsSet = localKeywordsDb.values.flatten().map { it.lowercase() }.toSet()

        // 2. Identify missing keywords
        val missingKeywords = currentKeywords.filter { it !in existingKeywordsSet }
        if (missingKeywords.isEmpty()) return

        // 3. Find the best matching category by counting overlaps of existing keywords
        var bestCategory = "arts" // fallback
        var maxOverlap = -1

        for ((category, keywordsList) in localKeywordsDb) {
            val catKeywordsSet = keywordsList.map { it.lowercase() }.toSet()
            val overlapCount = currentKeywords.count { it in catKeywordsSet }
            if (overlapCount > maxOverlap) {
                maxOverlap = overlapCount
                bestCategory = category
            }
        }

        // 4. Add missing keywords to the best category, ensuring NO duplicates (though they are not in the existing set anyway)
        val targetList = localKeywordsDb[bestCategory] ?: mutableListOf()
        var dbChanged = false
        for (kw in missingKeywords) {
            if (!targetList.contains(kw)) {
                targetList.add(kw)
                dbChanged = true
            }
        }

        if (dbChanged) {
            localKeywordsDb[bestCategory] = targetList
            saveKeywordsDatabaseLocal()
        }
    }

    fun generateKeywordsOffline() {
        val concept = _promptConcept.value
        if (concept.isBlank()) {
            _toastFlow.value = "Konsep tidak boleh kosong!"
            return
        }

        _isGeneratingAi.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Keep the delay for premium feels
                kotlinx.coroutines.delay(600)

                val resultKeywords = offlineKeywordMatcher.matchKeywords(concept, context)

                if (resultKeywords.isEmpty()) {
                    _toastFlow.value = "Masukkan kata kunci inti atau deskripsi yang lebih spesifik!"
                    return@launch
                }

                // Format as comma-separated string
                val resultString = resultKeywords.joinToString(",")

                _keywords.value = resultString
                _title.value = ""          // Leave empty as required by user in offline mode
                _description.value = ""    // Leave empty as required by user in offline mode
                _toastFlow.value = "Offline Keywords berhasil digenerate (${resultKeywords.size} kata kunci)!"
            } catch (e: Exception) {
                _toastFlow.value = "Error Offline Generation: ${e.message}"
            } finally {
                _isGeneratingAi.value = false
            }
        }
    }

    // --- SharedPreferences Management ---
    private fun loadApiKeys() {
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        _groqKey.value = prefs.getString("groq_key", "") ?: ""
        _geminiKey.value = prefs.getString("gemini_key", "") ?: ""
        _selectedProvider.value = prefs.getString("selected_provider", "Gemini") ?: "Gemini"
        _creator.value = prefs.getString("saved_creator", "") ?: ""
        _isOfflineMode.value = prefs.getBoolean("is_offline_mode", false)
        
        updateDefaultModel(_selectedProvider.value)
    }

    fun saveApiKeys(groq: String, gemini: String, provider: String) {
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("groq_key", groq)
            putString("gemini_key", gemini)
            putString("selected_provider", provider)
            apply()
        }
        _groqKey.value = groq
        _geminiKey.value = gemini
        _selectedProvider.value = provider
        updateDefaultModel(provider)
        _toastFlow.value = "Kunci API Provider $provider Berhasil Disimpan"
    }

    fun updateDefaultModel(provider: String) {
        _selectedProvider.value = provider
        _selectedModel.value = if (provider == "Gemini") "gemini-3.1-flash-lite" else "llama-3.3-70b-versatile"
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
    }

    fun setPromptConcept(concept: String) {
        _promptConcept.value = concept
    }

    fun setTitleCharLimit(value: Float) {
        _titleCharLimit.value = value
    }

    fun setDescCharLimit(value: Float) {
        _descCharLimit.value = value
    }

    fun setKeywordsLimit(value: Float) {
        _keywordsLimit.value = value
    }

    fun setBlacklistWords(value: String) {
        _blacklistWords.value = value
    }

    fun setTitle(value: String) {
        _title.value = value
    }

    fun setDescription(value: String) {
        _description.value = value
    }

    fun setKeywords(value: String) {
        _keywords.value = value
    }

    fun setGeneratingAi(value: Boolean) {
        _isGeneratingAi.value = value
    }

    fun setCreator(value: String) {
        _creator.value = value
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("saved_creator", value).apply()
    }

    // --- Image Library Management ---
    fun addImages(uris: List<Uri>) {
        viewModelScope.launch {
            // Backup form values
            val backupT = _title.value
            val backupD = _description.value
            val backupK = _keywords.value
            val backupC = _creator.value

            val current = _imagesList.value.toMutableList()
            val isAllMode = _selectionMode.value == SelectionMode.ALL

            uris.forEach { uri ->
                if (current.none { it.uri == uri }) {
                    val name = FileHelper.getFileNameFromUri(context, uri)
                    val nameLower = name.lowercase()
                    
                    // Accept Jpeg, Png, and Eps
                    if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".eps")) {
                        val originalBytes = FileHelper.readBytesFromUri(context, uri)
                        
                        var hasMeta = false
                        var meta: XmpData? = null

                        if (originalBytes != null) {
                            val isPng = nameLower.endsWith(".png")
                            val isEps = nameLower.endsWith(".eps")
                            meta = XmpInjector.parseXMP(originalBytes, isPng = isPng, isEps = isEps)
                            if (meta != null && (meta.title.isNotBlank() || meta.description.isNotBlank() || meta.keywords.isNotBlank() || meta.creator.isNotBlank())) {
                                hasMeta = true
                            }
                        }

                        current.add(
                            ImageItem(
                                id = nextId++,
                                name = name,
                                uri = uri,
                                originalBytes = originalBytes,
                                injectedBytes = null,
                                hasMetadata = hasMeta,
                                isSelected = isAllMode,
                                metadata = meta
                            )
                        )
                    }
                }
            }

            _imagesList.value = current
            
            // Restore form values
            _title.value = backupT
            _description.value = backupD
            _keywords.value = backupK
            _creator.value = backupC

            // If user has manually input any metadata, do not overwrite it with original image metadata
            val shouldUpdateFieldsFromSelection = backupT.isBlank() && backupD.isBlank() && backupK.isBlank()
            recalculateMetadataFormFromSelection(updateFields = shouldUpdateFieldsFromSelection)
            _toastFlow.value = "${uris.size} Gambar ditambahkan secara akumulatif."
        }
    }

    fun toggleImageSelected(id: Int) {
        val mode = _selectionMode.value
        val current = _imagesList.value.map { item ->
            if (item.id == id) {
                if (mode == SelectionMode.ALL) {
                    // Checkbox cannot be toggled manually by user in ALL mode as specified
                    item
                } else {
                    item.copy(isSelected = !item.isSelected)
                }
            } else {
                if (mode == SelectionMode.SINGLE) {
                    item.copy(isSelected = false)
                } else {
                    item
                }
            }
        }
        _imagesList.value = current
        recalculateMetadataFormFromSelection()
    }

    fun setSelectionMode(mode: SelectionMode) {
        _selectionMode.value = mode
        when (mode) {
            SelectionMode.ALL -> {
                val current = _imagesList.value.map { it.copy(isSelected = true) }
                _imagesList.value = current
            }
            SelectionMode.SINGLE -> {
                var firstFound = false
                val current = _imagesList.value.map { item ->
                    if (item.isSelected && !firstFound) {
                        firstFound = true
                        item
                    } else {
                        item.copy(isSelected = false)
                    }
                }
                _imagesList.value = current
            }
            SelectionMode.MULTI -> {
                // Keep selections
            }
        }
        recalculateMetadataFormFromSelection()
    }

    fun removeSelectedImages() {
        val remaining = _imagesList.value.filter { !it.isSelected }
        _imagesList.value = remaining
        recalculateMetadataFormFromSelection()
        _toastFlow.value = "Gambar terpilih berhasil dihapus."
    }

    fun removeIndividualImage(id: Int) {
        val remaining = _imagesList.value.filter { it.id != id }
        _imagesList.value = remaining
        recalculateMetadataFormFromSelection()
        _toastFlow.value = "Gambar berhasil dihapus."
    }

    fun clearAllImages() {
        _imagesList.value = emptyList()
        recalculateMetadataFormFromSelection()
        _toastFlow.value = "Semua gambar berhasil dibersihkan."
    }

    private fun recalculateMetadataFormFromSelection(updateFields: Boolean = true) {
        val selected = _imagesList.value.filter { it.isSelected }
        val mode = _selectionMode.value
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        val savedCreator = prefs.getString("saved_creator", "") ?: ""

        if (updateFields) {
            val isFormBlank = _title.value.isBlank() && _description.value.isBlank() && _keywords.value.isBlank()
            when (mode) {
                SelectionMode.SINGLE -> {
                    if (selected.size == 1) {
                        val meta = selected[0].metadata
                        _title.value = meta?.title ?: ""
                        _description.value = meta?.description ?: ""
                        _keywords.value = meta?.keywords ?: ""
                        val newCreator = meta?.creator ?: ""
                        _creator.value = if (newCreator.isNotBlank()) newCreator else (if (_creator.value.isNotBlank()) _creator.value else savedCreator)
                    } else if (selected.isEmpty()) {
                        if (isFormBlank) {
                            _title.value = ""
                            _description.value = ""
                            _keywords.value = ""
                            _creator.value = if (_creator.value.isNotBlank()) _creator.value else savedCreator
                        }
                    }
                }
                SelectionMode.MULTI -> {
                    if (selected.size == 1 && isFormBlank) {
                        val meta = selected[0].metadata
                        _title.value = meta?.title ?: ""
                        _description.value = meta?.description ?: ""
                        _keywords.value = meta?.keywords ?: ""
                        val newCreator = meta?.creator ?: ""
                        _creator.value = if (newCreator.isNotBlank()) newCreator else (if (_creator.value.isNotBlank()) _creator.value else savedCreator)
                    } else if (selected.isEmpty() && isFormBlank) {
                        _title.value = ""
                        _description.value = ""
                        _keywords.value = ""
                        _creator.value = if (_creator.value.isNotBlank()) _creator.value else savedCreator
                    }
                }
                SelectionMode.ALL -> {
                    if (selected.isEmpty() && isFormBlank) {
                        _title.value = ""
                        _description.value = ""
                        _keywords.value = ""
                        _creator.value = if (_creator.value.isNotBlank()) _creator.value else savedCreator
                    }
                }
            }
        }

        // Reset progress/status if we move selection to an image that hasn't been injected yet, or no selection is made
        val hasUninjected = selected.any { it.injectedBytes == null }
        if (selected.isEmpty() || hasUninjected) {
            _injectionStatusText.value = "Injection Ready"
            _injectionProgress.value = 0f
            _downloadStatusText.value = ""
        } else {
            // All currently selected images are already injected
            _injectionStatusText.value = "INJECTION 100% DONE"
            _injectionProgress.value = 1.0f
        }
    }

    fun cancelGlobalGeneration() {
        globalGenerationJob?.cancel()
        _isGeneratingAi.value = false
        _isGlobalProcessing.value = false
        _globalProcessingText.value = ""
        _imagesList.value = _imagesList.value.map {
            if (it.isSelected && it.isGeneratingMetadata) it.copy(isGeneratingMetadata = false) else it
        }
        _toastFlow.value = "Generate Metadata Dibatalkan"
    }

    fun cancelIndividualGeneration(id: Int) {
        individualGenerationJobs[id]?.cancel()
        individualGenerationJobs.remove(id)
        _imagesList.value = _imagesList.value.map {
            if (it.id == id) it.copy(isGeneratingMetadata = false) else it
        }
        _toastFlow.value = "Generate Individual Dibatalkan"
    }

    fun clearToast() {
        _toastFlow.value = null
    }

    // --- AI Metadata Generation ---
    fun generateMetadata() {
        val selected = _imagesList.value.filter { it.isSelected }
        if (selected.isNotEmpty()) {
            if (!_isOfflineMode.value) {
                generateMetadataFromSelectedImage()
            } else {
                _toastFlow.value = "Fitur analisis gambar hanya tersedia di Mode Online!"
            }
            return
        }

        if (_isOfflineMode.value) {
            generateKeywordsOffline()
            return
        }
        val concept = _promptConcept.value
        if (concept.isBlank()) {
            _toastFlow.value = "Masukkan konsep deskripsi atau pilih satu gambar!"
            return
        }

        val provider = _selectedProvider.value
        val apiKey = if (provider == "Gemini") _geminiKey.value else _groqKey.value

        if (apiKey.isBlank()) {
            _toastFlow.value = "Masukkan API Key $provider terlebih dahulu di bagian API Key!"
            return
        }

        globalGenerationJob = viewModelScope.launch {
            _isGeneratingAi.value = true
            try {
                val titleLimit = _titleCharLimit.value.toInt()
                val descLimit = _descCharLimit.value.toInt()
                val kwLimit = _keywordsLimit.value.toInt()
                val blWords = _blacklistWords.value
                val blacklistInstruction = if (blWords.isNotBlank()) "7. BLACKLIST WORDS: DO NOT include any of these words: $blWords." else ""

                val systemPrompt = """
                    You are an expert Microstock SEO Specialist. Your job is to generate highly accurate metadata (Title, Description, and Keywords) based on the user's input. Don't Use - or _ and odd symbols.

                    Strictly follow these rules:
                    1. Language: Always output the Title, Description, and Keywords in English.
                    2. Title max until $titleLimit characters. 
                    3. Description must be Maximum $descLimit characters a dynamic combination of concept description and organic visual multi usage targets. and suitable for what.
                    4. Keywords Quantity: Generate exactly $kwLimit high-quality keywords. Quality and relevance are prioritized over quantity.
                    5. Keywords Formatting: 
                       - Separate keywords ONLY with a comma without any spaces after the comma (e.g., keyword1,keyword2,keyword3).
                       - DO NOT include periods, dots, or any other special characters.
                    6. Content Relevance: 
                       - No keyword spamming or redudant keywords. 
                       - Avoid contradictory terms.
                       - Do not repeat root words or redundant variations.
                       - Use only 1 word for each keyword. Don't combine 2 words without spaces to make one word, for example like photoobject, it's wrong and the correct is like this: photo, object.
                    $blacklistInstruction

                    Format output must be strictly valid JSON like this: {"title": "...", "description": "...", "keywords": "keyword1,keyword2,keyword3"}
                """.trimIndent()

                val userPrompt = """
                    Analyze this microstock concept: "$concept". Generate professional metadata for Shutterstock, Adobe Stock, Vecteezy, and Freepik in JSON format based on this description according to the system rules.
                """.trimIndent()

                val resultText: String
                if (provider == "Gemini") {
                    val modelName = _selectedModel.value
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                    
                    val req = GeminiRequest(
                        contents = listOf(
                            GeminiContent(
                                parts = listOf(
                                    GeminiPart(text = "$systemPrompt\n\n$userPrompt")
                                )
                            )
                        ),
                        generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
                    )
                    val resp = NetworkClient.apiService.getGeminiContent(url, req)
                    resultText = resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                } else {
                    // Groq
                    val modelName = _selectedModel.value
                    val url = "https://api.groq.com/openai/v1/chat/completions"
                    val authHeader = "Bearer $apiKey"
                    val req = GroqRequest(
                        model = modelName,
                        messages = listOf(
                            GroqMessage(role = "system", content = systemPrompt),
                            GroqMessage(role = "user", content = userPrompt)
                        ),
                        responseFormat = GroqResponseFormat(type = "json_object")
                    )
                    val resp = NetworkClient.apiService.getGroqCompletions(url, authHeader, req)
                    resultText = resp.choices.firstOrNull()?.message?.content ?: ""
                }

                val cleanJson = extractJson(resultText)
                val parsed = gson.fromJson(cleanJson, GeneratedMetadata::class.java)
                if (parsed != null) {
                    _title.value = parsed.title ?: ""
                    _description.value = parsed.description ?: ""
                    _keywords.value = parsed.keywords ?: ""
                    _toastFlow.value = "AI berhasil menghasilkan metadata!"
                } else {
                    _toastFlow.value = "Respon AI tidak valid JSON."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _toastFlow.value = "Error AI: ${e.localizedMessage ?: e.message}"
            } finally {
                _isGeneratingAi.value = false
            }
        }
    }

    fun generateMetadataForSingleImage(id: Int) {
        if (_isOfflineMode.value) {
            _toastFlow.value = "Fitur analisis gambar hanya tersedia di Mode Online!"
            return
        }
        val provider = _selectedProvider.value
        if (provider != "Gemini") {
            _toastFlow.value = "Fitur analisis gambar saat ini hanya didukung oleh Google Gemini!"
            return
        }
        val apiKey = _geminiKey.value
        if (apiKey.isBlank()) {
            _toastFlow.value = "Masukkan API Key Gemini terlebih dahulu di bagian API Key!"
            return
        }
        val imageItem = _imagesList.value.find { it.id == id } ?: return

        val job = viewModelScope.launch {
            _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = true) else it }
            try {
                val bytes = imageItem.originalBytes ?: FileHelper.readBytesFromUri(context, imageItem.uri)
                if (bytes != null) {
                    val parsed = performGeminiAnalysis(imageItem, bytes, apiKey, _selectedModel.value)
                    if (parsed != null) {
                        _title.value = parsed.title ?: ""
                        _description.value = parsed.description ?: ""
                        _keywords.value = parsed.keywords ?: ""

                        _imagesList.value = _imagesList.value.map { 
                            if (it.id == imageItem.id) it.copy(
                                individualTitle = parsed.title ?: "",
                                individualDescription = parsed.description ?: "",
                                individualKeywords = parsed.keywords ?: "",
                                isGeneratingMetadata = false
                            ) else it
                        }
                        _toastFlow.value = "Berhasil generate metadata untuk ${imageItem.name}"
                    } else {
                        _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false) else it }
                        _toastFlow.value = "Gagal parse respon JSON untuk ${imageItem.name}"
                    }
                } else {
                    _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false) else it }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                    _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false) else it }
                    _toastFlow.value = "Error AI Gemini: ${e.localizedMessage ?: e.message}"
                }
            } finally {
                individualGenerationJobs.remove(imageItem.id)
            }
        }
        individualGenerationJobs[imageItem.id] = job
    }

    fun generateMetadataFromSelectedImage() {
        if (_isOfflineMode.value) {
            _toastFlow.value = "Fitur analisis gambar hanya tersedia di Mode Online!"
            return
        }

        val selected = _imagesList.value.filter { it.isSelected }
        if (selected.isEmpty()) {
            _toastFlow.value = "Silakan centang/pilih satu gambar terlebih dahulu!"
            return
        }

        val provider = _selectedProvider.value
        if (provider != "Gemini") {
            _toastFlow.value = "Fitur analisis gambar saat ini hanya didukung oleh Google Gemini!"
            return
        }

        val apiKey = _geminiKey.value
        if (apiKey.isBlank()) {
            _toastFlow.value = "Masukkan API Key Gemini terlebih dahulu di bagian API Key!"
            return
        }

        globalGenerationJob = viewModelScope.launch {
            _isGeneratingAi.value = true
            _isGlobalProcessing.value = true
            try {
                val total = selected.size
                var completed = 0
                for (imageItem in selected) {
                    _globalProcessingText.value = "Generating Process...($completed/$total)"

                    _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = true) else it }
                    
                    val bytes = imageItem.originalBytes ?: FileHelper.readBytesFromUri(context, imageItem.uri)
                    if (bytes == null) {
                        _toastFlow.value = "Tidak dapat membaca file gambar: ${imageItem.name}"
                        _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false) else it }
                        continue
                    }
                    
                    val parsed = performGeminiAnalysis(imageItem, bytes, apiKey, _selectedModel.value)
                    if (parsed != null) {
                        if (selected.size == 1) {
                            _title.value = parsed.title ?: ""
                            _description.value = parsed.description ?: ""
                            _keywords.value = parsed.keywords ?: ""
                        }
                        
                        _imagesList.value = _imagesList.value.map { 
                            if (it.id == imageItem.id) it.copy(
                                individualTitle = parsed.title ?: "",
                                individualDescription = parsed.description ?: "",
                                individualKeywords = parsed.keywords ?: "",
                                isGeneratingMetadata = false
                            ) else it
                        }
                    } else {
                        _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false) else it }
                        _toastFlow.value = "Respon AI tidak valid JSON untuk ${imageItem.name}."
                    }
                    completed++
                    _globalProcessingText.value = "Generating Process...($completed/$total)"
                }
                
                _toastFlow.value = "AI berhasil menganalisis gambar & menghasilkan metadata!"
                
            } catch (e: Exception) {
                e.printStackTrace()
                _toastFlow.value = "Error AI Gemini: ${e.localizedMessage ?: e.message}"
            } finally {
                _isGeneratingAi.value = false
                _isGlobalProcessing.value = false
                _globalProcessingText.value = ""
            }
        }
    }

    private suspend fun performGeminiAnalysis(imageItem: ImageItem, bytes: ByteArray, apiKey: String, modelName: String): GeneratedMetadata? {
        val titleLimit = _titleCharLimit.value.toInt()
        val descLimit = _descCharLimit.value.toInt()
        val kwLimit = _keywordsLimit.value.toInt()
        val blWords = _blacklistWords.value
        val blacklistInstruction = if (blWords.isNotBlank()) "7. BLACKLIST WORDS: DO NOT include any of these words: $blWords." else ""

        val name = imageItem.name.lowercase()
        val systemPrompt = """
            You are an expert Microstock SEO Specialist. Your job is to analyze the provided image/asset and generate highly accurate metadata (Title, Description, and Keywords). Don't Use - or _ and odd symbols.

            Strictly follow these rules:
            1. Language: Always output the Title, Description, and Keywords in English.
            2. Title max until $titleLimit characters. 
            3. Description must be Maximum $descLimit characters a dynamic combination of concept description and organic visual multi usage targets. and suitable for what.
            4. Keywords Quantity: Generate exactly $kwLimit high-quality keywords. Quality and relevance are prioritized over quantity.
            5. Keywords Formatting: 
               - Separate keywords ONLY with a comma without any spaces after the comma (e.g., keyword1,keyword2,keyword3).
               - DO NOT include periods, dots, or any other special characters.
            6. Content Relevance: 
               - No keyword spamming or redundant keywords. 
               - Avoid contradictory terms.
               - Do not repeat root words or redundant variations.
               - Use only 1 word for each keyword. Don't combine 2 words without spaces to make one word, for example like photoobject, it's wrong and the correct is like this: photo, object.
            $blacklistInstruction

            Format output must be strictly valid JSON like this: {"title": "...", "description": "...", "keywords": "keyword1,keyword2,keyword3"}
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val req = if (name.endsWith(".eps")) {
            val epsText = try {
                val fullString = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                if (fullString.length > 250000) {
                    fullString.substring(0, 250000) + "\n...[truncated EPS content]..."
                } else {
                    fullString
                }
            } catch (e: Exception) {
                ""
            }

            val concept = _promptConcept.value
            val conceptHint = if (concept.isNotBlank()) "User provided concept/hint: $concept\n" else ""

            val userPromptEps = """
                $conceptHint
                Analyze this EPS (Encapsulated PostScript) vector file code. Inspect metadata tags, labels, font comments, layer names, coordinates, and shape parameters inside the PostScript content. Deducing what visual concept, template style, interface mock, or illustrative graphic is defined in this PostScript vector, generate professional microstock metadata (Title, Description, and Keywords).

                System Rules:
                $systemPrompt

                EPS Code to analyze:
                ```postscript
                $epsText
                ```
            """.trimIndent()

            GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = userPromptEps)
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
            )
        } else {
            val mimeType = if (name.endsWith(".png")) "image/png" else "image/jpeg"
            val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val concept = _promptConcept.value
            val conceptHint = if (concept.isNotBlank()) "User provided concept/hint: $concept\n" else ""
            
            val userPrompt = """
                $conceptHint
                Analyze this image and generate professional microstock metadata in JSON format according to system rules.
            """.trimIndent()

            GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = "$systemPrompt\n\n$userPrompt"),
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = mimeType,
                                    data = base64Data
                                )
                            )
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
            )
        }

        val resp = NetworkClient.apiService.getGeminiContent(url, req)
        val resultText = resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

        val cleanJson = extractJson(resultText)
        return gson.fromJson(cleanJson, GeneratedMetadata::class.java)
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return trimmed
    }

    // --- XMP Metadata Injection ---
    fun updateIndividualTitle(id: Int, title: String) {
        _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(individualTitle = title) else it }
    }

    fun updateIndividualDescription(id: Int, description: String) {
        _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(individualDescription = description) else it }
    }

    fun updateIndividualKeywords(id: Int, keywords: String) {
        _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(individualKeywords = keywords) else it }
    }

    fun updateIndividualCreator(id: Int, creator: String) {
        _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(individualCreator = creator) else it }
    }

    fun clearIndividualMetadata(id: Int) {
        _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(individualTitle = "", individualDescription = "", individualKeywords = "", individualCreator = "") else it }
    }

    fun injectIndividualMetadata(id: Int) {
        val item = _imagesList.value.find { it.id == id } ?: return
        
        val metaTitle = item.individualTitle
        val metaDesc = item.individualDescription
        val metaKeywordsString = item.individualKeywords
        val metaCreator = item.individualCreator

        if (metaTitle.isBlank() && metaDesc.isBlank() && metaKeywordsString.isBlank() && metaCreator.isBlank()) {
            _toastFlow.value = "Form input metadata tidak boleh kosong!"
            return
        }

        val keywordsList = if (metaKeywordsString.isNotBlank()) {
            metaKeywordsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        viewModelScope.launch {
            _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(isInjectingIndividual = true) else it }
            
            try {
                val bytesToInject = item.originalBytes ?: FileHelper.readBytesFromUri(context, item.uri)
                if (bytesToInject != null) {
                    val nameLower = item.name.lowercase()
                    val injectedBytes: ByteArray = when {
                        nameLower.endsWith(".png") -> {
                            XmpInjector.injectIntoPng(bytesToInject, metaTitle, metaDesc, keywordsList, metaCreator)
                        }
                        nameLower.endsWith(".eps") -> {
                            XmpInjector.injectIntoEps(bytesToInject, metaTitle, metaDesc, keywordsList, metaCreator)
                        }
                        else -> {
                            XmpInjector.injectIntoJpeg(bytesToInject, metaTitle, metaDesc, keywordsList, metaCreator)
                        }
                    }

                    val newestXmpData = XmpData(
                        title = metaTitle,
                        description = metaDesc,
                        keywords = metaKeywordsString,
                        creator = metaCreator
                    )

                    _imagesList.value = _imagesList.value.map { 
                        if (it.id == id) it.copy(
                            injectedBytes = injectedBytes,
                            hasMetadata = true,
                            metadata = newestXmpData,
                            isInjectingIndividual = false
                        ) else it 
                    }
                    _toastFlow.value = "Inject metadata berhasil untuk ${item.name}!"
                } else {
                    _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(isInjectingIndividual = false) else it }
                    _toastFlow.value = "Gagal membaca file ${item.name}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(isInjectingIndividual = false) else it }
                _toastFlow.value = "Gagal inject: ${e.message}"
            }
        }
    }

    fun injectAllIndividualMetadata() {
        val selected = _imagesList.value.filter { it.isSelected }
        if (selected.isEmpty()) {
            _toastFlow.value = "Pilih minimal satu gambar untuk diinject!"
            return
        }
        
        viewModelScope.launch {
            _isInjecting.value = true
            _injectionProgress.value = 0f
            _injectionStatusText.value = "INJECTION 0%"

            try {
                val updatedList = _imagesList.value.map { it }.toMutableList()
                var successCount = 0
                var failCount = 0

                for (i in selected.indices) {
                    val item = selected[i]
                    val metaTitle = item.individualTitle
                    val metaDesc = item.individualDescription
                    val metaKeywordsString = item.individualKeywords
                    val metaCreator = item.individualCreator
                    
                    if (metaTitle.isBlank() && metaDesc.isBlank() && metaKeywordsString.isBlank() && metaCreator.isBlank()) {
                        continue // Skip empty ones
                    }

                    val keywordsList = if (metaKeywordsString.isNotBlank()) {
                        metaKeywordsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        emptyList()
                    }

                    try {
                        val bytesToInject = item.originalBytes ?: FileHelper.readBytesFromUri(context, item.uri)
                        if (bytesToInject != null) {
                            val nameLower = item.name.lowercase()
                            val injectedBytes: ByteArray = when {
                                nameLower.endsWith(".png") -> {
                                    XmpInjector.injectIntoPng(bytesToInject, metaTitle, metaDesc, keywordsList, metaCreator)
                                }
                                nameLower.endsWith(".eps") -> {
                                    XmpInjector.injectIntoEps(bytesToInject, metaTitle, metaDesc, keywordsList, metaCreator)
                                }
                                else -> {
                                    XmpInjector.injectIntoJpeg(bytesToInject, metaTitle, metaDesc, keywordsList, metaCreator)
                                }
                            }

                            val newestXmpData = XmpData(
                                title = metaTitle,
                                description = metaDesc,
                                keywords = metaKeywordsString,
                                creator = metaCreator
                            )

                            val indexInMaster = updatedList.indexOfFirst { it.id == item.id }
                            if (indexInMaster != -1) {
                                updatedList[indexInMaster] = updatedList[indexInMaster].copy(
                                    injectedBytes = injectedBytes,
                                    hasMetadata = true,
                                    metadata = newestXmpData
                                )
                            }
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failCount++
                    }

                    _injectionProgress.value = (i + 1).toFloat() / selected.size.toFloat()
                    val percent = (_injectionProgress.value * 100).toInt()
                    _injectionStatusText.value = "INJECTION $percent%"
                }

                _imagesList.value = updatedList
                _injectionStatusText.value = "INJECTION DONE ($successCount OK, $failCount FAIL)"
            } catch (e: Exception) {
                e.printStackTrace()
                _injectionStatusText.value = "INJECTION ERROR"
                _toastFlow.value = "Terjadi kesalahan saat inject."
            } finally {
                _isInjecting.value = false
            }
        }
    }

    fun injectMetadata() {
        val selected = _imagesList.value.filter { it.isSelected }
        if (selected.isEmpty()) {
            _toastFlow.value = "Pilih minimal satu gambar untuk diinject!"
            return
        }

        val metaTitle = _title.value
        val metaDesc = _description.value
        val metaKeywordsString = _keywords.value
        val metaCreator = _creator.value

        if (metaTitle.isBlank() && metaDesc.isBlank() && metaKeywordsString.isBlank() && metaCreator.isBlank()) {
            _toastFlow.value = "Form input metadata tidak boleh kosong!"
            return
        }

        val keywordsList = if (metaKeywordsString.isNotBlank()) {
            metaKeywordsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        viewModelScope.launch {
            _isInjecting.value = true
            _injectionProgress.value = 0f
            _injectionStatusText.value = "INJECTION 0%"

            try {
                val updatedList = _imagesList.value.map { it }.toMutableList()
                var successCount = 0
                var failCount = 0

                for (i in selected.indices) {
                    val item = selected[i]
                    try {
                        val bytesToInject = item.originalBytes ?: FileHelper.readBytesFromUri(context, item.uri)
                        if (bytesToInject != null) {
                            val nameLower = item.name.lowercase()
                            val injectedBytes: ByteArray = when {
                                nameLower.endsWith(".png") -> {
                                    XmpInjector.injectIntoPng(
                                        bytesToInject,
                                        metaTitle,
                                        metaDesc,
                                        keywordsList,
                                        metaCreator
                                    )
                                }
                                nameLower.endsWith(".eps") -> {
                                    XmpInjector.injectIntoEps(
                                        bytesToInject,
                                        metaTitle,
                                        metaDesc,
                                        keywordsList,
                                        metaCreator
                                    )
                                }
                                else -> {
                                    XmpInjector.injectIntoJpeg(
                                        bytesToInject,
                                        metaTitle,
                                        metaDesc,
                                        keywordsList,
                                        metaCreator
                                    )
                                }
                            }

                            val newestXmpData = XmpData(
                                title = metaTitle,
                                description = metaDesc,
                                keywords = metaKeywordsString,
                                creator = metaCreator
                            )

                            // Find index of this item in the master list
                            val indexInMaster = updatedList.indexOfFirst { it.id == item.id }
                            if (indexInMaster != -1) {
                                updatedList[indexInMaster] = updatedList[indexInMaster].copy(
                                    injectedBytes = injectedBytes,
                                    hasMetadata = true,
                                    metadata = newestXmpData
                                )
                            }
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failCount++
                    }

                    val currentProgress = (i + 1).toFloat() / selected.size
                    _injectionProgress.value = currentProgress
                    _injectionStatusText.value = "INJECTION ${(currentProgress * 100).toInt()}%"
                }

                if (successCount > 0 && !_isOfflineMode.value) {
                    updateDatabaseWithNewOnlineKeywords(metaKeywordsString)
                }

                _imagesList.value = updatedList
                _injectionStatusText.value = "INJECTION 100% DONE"
                _toastFlow.value = "Selesai: $successCount Berhasil, $failCount Gagal di memori. Klik DOWNLOAD untuk menyimpan ke Disk."
                recalculateMetadataFormFromSelection()
            } catch (e: Exception) {
                e.printStackTrace()
                _injectionStatusText.value = "INJECTION FAILED"
                _toastFlow.value = "Injeksi gagal: ${e.localizedMessage ?: e.message}"
            } finally {
                _isInjecting.value = false
            }
        }
    }

    // --- ZIP and Download ---
    fun downloadInjectedFiles() {
        val selected = _imagesList.value.filter { it.isSelected }
        if (selected.isEmpty()) {
            _toastFlow.value = "Pilih gambar yang ingin didownload!"
            return
        }

        viewModelScope.launch {
            _isDownloading.value = true
            _isGlobalProcessing.value = true
            _downloadStatusText.value = "DOWNLOADING..."

            try {
                val total = selected.size
                var completed = 0
                val usedNames = mutableSetOf<String>()

                for (item in selected) {
                    _globalProcessingText.value = "Processing Download...($completed/$total)"
                    val bytesToSave = item.injectedBytes ?: item.originalBytes
                    if (bytesToSave != null) {
                        val ext: String
                        val dotIndex = item.name.lastIndexOf('.')
                        if (dotIndex != -1) {
                            ext = item.name.substring(dotIndex)
                        } else {
                            ext = when {
                                item.name.endsWith("png", true) -> ".png"
                                item.name.endsWith("eps", true) -> ".eps"
                                else -> ".jpg"
                            }
                        }

                        var finalName = ""
                        val metaTitle = item.metadata?.title?.trim()
                        if (!metaTitle.isNullOrEmpty()) {
                            var sanitized = metaTitle.replace(Regex("[\\\\/:*?\"<>|]"), "")
                            sanitized = sanitized.replace(Regex("\\s+"), "-").lowercase()
                            if (sanitized.length > 50) sanitized = sanitized.substring(0, 50)
                            sanitized = sanitized.trimEnd('-')
                            if (sanitized.isNotEmpty()) finalName = "$sanitized$ext"
                        }
                        
                        if (finalName.isEmpty()) {
                            val baseName = if (dotIndex != -1) item.name.substring(0, dotIndex) else item.name
                            finalName = "$baseName$ext"
                        }
                        
                        var uniqueName = finalName
                        var counter = 1
                        val nameWithoutExt = finalName.substringBeforeLast(".")
                        val extension = if (finalName.contains(".")) ".${finalName.substringAfterLast(".")}" else ""
                        while (usedNames.contains(uniqueName)) {
                            uniqueName = "$nameWithoutExt-$counter$extension"
                            counter++
                        }
                        usedNames.add(uniqueName)

                        withContext(Dispatchers.IO) {
                            try {
                                val keyLower = uniqueName.lowercase()
                                val mimeType = when {
                                    keyLower.endsWith(".png") -> "image/png"
                                    keyLower.endsWith(".eps") -> "application/postscript"
                                    else -> "image/jpeg"
                                }
                                FileHelper.saveToDownloads(context, uniqueName, mimeType, bytesToSave)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    completed++
                    _globalProcessingText.value = "Processing Download...($completed/$total)"
                }

                _downloadStatusText.value = "DOWNLOAD DONE"
                _toastFlow.value = "Semua file berhasil disimpan ke folder Download/WarMachineHybrid"

            } catch (e: Exception) {
                e.printStackTrace()
                _downloadStatusText.value = "DOWNLOAD ERROR"
                _toastFlow.value = "Terjadi kesalahan saat menyimpan file: ${e.message}"
            } finally {
                _isGlobalProcessing.value = false
                _globalProcessingText.value = ""
                // Keep the success text for 3 seconds, then clear it
                kotlinx.coroutines.delay(3000)
                _downloadStatusText.value = ""
                _isDownloading.value = false
            }
        }
    }

    fun downloadIndividualFile(id: Int) {
        val item = _imagesList.value.find { it.id == id }
        if (item == null) {
            _toastFlow.value = "Gambar tidak ditemukan!"
            return
        }
        
        viewModelScope.launch {
            _toastFlow.value = "Menyimpan gambar..."
            val bytesToSave = item.injectedBytes ?: item.originalBytes
            if (bytesToSave != null) {
                val ext: String
                val dotIndex = item.name.lastIndexOf('.')
                if (dotIndex != -1) {
                    ext = item.name.substring(dotIndex)
                } else {
                    ext = when {
                        item.name.endsWith("png", true) -> ".png"
                        item.name.endsWith("eps", true) -> ".eps"
                        else -> ".jpg"
                    }
                }
                
                var finalName = ""
                val metaTitle = item.metadata?.title?.trim()
                if (!metaTitle.isNullOrEmpty()) {
                    var sanitized = metaTitle.replace(Regex("[\\\\/:*?\"<>|]"), "")
                    sanitized = sanitized.replace(Regex("\\s+"), "-").lowercase()
                    if (sanitized.length > 50) sanitized = sanitized.substring(0, 50)
                    sanitized = sanitized.trimEnd('-')
                    if (sanitized.isNotEmpty()) finalName = "$sanitized$ext"
                }
                if (finalName.isEmpty()) {
                    val baseName = if (dotIndex != -1) item.name.substring(0, dotIndex) else item.name
                    finalName = "$baseName$ext"
                }
                
                val hasSucceeded = withContext(Dispatchers.IO) {
                    try {
                        val keyLower = finalName.lowercase()
                        val mimeType = when {
                            keyLower.endsWith(".png") -> "image/png"
                            keyLower.endsWith(".eps") -> "application/postscript"
                            else -> "image/jpeg"
                        }
                        val savedUri = FileHelper.saveToDownloads(context, finalName, mimeType, bytesToSave)
                        savedUri != null
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                if (hasSucceeded) {
                    _toastFlow.value = "File berhasil disimpan ke folder Download/WarMachineHybrid"
                } else {
                    _toastFlow.value = "Gagal menyimpan file."
                }
            } else {
                _toastFlow.value = "Byte file tidak valid."
            }
        }
    }
}

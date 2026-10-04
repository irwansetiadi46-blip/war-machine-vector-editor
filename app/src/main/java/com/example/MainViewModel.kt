package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.WarMachineBg
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.ByteArrayOutputStream

enum class SvgExportFormat {
    SVG,
    EPS,
    ZIP_SVG_EPS_JPG
}

data class SvgExportDialogState(
    val isIndividual: Boolean,
    val itemId: Int? = null,
    val svgCount: Int = 1
)

data class KeywordItem(
    val word: String,
    val demandScore: Int = 50,
    val isTrademark: Boolean = false,
    val replacement: String? = null
)

data class ImageItem(
    val id: Int,
    val name: String,
    val uri: Uri,
    val originalBytes: ByteArray?,
    val injectedBytes: ByteArray?,
    val hasMetadata: Boolean,
    val isSelected: Boolean = false,
    val metadata: XmpData?,
    val individualTitle: String = "",
    val individualDescription: String = "",
    val individualKeywords: String = "",
    val individualKeywordItems: List<KeywordItem> = emptyList(),
    val individualCreator: String = "",
    val isGeneratingMetadata: Boolean = false,
    val isInjectingIndividual: Boolean = false,
    val previewUri: Uri? = null,
    val previewBytes: ByteArray? = null
) {
    val isGenerated: Boolean
        get() = individualTitle.isNotBlank() || individualKeywords.isNotBlank() || individualDescription.isNotBlank() || hasMetadata

    fun getEffectiveKeywordItems(): List<KeywordItem> {
        if (individualKeywordItems.isNotEmpty()) return individualKeywordItems
        if (individualKeywords.isBlank()) return emptyList()
        return individualKeywords.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { KeywordItem(word = it, demandScore = 55, isTrademark = false, replacement = null) }
    }
}

data class GeneratedMetadata(
    val title: String? = null,
    val description: String? = null,
    val keywords: List<KeywordItem>? = null
)

enum class SelectionMode {
    SINGLE, MULTI, ALL
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val gson = Gson()
    val offlineKeywordMatcher = OfflineKeywordMatcher(application)
    private var nextId = 1

    private val geminiResponseSchema: Map<String, Any> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "title" to mapOf("type" to "STRING"),
            "description" to mapOf("type" to "STRING"),
            "keywords" to mapOf(
                "type" to "ARRAY",
                "items" to mapOf(
                    "type" to "OBJECT",
                    "properties" to mapOf(
                        "word" to mapOf("type" to "STRING"),
                        "demandScore" to mapOf("type" to "INTEGER"),
                        "isTrademark" to mapOf("type" to "BOOLEAN"),
                        "replacement" to mapOf("type" to "STRING")
                    ),
                    "required" to listOf("word", "demandScore", "isTrademark")
                )
            )
        ),
        "required" to listOf("title", "description", "keywords")
    )

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
    private val _geminiKey = MutableStateFlow("")
    val geminiKey = _geminiKey.asStateFlow()

    private val _selectedProvider = MutableStateFlow("Gemini")
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.5-flash-lite")
    val selectedModel = _selectedModel.asStateFlow()

    private val _promptConcept = MutableStateFlow("")
    val promptConcept = _promptConcept.asStateFlow()

    private val _savedPromptConcept = MutableStateFlow("")
    val savedPromptConcept = _savedPromptConcept.asStateFlow()

    private val _titleCharLimit = MutableStateFlow(100f)
    val titleCharLimit = _titleCharLimit.asStateFlow()

    private val _descCharLimit = MutableStateFlow(150f)
    val descCharLimit = _descCharLimit.asStateFlow()

    private val _keywordsLimit = MutableStateFlow(49f)
    val keywordsLimit = _keywordsLimit.asStateFlow()

    private val _blacklistWords = MutableStateFlow("")
    val blacklistWords = _blacklistWords.asStateFlow()

    private val _savedBlacklistWords = MutableStateFlow("")
    val savedBlacklistWords = _savedBlacklistWords.asStateFlow()

    private val _isAutoInjectionEnabled = MutableStateFlow(true)
    val isAutoInjectionEnabled = _isAutoInjectionEnabled.asStateFlow()

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

    private val _svgExportDialogState = MutableStateFlow<SvgExportDialogState?>(null)
    val svgExportDialogState = _svgExportDialogState.asStateFlow()

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

                if (_isAutoInjectionEnabled.value) {
                    withContext(Dispatchers.Main) {
                        val hasSelected = _imagesList.value.any { it.isSelected }
                        if (hasSelected) {
                            injectMetadata()
                        } else if (_imagesList.value.isNotEmpty()) {
                            selectAllImages(true)
                            injectMetadata()
                        }
                    }
                }
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
        _geminiKey.value = prefs.getString("gemini_key", "") ?: ""
        _selectedProvider.value = "Gemini"
        _selectedModel.value = prefs.getString("selected_model", "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite"
        _creator.value = prefs.getString("saved_creator", "") ?: ""
        _isOfflineMode.value = prefs.getBoolean("is_offline_mode", false)

        // Auto-load persistent slider limits
        _titleCharLimit.value = prefs.getFloat("saved_title_limit", 100f)
        _descCharLimit.value = prefs.getFloat("saved_desc_limit", 150f)
        _keywordsLimit.value = prefs.getFloat("saved_keywords_limit", 49f)

        // Auto-load saved Kata Kunci Inti & Blacklist Words
        val savedConcept = prefs.getString("saved_prompt_concept", "") ?: ""
        val savedBlacklist = prefs.getString("saved_blacklist_words", "") ?: ""
        _promptConcept.value = savedConcept
        _savedPromptConcept.value = savedConcept
        _blacklistWords.value = savedBlacklist
        _savedBlacklistWords.value = savedBlacklist

        // Auto-load Auto Injection preference (Default ON)
        _isAutoInjectionEnabled.value = prefs.getBoolean("is_auto_injection", true)
    }

    fun saveApiKey(gemini: String) {
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("gemini_key", gemini)
            putString("selected_provider", "Gemini")
            apply()
        }
        _geminiKey.value = gemini
        _selectedProvider.value = "Gemini"
        _toastFlow.value = "Kunci API Google Gemini Berhasil Disimpan"
    }

    fun saveApiKeys(groq: String = "", gemini: String, provider: String = "Gemini") {
        saveApiKey(gemini)
    }

    fun updateDefaultModel(provider: String = "Gemini") {
        _selectedProvider.value = "Gemini"
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_model", model).apply()
    }

    fun setPromptConcept(concept: String) {
        _promptConcept.value = concept
    }

    fun savePromptConceptPermanent() {
        val concept = _promptConcept.value
        if (concept.isBlank()) {
            return
        }
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("saved_prompt_concept", concept).apply()
        _savedPromptConcept.value = concept
        _toastFlow.value = "Kata kunci inti berhasil disimpan!"
    }

    fun clearPromptConceptPermanent() {
        _promptConcept.value = ""
        _savedPromptConcept.value = ""
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("saved_prompt_concept").apply()
        _toastFlow.value = "Kata kunci inti dihapus"
    }

    fun setBlacklistWords(value: String) {
        _blacklistWords.value = value
    }

    fun saveBlacklistWordsPermanent() {
        val bl = _blacklistWords.value
        if (bl.isBlank()) {
            return
        }
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("saved_blacklist_words", bl).apply()
        _savedBlacklistWords.value = bl
        _toastFlow.value = "Blacklist words berhasil disimpan!"
    }

    fun clearBlacklistWordsPermanent() {
        _blacklistWords.value = ""
        _savedBlacklistWords.value = ""
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("saved_blacklist_words").apply()
        _toastFlow.value = "Blacklist words dihapus"
    }

    fun setAutoInjectionEnabled(enabled: Boolean) {
        _isAutoInjectionEnabled.value = enabled
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_auto_injection", enabled).apply()
    }

    fun setTitleCharLimit(value: Float) {
        _titleCharLimit.value = value
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("saved_title_limit", value).apply()
    }

    fun setDescCharLimit(value: Float) {
        _descCharLimit.value = value
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("saved_desc_limit", value).apply()
    }

    fun setKeywordsLimit(value: Float) {
        _keywordsLimit.value = value
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("saved_keywords_limit", value).apply()
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
                    
                    // Accept Jpeg, Png, Eps, and Svg
                    if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".eps") || nameLower.endsWith(".svg")) {
                        val originalBytes = FileHelper.readBytesFromUri(context, uri)
                        
                        var hasMeta = false
                        var meta: XmpData? = null

                        var previewUri: Uri? = null
                        var previewBytes: ByteArray? = null

                        if (originalBytes != null) {
                            val isPng = nameLower.endsWith(".png")
                            val isEps = nameLower.endsWith(".eps")
                            val isSvg = nameLower.endsWith(".svg")
                            meta = XmpInjector.parseXMP(originalBytes, isPng = isPng, isEps = isEps, isSvg = isSvg)
                            if (meta != null && (meta.title.isNotBlank() || meta.description.isNotBlank() || meta.keywords.isNotBlank() || meta.creator.isNotBlank())) {
                                hasMeta = true
                            }

                            if (isSvg) {
                                val base64Png = SvgRenderer.renderSvgToPngBase64(context, originalBytes)
                                if (base64Png != null) {
                                    try {
                                        val decoded = android.util.Base64.decode(base64Png, android.util.Base64.NO_WRAP)
                                        val tempFile = java.io.File(context.cacheDir, "preview_svg_${System.currentTimeMillis()}_$nextId.png")
                                        tempFile.writeBytes(decoded)
                                        previewUri = Uri.fromFile(tempFile)
                                        previewBytes = decoded
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            } else if (isEps) {
                                val base64Jpg = EpsRenderer.renderEpsToJpegBase64(context, originalBytes)
                                if (base64Jpg != null) {
                                    try {
                                        val decoded = android.util.Base64.decode(base64Jpg, android.util.Base64.NO_WRAP)
                                        val tempFile = java.io.File(context.cacheDir, "preview_eps_${System.currentTimeMillis()}_$nextId.jpg")
                                        tempFile.writeBytes(decoded)
                                        previewUri = Uri.fromFile(tempFile)
                                        previewBytes = decoded
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            } else if (isPng || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
                                previewUri = uri
                                previewBytes = originalBytes
                            }
                        }

                        current.add(
                            ImageItem(
                                id = nextId++,
                                name = name,
                                uri = uri,
                                originalBytes = null, // Set to null to avoid out-of-memory errors
                                injectedBytes = null,
                                hasMetadata = hasMeta,
                                isSelected = isAllMode,
                                metadata = meta,
                                previewUri = previewUri,
                                previewBytes = previewBytes
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

    fun selectAllImages(selected: Boolean = true) {
        _imagesList.value = _imagesList.value.map { it.copy(isSelected = selected) }
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
        if (_imagesList.value.isNotEmpty()) {
            if (!_isOfflineMode.value) {
                generateMetadataForAllImages()
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

        val apiKey = _geminiKey.value

        if (apiKey.isBlank()) {
            _toastFlow.value = "Masukkan API Key Google Gemini terlebih dahulu di bagian API Key!"
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
                    You are an expert Microstock SEO Specialist. Your job is to generate highly accurate metadata (Title, Description, and Keywords with popularity & trademark detection) based on the user's input in structured JSON format. Don't Use - or _ and odd symbols.

                    Strictly follow these rules:
                    1. Language: Always output the Title, Description, and Keywords in English.
                    2. Title max until $titleLimit characters. 
                    3. Description must be Maximum $descLimit characters a dynamic combination of concept description and organic visual multi usage targets. and suitable for what.
                    4. Keywords Quantity: Generate exactly $kwLimit high-quality keywords. Quality and relevance are prioritized over quantity.
                    5. Keywords Formatting & Demand Score (ImStocker-Style): 
                       Each keyword MUST be an object with:
                       - "word": String (single word, no spaces or special symbols).
                       - "demandScore": Integer from 1 to 100 based on estimated buyer search demand on microstock platforms (e.g. Shutterstock, Adobe Stock, Freepik).
                         * >= 90: Very High search volume
                         * 75-89: High demand
                         * 50-74: Medium-High demand
                         * 25-49: Medium-Low demand
                         * < 25: Low demand
                       - "isTrademark": Boolean (true if keyword contains registered trademark/brand like iPhone, Nike, Adobe, Apple, etc., false otherwise).
                       - "replacement": String or null (If isTrademark is true, provide the generic safe microstock replacement, e.g. "smartphone" for "iPhone"; otherwise null).
                    6. Content Relevance: 
                       - No keyword spamming or redundant root words. 
                       - Avoid contradictory terms.
                       - Use only 1 word for each keyword.
                    $blacklistInstruction

                    Format output strictly matching this schema:
                    {"title": "...", "description": "...", "keywords": [{"word": "...", "demandScore": 80, "isTrademark": false, "replacement": null}]}
                """.trimIndent()

                val userPrompt = """
                    Analyze this microstock concept: "$concept". Generate professional metadata for Shutterstock, Adobe Stock, Vecteezy, and Freepik in JSON format based on this description according to the system rules.
                """.trimIndent()

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
                    generationConfig = GeminiGenerationConfig(
                        responseMimeType = "application/json",
                        responseSchema = geminiResponseSchema
                    )
                )
                val resp = NetworkClient.apiService.getGeminiContent(url, req)
                val resultText = resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

                val cleanJson = extractJson(resultText)
                val parsed = parseGeneratedMetadata(cleanJson)
                if (parsed != null) {
                    val kwsList = parsed.keywords ?: emptyList()
                    val kwsString = kwsList.joinToString(",") { 
                        if (it.isTrademark && !it.replacement.isNullOrBlank()) it.replacement else it.word 
                    }
                    _title.value = parsed.title ?: ""
                    _description.value = parsed.description ?: ""
                    _keywords.value = kwsString
                    _toastFlow.value = "AI berhasil menghasilkan metadata!"

                    if (_isAutoInjectionEnabled.value) {
                        val hasSelected = _imagesList.value.any { it.isSelected }
                        if (hasSelected) {
                            injectMetadata()
                        } else if (_imagesList.value.isNotEmpty()) {
                            selectAllImages(true)
                            injectMetadata()
                        }
                    }
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
        val apiKey = _geminiKey.value
        if (apiKey.isBlank()) {
            _toastFlow.value = "Masukkan API Key Google Gemini terlebih dahulu di bagian API Key!"
            return
        }
        val imageItem = _imagesList.value.find { it.id == id } ?: return

        val job = viewModelScope.launch {
            _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = true) else it }
            try {
                val parsed = performGeminiAnalysis(imageItem, apiKey, _selectedModel.value)
                if (parsed != null) {
                    val kwsList = parsed.keywords ?: emptyList()
                    val kwsString = kwsList.joinToString(",") { 
                        if (it.isTrademark && !it.replacement.isNullOrBlank()) it.replacement else it.word 
                    }

                    _title.value = parsed.title ?: ""
                    _description.value = parsed.description ?: ""
                    _keywords.value = kwsString

                    _imagesList.value = _imagesList.value.map { 
                        if (it.id == imageItem.id) it.copy(
                            individualTitle = parsed.title ?: "",
                            individualDescription = parsed.description ?: "",
                            individualKeywords = kwsString,
                            individualKeywordItems = kwsList,
                            isGeneratingMetadata = false
                        ) else it
                    }
                    _toastFlow.value = "Berhasil generate metadata untuk ${imageItem.name}"
                    if (_isAutoInjectionEnabled.value) {
                        injectIndividualMetadata(imageItem.id)
                    }
                } else {
                    _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false) else it }
                    _toastFlow.value = "Gagal parse respon JSON untuk ${imageItem.name}"
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

    fun generateMetadataForAllImages() {
        if (_isOfflineMode.value) {
            _toastFlow.value = "Fitur analisis gambar hanya tersedia di Mode Online!"
            return
        }

        val allImages = _imagesList.value
        if (allImages.isEmpty()) {
            _toastFlow.value = "Belum ada gambar yang di-import!"
            return
        }

        val apiKey = _geminiKey.value
        if (apiKey.isBlank()) {
            _toastFlow.value = "Masukkan API Key Google Gemini terlebih dahulu di bagian API Key!"
            return
        }

        globalGenerationJob = viewModelScope.launch {
            _isGeneratingAi.value = true
            _isGlobalProcessing.value = true
            try {
                val total = allImages.size
                var completed = 0
                for (imageItem in allImages) {
                    _globalProcessingText.value = "Generating Process...($completed/$total)"

                    _imagesList.value = _imagesList.value.map { if (it.id == imageItem.id) it.copy(isGeneratingMetadata = true) else it }
                    
                    val parsed = performGeminiAnalysis(imageItem, apiKey, _selectedModel.value)
                    if (parsed != null) {
                        val kwsList = parsed.keywords ?: emptyList()
                        val kwsString = kwsList.joinToString(",") { 
                            if (it.isTrademark && !it.replacement.isNullOrBlank()) it.replacement else it.word 
                        }

                        if (allImages.size == 1) {
                            _title.value = parsed.title ?: ""
                            _description.value = parsed.description ?: ""
                            _keywords.value = kwsString
                        }
                        
                        _imagesList.value = _imagesList.value.map { 
                            if (it.id == imageItem.id) it.copy(
                                individualTitle = parsed.title ?: "",
                                individualDescription = parsed.description ?: "",
                                individualKeywords = kwsString,
                                individualKeywordItems = kwsList,
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
                
                _toastFlow.value = "AI berhasil menganalisis semua gambar & menghasilkan metadata!"
                if (_isAutoInjectionEnabled.value) {
                    injectAllIndividualMetadata()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _toastFlow.value = "Error AI Gemini: ${e.localizedMessage ?: e.message}"
            } finally {
                _isGeneratingAi.value = false
                _isGlobalProcessing.value = false
                _globalProcessingText.value = ""
                _imagesList.value = _imagesList.value.map { it.copy(isGeneratingMetadata = false) }
            }
        }
    }

    fun generateMetadataFromSelectedImage() {
        generateMetadataForAllImages()
    }

    private suspend fun performGeminiAnalysis(imageItem: ImageItem, apiKey: String, modelName: String): GeneratedMetadata? {
        val titleLimit = _titleCharLimit.value.toInt()
        val descLimit = _descCharLimit.value.toInt()
        val kwLimit = _keywordsLimit.value.toInt()
        val blWords = _blacklistWords.value
        val blacklistInstruction = if (blWords.isNotBlank()) "7. BLACKLIST WORDS: DO NOT include any of these words: $blWords." else ""

        val name = imageItem.name.lowercase()
        val systemPrompt = """
            You are an expert Microstock SEO Specialist. Your job is to analyze the provided image/asset and generate highly accurate metadata (Title, Description, and Keywords with popularity & trademark detection) in structured JSON format. Don't Use - or _ and odd symbols.

            Strictly follow these rules:
            1. Language: Always output Title, Description, and Keywords in English.
            2. Title max until $titleLimit characters (100-200). 
            3. Description must be Maximum $descLimit characters a dynamic combination of concept description and organic visual multi usage targets and suitable for what.
            4. Keywords Quantity: Generate exactly $kwLimit high-quality keywords. Quality and relevance are prioritized over quantity.
            5. Keywords Formatting & Demand Score (ImStocker-Style): 
               Each keyword MUST be an object with:
               - "word": String (single word, no spaces or special symbols).
               - "demandScore": Integer from 1 to 100 based on estimated buyer search demand on microstock platforms (e.g. Shutterstock, Adobe Stock, Freepik):
                 * >= 90: Very High search volume
                 * 75-89: High demand
                 * 50-74: Medium-High demand
                 * 25-49: Medium-Low demand
                 * < 25: Low demand
               - "isTrademark": Boolean (true if keyword contains registered trademark/brand like iPhone, Nike, Adobe, Apple, Canon, Lego, etc., false otherwise).
               - "replacement": String or null (If AI generates a trademarked word, set isTrademark = true and provide its generic safe microstock replacement, e.g. "smartphone" for "iPhone", "shoes" for "Nike", "software" for "Photoshop"; otherwise set to null).
            6. Content Relevance: 
               - No keyword spamming or redundant root words. 
               - Avoid contradictory terms.
               - Use only 1 word for each keyword.
            $blacklistInstruction

            Format output strictly matching this schema:
            {"title": "...", "description": "...", "keywords": [{"word": "...", "demandScore": 85, "isTrademark": false, "replacement": null}]}
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val req = if (imageItem.previewUri != null) {
            val isSvg = name.endsWith(".svg")
            val isEps = name.endsWith(".eps")
            val mimeType = if (isSvg) "image/png" else if (name.endsWith(".png")) "image/png" else "image/jpeg"
            val base64Data = FileHelper.readBase64FromUri(context, imageItem.previewUri) ?: ""
            val concept = _promptConcept.value
            val conceptHint = if (concept.isNotBlank()) "User provided concept/hint: $concept\n" else ""
            
            val assetType = if (isSvg) "SVG Vector graphic" else if (isEps) "EPS Vector graphic" else "Photo/Illustration"
            
            val userPrompt = if (isEps) {
                val bytes = imageItem.originalBytes ?: FileHelper.readBytesFromUri(context, imageItem.uri) ?: ByteArray(0)
                val textContent = try {
                    val fullString = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    if (fullString.length > 100000) {
                        fullString.substring(0, 100000) + "\n...[truncated EPS content]..."
                    } else {
                        fullString
                    }
                } catch (e: Exception) { "" }

                """
                $conceptHint
                Analyze BOTH the provided visual image (which is an accurate visual render of the EPS vector graphic) AND the EPS source code/header.
                Inspect shapes, colors, layout, subject matter, style, and visual composition in the rendered image, and cross-reference them with title headers, metadata tags, layer labels, comments, and PostScript vector structures in the EPS source code.
                Generate highly accurate, professional microstock metadata (Title(Title max until $titleLimit characters.), Description, and Keywords) that perfectly describes the visual subject, vector style, theme, color scheme, and microstock utility of this asset.
                
                System Rules:
                $systemPrompt
                
                EPS Source Code / Header:
                ```postscript
                $textContent
                ```
                """.trimIndent()
            } else {
                """
                $conceptHint
                This image is a visual render of a $assetType.
                Analyze this image and generate highly accurate, professional microstock metadata in JSON format according to system rules.
                Include relevant microstock keywords (such as vector, illustration, graphic, design element, etc. if appropriate for the visual style).
                """.trimIndent()
            }

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
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = geminiResponseSchema
                )
            )
        } else if (name.endsWith(".svg")) {
            val bytes = imageItem.originalBytes ?: FileHelper.readBytesFromUri(context, imageItem.uri) ?: ByteArray(0)
            val base64SvgPng = SvgRenderer.renderSvgToPngBase64(context, bytes)
            
            val concept = _promptConcept.value
            val conceptHint = if (concept.isNotBlank()) "User provided concept/hint: $concept\n" else ""
            
            val textContent = try {
                val fullString = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                if (fullString.length > 100000) {
                    fullString.substring(0, 100000) + "\n...[truncated SVG content]..."
                } else {
                    fullString
                }
            } catch (e: Exception) {
                ""
            }

            if (base64SvgPng != null) {
                val userPromptSvg = """
                    $conceptHint
                    Analyze BOTH the provided visual image (which is a high-fidelity render of the SVG vector) AND the SVG source code.
                    Inspect paths, colors, shapes, visual layout, and graphic style in the visual image. Cross-reference them with class names, label attributes, IDs, and path data in the SVG source code.
                    Generate professional microstock metadata (Title(Title max until $titleLimit characters.), Description, and Keywords) that is perfectly accurate and highly relevant to the actual design, utility, visual themes, and colors of this vector asset.
                    
                    System Rules:
                    $systemPrompt
                    
                    SVG Source Code for reference:
                    ```xml
                    $textContent
                    ```
                """.trimIndent()

                GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = userPromptSvg),
                                GeminiPart(
                                    inlineData = GeminiInlineData(
                                        mimeType = "image/png",
                                        data = base64SvgPng
                                    )
                                )
                            )
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        responseMimeType = "application/json",
                        responseSchema = geminiResponseSchema
                    )
                )
            } else {
                val userPromptSvgNoRender = """
                    $conceptHint
                    Analyze this SVG vector file code. Inspect metadata tags, labels, class names, path details, coordinates, and color properties inside the vector content. Deducing what visual concept, template style, interface mock, or illustrative graphic is defined in this vector, generate professional microstock metadata (Title, Description, and Keywords).
                    
                    System Rules:
                    $systemPrompt
                    
                    SVG Code to analyze:
                    ```xml
                    $textContent
                    ```
                """.trimIndent()

                GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = userPromptSvgNoRender)
                            )
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        responseMimeType = "application/json",
                        responseSchema = geminiResponseSchema
                    )
                )
            }
        } else if (name.endsWith(".eps")) {
            val bytes = imageItem.originalBytes ?: FileHelper.readBytesFromUri(context, imageItem.uri) ?: ByteArray(0)
            val textContent = try {
                val fullString = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                if (fullString.length > 200000) {
                    fullString.substring(0, 200000) + "\n...[truncated vector content]..."
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
                Analyze this EPS (Encapsulated PostScript) vector file code. 
                Inspect metadata tags, title headers, keywords, labels, creator notes, fonts, layer descriptions, coordinate structures, paths, shapes, transformations, and color operators (like CMYK/RGB fills and strokes).
                Reconstruct the visual representation mentally from the paths, curves, color schemes, and structural layout defined in this PostScript vector code. 
                Generate highly accurate, professional microstock metadata (Title, Description, and Keywords) that is perfectly relevant to the actual design, theme, and utility of the graphic.
                
                System Rules:
                $systemPrompt
                
                EPS Code to analyze:
                ```postscript
                $textContent
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
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = geminiResponseSchema
                )
            )
        } else {
            val mimeType = if (name.endsWith(".png")) "image/png" else "image/jpeg"
            val base64Data = FileHelper.readBase64FromUri(context, imageItem.uri) ?: ""
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
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = geminiResponseSchema
                )
            )
        }

        val resp = NetworkClient.apiService.getGeminiContent(url, req)
        val resultText = resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

        val cleanJson = extractJson(resultText)
        return parseGeneratedMetadata(cleanJson)
    }

    fun parseGeneratedMetadata(cleanJson: String): GeneratedMetadata? {
        return try {
            val root = JsonParser.parseString(cleanJson).asJsonObject
            val title = if (root.has("title") && !root.get("title").isJsonNull) root.get("title").asString else ""
            val description = if (root.has("description") && !root.get("description").isJsonNull) root.get("description").asString else ""
            val keywordList = mutableListOf<KeywordItem>()

            val kwElem = root.get("keywords")
            if (kwElem != null) {
                if (kwElem.isJsonArray) {
                    for (item in kwElem.asJsonArray) {
                        if (item.isJsonObject) {
                            val obj = item.asJsonObject
                            val word = if (obj.has("word") && !obj.get("word").isJsonNull) obj.get("word").asString.trim() else ""
                            val demandScore = if (obj.has("demandScore") && !obj.get("demandScore").isJsonNull) obj.get("demandScore").asInt else 50
                            val isTrademark = if (obj.has("isTrademark") && !obj.get("isTrademark").isJsonNull) obj.get("isTrademark").asBoolean else false
                            val replacement = if (obj.has("replacement") && !obj.get("replacement").isJsonNull) {
                                val rep = obj.get("replacement").asString.trim()
                                if (rep.equals("null", ignoreCase = true) || rep.isEmpty()) null else rep
                            } else null
                            if (word.isNotBlank()) {
                                keywordList.add(KeywordItem(word = word, demandScore = demandScore.coerceIn(1, 100), isTrademark = isTrademark, replacement = replacement))
                            }
                        } else if (item.isJsonPrimitive) {
                            val word = item.asString.trim()
                            if (word.isNotBlank()) {
                                keywordList.add(KeywordItem(word = word, demandScore = 55))
                            }
                        }
                    }
                } else if (kwElem.isJsonPrimitive) {
                    val rawKws = kwElem.asString
                    rawKws.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        keywordList.add(KeywordItem(word = it, demandScore = 55))
                    }
                }
            }
            GeneratedMetadata(title = title, description = description, keywords = keywordList)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
        val words = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        _imagesList.value = _imagesList.value.map { item ->
            if (item.id == id) {
                val existingMap = item.individualKeywordItems.associateBy { it.word.lowercase() }
                val newItems = words.map { w ->
                    existingMap[w.lowercase()] ?: KeywordItem(word = w, demandScore = 55)
                }
                item.copy(individualKeywords = keywords, individualKeywordItems = newItems)
            } else item
        }
    }

    fun removeKeywordFromImage(imageId: Int, index: Int) {
        _imagesList.value = _imagesList.value.map { item ->
            if (item.id == imageId) {
                val current = item.getEffectiveKeywordItems().toMutableList()
                if (index in current.indices) {
                    current.removeAt(index)
                    val newStr = current.joinToString(",") { it.word }
                    item.copy(individualKeywordItems = current, individualKeywords = newStr)
                } else item
            } else item
        }
    }

    fun replaceTrademarkKeyword(imageId: Int, index: Int) {
        _imagesList.value = _imagesList.value.map { item ->
            if (item.id == imageId) {
                val current = item.getEffectiveKeywordItems().toMutableList()
                if (index in current.indices) {
                    val old = current[index]
                    val repWord = old.replacement ?: "generic"
                    current[index] = old.copy(word = repWord, isTrademark = false, replacement = null)
                    val newStr = current.joinToString(",") { it.word }
                    item.copy(individualKeywordItems = current, individualKeywords = newStr)
                } else item
            } else item
        }
    }

    fun addKeywordToImage(imageId: Int, word: String) {
        val clean = word.trim().replace(",", "")
        if (clean.isBlank()) return
        _imagesList.value = _imagesList.value.map { item ->
            if (item.id == imageId) {
                val current = item.getEffectiveKeywordItems().toMutableList()
                current.add(KeywordItem(word = clean, demandScore = 60, isTrademark = false, replacement = null))
                val newStr = current.joinToString(",") { it.word }
                item.copy(individualKeywordItems = current, individualKeywords = newStr)
            } else item
        }
    }

    fun autoFixAllTrademarks(imageId: Int) {
        _imagesList.value = _imagesList.value.map { item ->
            if (item.id == imageId) {
                val current = item.getEffectiveKeywordItems().map { kw ->
                    if (kw.isTrademark && !kw.replacement.isNullOrBlank()) {
                        kw.copy(word = kw.replacement, isTrademark = false, replacement = null)
                    } else kw
                }
                val newStr = current.joinToString(",") { it.word }
                item.copy(individualKeywordItems = current, individualKeywords = newStr)
            } else item
        }
    }

    fun updateIndividualCreator(id: Int, creator: String) {
        _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(individualCreator = creator) else it }
    }

    fun clearIndividualMetadata(id: Int) {
        _imagesList.value = _imagesList.value.map { 
            if (it.id == id) it.copy(
                individualTitle = "", 
                individualDescription = "", 
                individualKeywords = "", 
                individualKeywordItems = emptyList(), 
                individualCreator = ""
            ) else it 
        }
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
                        nameLower.endsWith(".svg") -> {
                            XmpInjector.injectIntoSvg(bytesToInject, metaTitle, metaDesc, keywordsList)
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
        val selected = if (_imagesList.value.any { it.isSelected }) _imagesList.value.filter { it.isSelected } else _imagesList.value
        if (selected.isEmpty()) {
            _toastFlow.value = "Belum ada gambar yang di-import!"
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
                                nameLower.endsWith(".svg") -> {
                                    XmpInjector.injectIntoSvg(bytesToInject, metaTitle, metaDesc, keywordsList)
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
                                nameLower.endsWith(".svg") -> {
                                    XmpInjector.injectIntoSvg(
                                        bytesToInject,
                                        metaTitle,
                                        metaDesc,
                                        keywordsList
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

    // --- SVG Export Dialog Trigger & Handlers ---
    fun showSvgExportDialogForIndividual(id: Int) {
        val item = _imagesList.value.find { it.id == id } ?: return
        if (item.name.endsWith(".svg", ignoreCase = true)) {
            _svgExportDialogState.value = SvgExportDialogState(isIndividual = true, itemId = id, svgCount = 1)
        } else {
            downloadIndividualFileWithFormat(id, SvgExportFormat.SVG)
        }
    }

    fun showSvgExportDialogForBulk() {
        val selected = if (_imagesList.value.any { it.isSelected }) _imagesList.value.filter { it.isSelected } else _imagesList.value
        if (selected.isEmpty()) {
            _toastFlow.value = "Belum ada gambar yang di-import!"
            return
        }
        val svgCount = selected.count { it.name.endsWith(".svg", ignoreCase = true) }
        if (svgCount > 0) {
            _svgExportDialogState.value = SvgExportDialogState(isIndividual = false, svgCount = svgCount)
        } else {
            downloadInjectedFilesWithFormat(SvgExportFormat.SVG)
        }
    }

    fun dismissSvgExportDialog() {
        _svgExportDialogState.value = null
    }

    fun confirmSvgExportFormat(format: SvgExportFormat) {
        val dialogState = _svgExportDialogState.value ?: return
        _svgExportDialogState.value = null
        if (dialogState.isIndividual) {
            val itemId = dialogState.itemId ?: return
            downloadIndividualFileWithFormat(itemId, format)
        } else {
            downloadInjectedFilesWithFormat(format)
        }
    }

    fun downloadIndividualFileWithFormat(id: Int, format: SvgExportFormat) {
        val item = _imagesList.value.find { it.id == id } ?: return

        viewModelScope.launch {
            _toastFlow.value = "Memproses dan menyimpan..."
            _isDownloading.value = true
            _isGlobalProcessing.value = true
            _globalProcessingText.value = "Processing Export..."

            try {
                val baseBytes = item.injectedBytes ?: item.originalBytes ?: FileHelper.readBytesFromUri(context, item.uri)
                if (baseBytes == null) {
                    _toastFlow.value = "Byte file tidak valid."
                    return@launch
                }

                val metaTitle = item.metadata?.title?.trim() ?: ""
                val metaDesc = item.metadata?.description?.trim() ?: ""
                val metaKeywordsStr = item.metadata?.keywords ?: ""
                val keywordsList = metaKeywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val metaCreator = item.metadata?.creator?.trim() ?: ""

                val dotIndex = item.name.lastIndexOf('.')
                val baseNameRaw = if (dotIndex != -1) item.name.substring(0, dotIndex) else item.name

                var sanitizedTitle = ""
                if (metaTitle.isNotEmpty()) {
                    sanitizedTitle = metaTitle.replace(Regex("[\\\\/:*?\"<>|]"), "").replace(Regex("\\s+"), "-").lowercase()
                    if (sanitizedTitle.length > 200) sanitizedTitle = sanitizedTitle.substring(0, 200).trimEnd('-')
                }
                val baseName = if (sanitizedTitle.isNotEmpty()) sanitizedTitle else baseNameRaw

                val isSvg = item.name.endsWith(".svg", ignoreCase = true)

                withContext(Dispatchers.IO) {
                    if (isSvg) {
                        when (format) {
                            SvgExportFormat.SVG -> {
                                val svgBytes = XmpInjector.injectIntoSvg(baseBytes, metaTitle, metaDesc, keywordsList)
                                val fileName = "$baseName.svg"
                                FileHelper.saveToDownloads(context, fileName, "image/svg+xml", svgBytes)
                            }
                            SvgExportFormat.EPS -> {
                                val epsBytes = SvgToEpsConverter.convertSvgToEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)
                                val fileName = "$baseName.eps"
                                FileHelper.saveToDownloads(context, fileName, "application/postscript", epsBytes)
                            }
                            SvgExportFormat.ZIP_SVG_EPS_JPG -> {
                                val zipName = "${baseName}_bundle.zip"
                                FileHelper.saveStreamToDownloads(context, zipName, "application/zip") { outputStream ->
                                    java.util.zip.ZipOutputStream(outputStream.buffered()).use { zos ->
                                        val svgBytes = XmpInjector.injectIntoSvg(baseBytes, metaTitle, metaDesc, keywordsList)
                                        zos.putNextEntry(java.util.zip.ZipEntry("$baseName/$baseName.svg"))
                                        zos.write(svgBytes)
                                        zos.closeEntry()

                                        val epsBytes = SvgToEpsConverter.convertSvgToEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)
                                        zos.putNextEntry(java.util.zip.ZipEntry("$baseName/$baseName.eps"))
                                        zos.write(epsBytes)
                                        zos.closeEntry()

                                        val rawJpg = SvgRenderer.renderSvgToHighResJpgBytes(context, baseBytes, targetLongEdge = 4000)
                                        if (rawJpg != null) {
                                            val jpgBytes = XmpInjector.injectIntoJpeg(rawJpg, metaTitle, metaDesc, keywordsList, metaCreator)
                                            zos.putNextEntry(java.util.zip.ZipEntry("$baseName/$baseName.jpg"))
                                            zos.write(jpgBytes)
                                            zos.closeEntry()
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val isEps = item.name.endsWith(".eps", ignoreCase = true)
                        val ext = if (dotIndex != -1) item.name.substring(dotIndex) else if (isEps) ".eps" else ".jpg"
                        val fileName = "$baseName$ext"
                        val mimeType = when {
                            ext.endsWith(".png", true) -> "image/png"
                            ext.endsWith(".eps", true) -> "application/postscript"
                            else -> "image/jpeg"
                        }
                        val finalBytes = if (isEps) {
                            item.injectedBytes ?: XmpInjector.injectIntoEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)
                        } else {
                            baseBytes
                        }
                        FileHelper.saveToDownloads(context, fileName, mimeType, finalBytes)
                    }
                }

                _toastFlow.value = "File berhasil disimpan ke folder Download/WarMachineHybrid"
            } catch (e: Exception) {
                e.printStackTrace()
                _toastFlow.value = "Gagal menyimpan file: ${e.message}"
            } finally {
                _isGlobalProcessing.value = false
                _globalProcessingText.value = ""
                _isDownloading.value = false
            }
        }
    }

    fun downloadInjectedFilesWithFormat(format: SvgExportFormat) {
        val selected = if (_imagesList.value.any { it.isSelected }) _imagesList.value.filter { it.isSelected } else _imagesList.value
        if (selected.isEmpty()) {
            _toastFlow.value = "Belum ada gambar yang di-import!"
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

                if (format == SvgExportFormat.ZIP_SVG_EPS_JPG) {
                    val masterZipName = "WarMachine_SVG_Bundle_${System.currentTimeMillis()}.zip"
                    withContext(Dispatchers.IO) {
                        FileHelper.saveStreamToDownloads(context, masterZipName, "application/zip") { outputStream ->
                            java.util.zip.ZipOutputStream(outputStream.buffered()).use { zos ->
                                for (item in selected) {
                                    try {
                                        _globalProcessingText.value = "Processing Zip...($completed/$total)"
                                        val baseBytes = item.injectedBytes ?: item.originalBytes ?: FileHelper.readBytesFromUri(context, item.uri)
                                        if (baseBytes != null) {
                                            val isSvg = item.name.endsWith(".svg", ignoreCase = true)
                                            val metaTitle = item.metadata?.title?.trim() ?: ""
                                            val metaDesc = item.metadata?.description?.trim() ?: ""
                                            val metaKeywordsStr = item.metadata?.keywords ?: ""
                                            val keywordsList = metaKeywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            val metaCreator = item.metadata?.creator?.trim() ?: ""

                                            val dotIndex = item.name.lastIndexOf('.')
                                            val baseNameRaw = if (dotIndex != -1) item.name.substring(0, dotIndex) else item.name

                                            var sanitizedTitle = ""
                                            if (metaTitle.isNotEmpty()) {
                                                sanitizedTitle = metaTitle.replace(Regex("[\\\\/:*?\"<>|]"), "").replace(Regex("\\s+"), "-").lowercase()
                                                if (sanitizedTitle.length > 50) sanitizedTitle = sanitizedTitle.substring(0, 50).trimEnd('-')
                                            }
                                            val baseName = if (sanitizedTitle.isNotEmpty()) sanitizedTitle else baseNameRaw

                                            var uniqueBaseName = baseName
                                            var counter = 1
                                            while (usedNames.contains(uniqueBaseName)) {
                                                uniqueBaseName = "$baseName-$counter"
                                                counter++
                                            }
                                            usedNames.add(uniqueBaseName)

                                            if (isSvg) {
                                                val svgBytes = XmpInjector.injectIntoSvg(baseBytes, metaTitle, metaDesc, keywordsList)
                                                zos.putNextEntry(java.util.zip.ZipEntry("$uniqueBaseName/$uniqueBaseName.svg"))
                                                zos.write(svgBytes)
                                                zos.closeEntry()

                                                val epsBytes = SvgToEpsConverter.convertSvgToEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)
                                                zos.putNextEntry(java.util.zip.ZipEntry("$uniqueBaseName/$uniqueBaseName.eps"))
                                                zos.write(epsBytes)
                                                zos.closeEntry()

                                                val rawJpg = SvgRenderer.renderSvgToHighResJpgBytes(context, baseBytes, targetLongEdge = 4000)
                                                if (rawJpg != null) {
                                                    val jpgBytes = XmpInjector.injectIntoJpeg(rawJpg, metaTitle, metaDesc, keywordsList, metaCreator)
                                                    zos.putNextEntry(java.util.zip.ZipEntry("$uniqueBaseName/$uniqueBaseName.jpg"))
                                                    zos.write(jpgBytes)
                                                    zos.closeEntry()
                                                }
                                            } else {
                                                val ext = if (dotIndex != -1) item.name.substring(dotIndex) else ".jpg"
                                                zos.putNextEntry(java.util.zip.ZipEntry("$uniqueBaseName$ext"))
                                                zos.write(baseBytes)
                                                zos.closeEntry()
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        t.printStackTrace()
                                    } finally {
                                        completed++
                                        System.gc()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    for (item in selected) {
                        try {
                            _globalProcessingText.value = "Processing Download...($completed/$total)"
                            val baseBytes = item.injectedBytes ?: item.originalBytes ?: FileHelper.readBytesFromUri(context, item.uri)
                            if (baseBytes != null) {
                                val isSvg = item.name.endsWith(".svg", ignoreCase = true)
                                val metaTitle = item.metadata?.title?.trim() ?: ""
                                val metaDesc = item.metadata?.description?.trim() ?: ""
                                val metaKeywordsStr = item.metadata?.keywords ?: ""
                                val keywordsList = metaKeywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val metaCreator = item.metadata?.creator?.trim() ?: ""

                                val dotIndex = item.name.lastIndexOf('.')
                                val baseNameRaw = if (dotIndex != -1) item.name.substring(0, dotIndex) else item.name

                                var sanitizedTitle = ""
                                if (metaTitle.isNotEmpty()) {
                                    sanitizedTitle = metaTitle.replace(Regex("[\\\\/:*?\"<>|]"), "").replace(Regex("\\s+"), "-").lowercase()
                                    if (sanitizedTitle.length > 50) sanitizedTitle = sanitizedTitle.substring(0, 50).trimEnd('-')
                                }
                                val baseName = if (sanitizedTitle.isNotEmpty()) sanitizedTitle else baseNameRaw

                                withContext(Dispatchers.IO) {
                                    if (isSvg) {
                                        if (format == SvgExportFormat.EPS) {
                                            val epsBytes = SvgToEpsConverter.convertSvgToEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)
                                            var uniqueName = "$baseName.eps"
                                            var counter = 1
                                            while (usedNames.contains(uniqueName)) {
                                                uniqueName = "$baseName-$counter.eps"
                                                counter++
                                            }
                                            usedNames.add(uniqueName)
                                            FileHelper.saveToDownloads(context, uniqueName, "application/postscript", epsBytes)
                                        } else {
                                            val svgBytes = XmpInjector.injectIntoSvg(baseBytes, metaTitle, metaDesc, keywordsList)
                                            var uniqueName = "$baseName.svg"
                                            var counter = 1
                                            while (usedNames.contains(uniqueName)) {
                                                uniqueName = "$baseName-$counter.svg"
                                                counter++
                                            }
                                            usedNames.add(uniqueName)
                                            FileHelper.saveToDownloads(context, uniqueName, "image/svg+xml", svgBytes)
                                        }
                                    } else {
                                        val isEps = item.name.endsWith(".eps", ignoreCase = true)
                                        val ext = if (dotIndex != -1) item.name.substring(dotIndex) else if (isEps) ".eps" else ".jpg"
                                        var uniqueName = "$baseName$ext"
                                        var counter = 1
                                        while (usedNames.contains(uniqueName)) {
                                            uniqueName = "$baseName-$counter$ext"
                                            counter++
                                        }
                                        usedNames.add(uniqueName)

                                        val mimeType = when {
                                            ext.endsWith(".png", true) -> "image/png"
                                            ext.endsWith(".eps", true) -> "application/postscript"
                                            else -> "image/jpeg"
                                        }
                                        val finalBytes = if (isEps) {
                                            item.injectedBytes ?: XmpInjector.injectIntoEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)
                                        } else {
                                            baseBytes
                                        }
                                        FileHelper.saveToDownloads(context, uniqueName, mimeType, finalBytes)
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        } finally {
                            completed++
                            System.gc()
                        }
                    }
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
                kotlinx.coroutines.delay(3000)
                _downloadStatusText.value = ""
                _isDownloading.value = false
            }
        }
    }

    // --- ZIP and Download ---
    fun downloadInjectedFiles() {
        showSvgExportDialogForBulk()
    }

    fun downloadIndividualFile(id: Int) {
        showSvgExportDialogForIndividual(id)
    }
}

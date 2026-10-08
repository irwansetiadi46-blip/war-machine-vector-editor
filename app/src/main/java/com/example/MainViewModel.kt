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
import java.util.Locale

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

enum class ProcessStatus {
    IDLE,
    WAITING,
    PROCESSING,
    SUCCESS,
    FAILED
}

data class ImageItem(
    val id: Int,
    val name: String,
    val uri: Uri,
    val originalBytes: ByteArray?,
    val injectedBytes: ByteArray?,
    val hasMetadata: Boolean,
    val isSelected: Boolean = false,
    val metadata: XmpData?,
    val individualFileName: String = "",
    val individualTitle: String = "",
    val individualDescription: String = "",
    val individualKeywords: String = "",
    val individualKeywordItems: List<KeywordItem> = emptyList(),
    val individualCreator: String = "",
    val isGeneratingMetadata: Boolean = false,
    val isInjectingIndividual: Boolean = false,
    val previewUri: Uri? = null,
    val previewBytes: ByteArray? = null,
    val processStatus: ProcessStatus = ProcessStatus.IDLE
) {
    val isGenerated: Boolean
        get() = individualFileName.isNotBlank() || individualTitle.isNotBlank() || individualKeywords.isNotBlank() || individualDescription.isNotBlank() || hasMetadata

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
    val fileName: String? = null,
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
    private var nextId = 1

    private val geminiResponseSchema: Map<String, Any> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "file_name" to mapOf("type" to "STRING"),
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
        "required" to listOf("file_name", "title", "description", "keywords")
    )

    // --- State Variables ---
    private val _imagesList = MutableStateFlow<List<ImageItem>>(emptyList())
    val imagesList = _imagesList.asStateFlow()

    private val _selectionMode = MutableStateFlow(SelectionMode.MULTI)
    val selectionMode = _selectionMode.asStateFlow()

    private val _fileName = MutableStateFlow("")
    val fileName = _fileName.asStateFlow()

    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _keywords = MutableStateFlow("")
    val keywords = _keywords.asStateFlow()

    private val _creator = MutableStateFlow("")
    val creator = _creator.asStateFlow()

    fun sanitizeFileName(input: String): String {
        val clean = input.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("[\\s_]+"), "-")
            .trim('-')
            .lowercase(Locale.ROOT)
        val words = clean.split("-").filter { it.isNotBlank() }
        return if (words.size > 5) words.take(5).joinToString("-") else clean
    }

    fun getEffectiveBaseName(item: ImageItem): String {
        val customFileName = item.individualFileName.trim()
        val dotIndex = item.name.lastIndexOf('.')
        val baseNameRaw = if (dotIndex != -1) item.name.substring(0, dotIndex) else item.name

        if (customFileName.isNotBlank()) {
            val sanitized = sanitizeFileName(customFileName)
            if (sanitized.isNotEmpty()) return sanitized
        }
        return baseNameRaw
    }

    fun updateFileName(name: String) {
        _fileName.value = name
    }

    fun updateIndividualFileName(id: Int, fileName: String) {
        _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(individualFileName = fileName) else it }
    }

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
    private var currentBatchProcessingId: Int? = null

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

    private val _isTouchEffectEnabled = MutableStateFlow(true)
    val isTouchEffectEnabled = _isTouchEffectEnabled.asStateFlow()

    private val _selectedTouchEffect = MutableStateFlow("Glowing Ring")
    val selectedTouchEffect = _selectedTouchEffect.asStateFlow()

    private val _toastFlow = MutableStateFlow<String?>(null)
    val toastFlow = _toastFlow.asStateFlow()

    init {
        loadApiKeys()
    }

    fun setOfflineMode(enabled: Boolean) {
        _isOfflineMode.value = enabled
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_offline_mode", enabled).apply()
    }

    fun generateKeywordsOffline() {
        val concept = _promptConcept.value
        if (concept.isBlank()) {
            _toastFlow.value = "Need Concept"
            return
        }

        _isGeneratingAi.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(300)

                val tokens = concept.split(Regex("[^a-zA-Z0-9]+"))
                    .map { it.trim().lowercase(Locale.US) }
                    .filter { it.length > 2 }
                    .distinct()

                if (tokens.isEmpty()) {
                    _toastFlow.value = "Need Details"
                    return@launch
                }

                val resultString = tokens.joinToString(",")

                _keywords.value = resultString
                _title.value = ""
                _description.value = ""
                _toastFlow.value = "Generated"

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
                _toastFlow.value = "AI Error"
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

        // Auto-load Touch Effect preferences (Default ON, Glowing Ring)
        _isTouchEffectEnabled.value = prefs.getBoolean("is_touch_effect_enabled", true)
        _selectedTouchEffect.value = prefs.getString("selected_touch_effect", "Glowing Ring") ?: "Glowing Ring"
    }

    fun setTouchEffectEnabled(enabled: Boolean) {
        _isTouchEffectEnabled.value = enabled
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_touch_effect_enabled", enabled).apply()
    }

    fun setSelectedTouchEffect(effect: String) {
        _selectedTouchEffect.value = effect
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_touch_effect", effect).apply()
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
        _toastFlow.value = "API Saved"
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
        _toastFlow.value = "Keywords Saved"
    }

    fun clearPromptConceptPermanent() {
        _promptConcept.value = ""
        _savedPromptConcept.value = ""
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("saved_prompt_concept").apply()
        _toastFlow.value = "Cleared"
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
        _toastFlow.value = "Blacklist Saved"
    }

    fun clearBlacklistWordsPermanent() {
        _blacklistWords.value = ""
        _savedBlacklistWords.value = ""
        val prefs = context.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("saved_blacklist_words").apply()
        _toastFlow.value = "Cleared"
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
        _toastFlow.value = "Removed"
    }

    fun removeIndividualImage(id: Int) {
        val itemToRemove = _imagesList.value.find { it.id == id }
        val remaining = _imagesList.value.filter { it.id != id }
        _imagesList.value = remaining
        recalculateMetadataFormFromSelection()
        
        // Clean cached preview file if any
        itemToRemove?.previewUri?.path?.let { path ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val file = java.io.File(path)
                    if (file.exists() && file.parentFile == context.cacheDir) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
        }
        _toastFlow.value = "Removed"
    }

    fun clearAllImages() {
        // Cancel all ongoing jobs
        globalGenerationJob?.cancel()
        currentBatchProcessingId = null
        individualGenerationJobs.values.forEach { it.cancel() }
        individualGenerationJobs.clear()
        _isGeneratingAi.value = false
        _isGlobalProcessing.value = false
        _globalProcessingText.value = ""

        // Reset list and form metadata
        _imagesList.value = emptyList()
        _title.value = ""
        _description.value = ""
        _keywords.value = ""
        recalculateMetadataFormFromSelection()

        // Clean temp preview files and Coil image disk/memory cache (preserve offline keyword database)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir?.listFiles()?.forEach { file ->
                    if (file.name.startsWith("preview_svg_") || file.name.startsWith("preview_eps_")) {
                        try { file.deleteRecursively() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            } catch (_: Exception) {}
            System.gc()
        }

        _toastFlow.value = "Cleared"
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
        currentBatchProcessingId = null
        _isGeneratingAi.value = false
        _isGlobalProcessing.value = false
        _globalProcessingText.value = ""
        _imagesList.value = _imagesList.value.map {
            when (it.processStatus) {
                ProcessStatus.PROCESSING -> it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.FAILED)
                ProcessStatus.WAITING -> it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.IDLE)
                else -> it.copy(isGeneratingMetadata = false)
            }
        }
        _toastFlow.value = "Canceled"
    }

    fun cancelIndividualGeneration(id: Int) {
        individualGenerationJobs[id]?.cancel()
        individualGenerationJobs.remove(id)
        if (currentBatchProcessingId == id) {
            globalGenerationJob?.cancel()
            currentBatchProcessingId = null
            _isGeneratingAi.value = false
            _isGlobalProcessing.value = false
            _globalProcessingText.value = ""
            _imagesList.value = _imagesList.value.map {
                if (it.id == id) it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.FAILED)
                else if (it.processStatus == ProcessStatus.WAITING) it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.IDLE)
                else it.copy(isGeneratingMetadata = false)
            }
            _toastFlow.value = "Canceled"
            return
        }
        _imagesList.value = _imagesList.value.map {
            if (it.id == id) {
                val newStatus = if (it.processStatus == ProcessStatus.WAITING) ProcessStatus.IDLE else ProcessStatus.FAILED
                it.copy(isGeneratingMetadata = false, processStatus = newStatus)
            } else it
        }
        _toastFlow.value = "Canceled"
    }

    fun showToast(message: String) {
        _toastFlow.value = message
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
                _toastFlow.value = "Need Online"
            }
            return
        }

        if (_isOfflineMode.value) {
            generateKeywordsOffline()
            return
        }
        val concept = _promptConcept.value
        if (concept.isBlank()) {
            _toastFlow.value = "Need Concept"
            return
        }

        val apiKey = _geminiKey.value

        if (apiKey.isBlank()) {
            _toastFlow.value = "Need API Key"
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
                    _toastFlow.value = "Generated"

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
                    _toastFlow.value = "Invalid AI"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _toastFlow.value = "AI Error"
            } finally {
                _isGeneratingAi.value = false
            }
        }
    }

    fun generateMetadataForSingleImage(id: Int) {
        if (_isOfflineMode.value) {
            _toastFlow.value = "Need Online"
            return
        }
        val apiKey = _geminiKey.value
        if (apiKey.isBlank()) {
            _toastFlow.value = "Need API Key"
            return
        }
        val imageItem = _imagesList.value.find { it.id == id } ?: return

        val job = viewModelScope.launch {
            _imagesList.value = _imagesList.value.map { 
                if (it.id == imageItem.id) it.copy(isGeneratingMetadata = true, processStatus = ProcessStatus.PROCESSING) else it 
            }
            try {
                val parsed = performGeminiAnalysis(imageItem, apiKey, _selectedModel.value)
                if (parsed != null) {
                    val kwsList = parsed.keywords ?: emptyList()
                    val kwsString = kwsList.joinToString(",") { 
                        if (it.isTrademark && !it.replacement.isNullOrBlank()) it.replacement else it.word 
                    }

                    _fileName.value = parsed.fileName ?: ""
                    _title.value = parsed.title ?: ""
                    _description.value = parsed.description ?: ""
                    _keywords.value = kwsString

                    _imagesList.value = _imagesList.value.map { 
                        if (it.id == imageItem.id) it.copy(
                            individualFileName = parsed.fileName ?: "",
                            individualTitle = parsed.title ?: "",
                            individualDescription = parsed.description ?: "",
                            individualKeywords = kwsString,
                            individualKeywordItems = kwsList,
                            isGeneratingMetadata = false,
                            processStatus = ProcessStatus.SUCCESS
                        ) else it
                    }
                    _toastFlow.value = "Generated"
                    if (_isAutoInjectionEnabled.value) {
                        injectIndividualMetadata(imageItem.id)
                    }
                } else {
                    _imagesList.value = _imagesList.value.map { 
                        if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.FAILED) else it 
                    }
                    _toastFlow.value = "Invalid AI"
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                    _imagesList.value = _imagesList.value.map { 
                        if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.FAILED) else it 
                    }
                    _toastFlow.value = "AI Error"
                } else {
                    _imagesList.value = _imagesList.value.map { 
                        if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.FAILED) else it 
                    }
                }
            } finally {
                individualGenerationJobs.remove(imageItem.id)
            }
        }
        individualGenerationJobs[imageItem.id] = job
    }

    fun generateMetadataForAllImages() {
        if (_isOfflineMode.value) {
            _toastFlow.value = "Need Online"
            return
        }

        val allImages = _imagesList.value
        if (allImages.isEmpty()) {
            _toastFlow.value = "No Images"
            return
        }

        val apiKey = _geminiKey.value
        if (apiKey.isBlank()) {
            _toastFlow.value = "Need API Key"
            return
        }

        // Target failed or waiting items first if any, or non-generated, or all
        val targetImages = if (allImages.any { it.processStatus == ProcessStatus.FAILED || it.processStatus == ProcessStatus.WAITING }) {
            allImages.filter { it.processStatus != ProcessStatus.SUCCESS }
        } else if (allImages.any { !it.isGenerated }) {
            allImages.filter { !it.isGenerated }
        } else {
            allImages
        }

        if (targetImages.isEmpty()) {
            _toastFlow.value = "Generated"
            return
        }

        val targetIds = targetImages.map { it.id }.toSet()

        // Set queued target images to WAITING status
        _imagesList.value = _imagesList.value.map {
            if (it.id in targetIds) it.copy(processStatus = ProcessStatus.WAITING, isGeneratingMetadata = false) else it
        }

        globalGenerationJob = viewModelScope.launch {
            _isGeneratingAi.value = true
            _isGlobalProcessing.value = true
            try {
                val total = targetImages.size
                var completed = 0
                for (imageItem in targetImages) {
                    currentBatchProcessingId = imageItem.id
                    _globalProcessingText.value = "Generating Process...($completed/$total)"

                    _imagesList.value = _imagesList.value.map { 
                        if (it.id == imageItem.id) it.copy(isGeneratingMetadata = true, processStatus = ProcessStatus.PROCESSING) else it 
                    }
                    
                    try {
                        val parsed = performGeminiAnalysis(imageItem, apiKey, _selectedModel.value)
                        if (parsed != null) {
                            val kwsList = parsed.keywords ?: emptyList()
                            val kwsString = kwsList.joinToString(",") { 
                                if (it.isTrademark && !it.replacement.isNullOrBlank()) it.replacement else it.word 
                            }

                            if (targetImages.size == 1) {
                                _fileName.value = parsed.fileName ?: ""
                                _title.value = parsed.title ?: ""
                                _description.value = parsed.description ?: ""
                                _keywords.value = kwsString
                            }
                            
                            _imagesList.value = _imagesList.value.map { 
                                if (it.id == imageItem.id) it.copy(
                                    individualFileName = parsed.fileName ?: "",
                                    individualTitle = parsed.title ?: "",
                                    individualDescription = parsed.description ?: "",
                                    individualKeywords = kwsString,
                                    individualKeywordItems = kwsList,
                                    isGeneratingMetadata = false,
                                    processStatus = ProcessStatus.SUCCESS
                                ) else it
                            }

                            // Langsung Auto Inject Metadata jika fitur auto injection ON
                            if (_isAutoInjectionEnabled.value) {
                                injectIndividualMetadata(imageItem.id)
                            }
                        } else {
                            _imagesList.value = _imagesList.value.map { 
                                if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.FAILED) else it 
                            }
                            _toastFlow.value = "Invalid AI"
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            _imagesList.value = _imagesList.value.map { 
                                if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.FAILED) else it 
                            }
                            throw e
                        } else {
                            e.printStackTrace()
                            _imagesList.value = _imagesList.value.map { 
                                if (it.id == imageItem.id) it.copy(isGeneratingMetadata = false, processStatus = ProcessStatus.FAILED) else it 
                            }
                            _toastFlow.value = "AI Error"
                        }
                    }
                    completed++
                    _globalProcessingText.value = "Generating Process...($completed/$total)"
                }
                
                _toastFlow.value = "Generated"
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                    _toastFlow.value = "AI Error"
                }
            } finally {
                currentBatchProcessingId = null
                _isGeneratingAi.value = false
                _isGlobalProcessing.value = false
                _globalProcessingText.value = ""
                // Revert any leftover WAITING back to IDLE
                _imagesList.value = _imagesList.value.map {
                    if (it.processStatus == ProcessStatus.WAITING) it.copy(processStatus = ProcessStatus.IDLE)
                    else it.copy(isGeneratingMetadata = false)
                }
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
        val blacklistInstruction = if (blWords.isNotBlank()) "8. BLACKLIST WORDS: DO NOT include any of these words: $blWords." else ""

        val name = imageItem.name.lowercase()
        val systemPrompt = """
            You are an expert Microstock SEO Specialist. Your job is to analyze the provided image/asset and generate highly accurate metadata (File Name, Title, Description, and Keywords with popularity & trademark detection) in structured JSON format. Don't Use - or _ and odd symbols in title/desc.

            Strictly follow these rules:
            1. Language: Always output File Name, Title, Description, and Keywords in English.
            2. File Name: Generate a natural, concise microstock File Name describing the visual theme/subject.
               - Maximum 5 words (strictly 1 to 5 words, do NOT exceed 5 words).
               - Only plain words separated by single spaces (no symbols, no dashes, no underscores, no file extension).
               - Example: "vintage coffee badge vector" or "minimalist business card template".
            3. Title max until $titleLimit characters (100-200). 
            4. Description must be Maximum $descLimit characters a dynamic combination of concept description and organic visual multi usage targets and suitable for what.
            5. Keywords Quantity: Generate exactly $kwLimit high-quality keywords. Quality and relevance are prioritized over quantity.
            6. Keywords Formatting & Demand Score (ImStocker-Style): 
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
            7. Content Relevance: 
               - No keyword spamming or redundant root words. 
               - Avoid contradictory terms.
               - Use only 1 word for each keyword.
            $blacklistInstruction

            Format output strictly matching this schema:
            {"file_name": "...", "title": "...", "description": "...", "keywords": [{"word": "...", "demandScore": 85, "isTrademark": false, "replacement": null}]}
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
                Generate highly accurate, professional microstock metadata (File Name max 5 words, Title max until $titleLimit characters, Description, and Keywords) that perfectly describes the visual subject, vector style, theme, color scheme, and microstock utility of this asset.
                
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
                Analyze this image and generate highly accurate, professional microstock metadata (File Name max 5 words, Title, Description, and Keywords) in JSON format according to system rules.
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
                    Generate professional microstock metadata (File Name max 5 words, Title max until $titleLimit characters, Description, and Keywords) that is perfectly accurate and highly relevant to the actual design, utility, visual themes, and colors of this vector asset.
                    
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
                    Analyze this SVG vector file code. Inspect metadata tags, labels, class names, path details, coordinates, and color properties inside the vector content. Deducing what visual concept, template style, interface mock, or illustrative graphic is defined in this vector, generate professional microstock metadata (File Name max 5 words, Title, Description, and Keywords).
                    
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
                Generate highly accurate, professional microstock metadata (File Name max 5 words, Title, Description, and Keywords) that is perfectly relevant to the actual design, theme, and utility of the graphic.
                
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
                Analyze this image and generate professional microstock metadata (File Name max 5 words, Title, Description, and Keywords) in JSON format according to system rules.
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
            val rawFileName = when {
                root.has("file_name") && !root.get("file_name").isJsonNull -> root.get("file_name").asString
                root.has("fileName") && !root.get("fileName").isJsonNull -> root.get("fileName").asString
                root.has("filename") && !root.get("filename").isJsonNull -> root.get("filename").asString
                else -> ""
            }
            val fileNameWords = rawFileName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val fileName = if (fileNameWords.size > 5) fileNameWords.take(5).joinToString(" ") else rawFileName.trim()

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
            GeneratedMetadata(fileName = fileName, title = title, description = description, keywords = keywordList)
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

    fun clearKeywordsFromImage(imageId: Int) {
        _imagesList.value = _imagesList.value.map { item ->
            if (item.id == imageId) {
                item.copy(individualKeywordItems = emptyList(), individualKeywords = "")
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
                individualFileName = "",
                individualTitle = "", 
                individualDescription = "", 
                individualKeywords = "", 
                individualKeywordItems = emptyList(), 
                individualCreator = "",
                processStatus = ProcessStatus.IDLE
            ) else it 
        }
    }

    fun injectIndividualMetadata(id: Int) {
        val item = _imagesList.value.find { it.id == id } ?: return
        
        val metaFileName = item.individualFileName.trim()
        val metaTitle = item.individualTitle
        val metaDesc = item.individualDescription
        val metaKeywordsString = item.individualKeywords
        val metaCreator = item.individualCreator

        if (metaFileName.isBlank() && metaTitle.isBlank() && metaDesc.isBlank() && metaKeywordsString.isBlank() && metaCreator.isBlank()) {
            _toastFlow.value = "Metadata Empty"
            return
        }

        val keywordsList = if (metaKeywordsString.isNotBlank()) {
            metaKeywordsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        val ext = if (item.name.contains('.')) item.name.substring(item.name.lastIndexOf('.')) else ""
        val sanitizedBase = if (metaFileName.isNotBlank()) sanitizeFileName(metaFileName) else ""
        val newFileName = if (sanitizedBase.isNotEmpty()) "$sanitizedBase$ext" else item.name

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
                            name = newFileName,
                            injectedBytes = injectedBytes,
                            hasMetadata = true,
                            metadata = newestXmpData,
                            isInjectingIndividual = false
                        ) else it 
                    }
                    _toastFlow.value = "Injected"
                } else {
                    _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(isInjectingIndividual = false) else it }
                    _toastFlow.value = "File Error"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _imagesList.value = _imagesList.value.map { if (it.id == id) it.copy(isInjectingIndividual = false) else it }
                _toastFlow.value = "Inject Failed"
            }
        }
    }

    fun injectAllIndividualMetadata() {
        val selected = if (_imagesList.value.any { it.isSelected }) _imagesList.value.filter { it.isSelected } else _imagesList.value
        if (selected.isEmpty()) {
            _toastFlow.value = "No Images"
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
                    val metaFileName = item.individualFileName.trim()
                    val metaTitle = item.individualTitle
                    val metaDesc = item.individualDescription
                    val metaKeywordsString = item.individualKeywords
                    val metaCreator = item.individualCreator
                    
                    if (metaFileName.isBlank() && metaTitle.isBlank() && metaDesc.isBlank() && metaKeywordsString.isBlank() && metaCreator.isBlank()) {
                        continue // Skip empty ones
                    }

                    val keywordsList = if (metaKeywordsString.isNotBlank()) {
                        metaKeywordsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        emptyList()
                    }

                    val ext = if (item.name.contains('.')) item.name.substring(item.name.lastIndexOf('.')) else ""
                    val sanitizedBase = if (metaFileName.isNotBlank()) sanitizeFileName(metaFileName) else ""
                    val newFileName = if (sanitizedBase.isNotEmpty()) "$sanitizedBase$ext" else item.name

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
                                    name = newFileName,
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
                _toastFlow.value = "Inject Failed"
            } finally {
                _isInjecting.value = false
            }
        }
    }

    fun injectMetadata() {
        val selected = _imagesList.value.filter { it.isSelected }
        if (selected.isEmpty()) {
            _toastFlow.value = "Select Image"
            return
        }

        val metaFileName = _fileName.value.trim()
        val metaTitle = _title.value
        val metaDesc = _description.value
        val metaKeywordsString = _keywords.value
        val metaCreator = _creator.value

        if (metaFileName.isBlank() && metaTitle.isBlank() && metaDesc.isBlank() && metaKeywordsString.isBlank() && metaCreator.isBlank()) {
            _toastFlow.value = "Metadata Empty"
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
                    val ext = if (item.name.contains('.')) item.name.substring(item.name.lastIndexOf('.')) else ""
                    val sanitizedBase = if (metaFileName.isNotBlank()) sanitizeFileName(metaFileName) else ""
                    val newFileName = if (sanitizedBase.isNotEmpty()) {
                        if (selected.size > 1) "$sanitizedBase-${i + 1}$ext" else "$sanitizedBase$ext"
                    } else item.name

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
                                    name = newFileName,
                                    individualFileName = if (metaFileName.isNotBlank()) metaFileName else updatedList[indexInMaster].individualFileName,
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

                _imagesList.value = updatedList
                _injectionStatusText.value = "INJECTION 100% DONE"
                _toastFlow.value = "Injected"
                recalculateMetadataFormFromSelection()
            } catch (e: Exception) {
                e.printStackTrace()
                _injectionStatusText.value = "INJECTION FAILED"
                _toastFlow.value = "Inject Failed"
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
            _toastFlow.value = "No Images"
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
            _isDownloading.value = true
            _isGlobalProcessing.value = true
            _globalProcessingText.value = "Processing Export..."

            try {
                val baseBytes = item.injectedBytes ?: item.originalBytes ?: FileHelper.readBytesFromUri(context, item.uri)
                if (baseBytes == null) {
                    _toastFlow.value = "File Error"
                    return@launch
                }

                val metaTitle = item.metadata?.title?.trim() ?: ""
                val metaDesc = item.metadata?.description?.trim() ?: ""
                val metaKeywordsStr = item.metadata?.keywords ?: ""
                val keywordsList = metaKeywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val metaCreator = item.metadata?.creator?.trim() ?: ""

                val dotIndex = item.name.lastIndexOf('.')
                val baseName = getEffectiveBaseName(item)

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
                                        val epsBytes = SvgToEpsConverter.convertSvgToEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)

                                        // 1. EPS inside "EPS file" folder
                                        zos.putNextEntry(java.util.zip.ZipEntry("EPS file/$baseName.eps"))
                                        zos.write(epsBytes)
                                        zos.closeEntry()

                                        // 2. EPS directly outside folder
                                        zos.putNextEntry(java.util.zip.ZipEntry("$baseName.eps"))
                                        zos.write(epsBytes)
                                        zos.closeEntry()

                                        // 3. High resolution JPG preview outside folder with injected metadata
                                        val rawJpg = SvgRenderer.renderSvgToHighResJpgBytes(context, baseBytes, targetLongEdge = 4000)
                                        if (rawJpg != null) {
                                            val jpgBytes = XmpInjector.injectIntoJpeg(rawJpg, metaTitle, metaDesc, keywordsList, metaCreator)
                                            zos.putNextEntry(java.util.zip.ZipEntry("$baseName.jpg"))
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

                _toastFlow.value = "File Saved"
            } catch (e: Exception) {
                e.printStackTrace()
                _toastFlow.value = "Save Failed"
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
            _toastFlow.value = "No Images"
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
                    try {
                        _globalProcessingText.value = "Processing Download...(${completed + 1}/$total)"
                        val baseBytes = item.injectedBytes ?: item.originalBytes ?: FileHelper.readBytesFromUri(context, item.uri)
                        if (baseBytes != null) {
                            val isSvg = item.name.endsWith(".svg", ignoreCase = true)
                            val metaTitle = item.metadata?.title?.trim() ?: ""
                            val metaDesc = item.metadata?.description?.trim() ?: ""
                            val metaKeywordsStr = item.metadata?.keywords ?: ""
                            val keywordsList = metaKeywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val metaCreator = item.metadata?.creator?.trim() ?: ""

                            val dotIndex = item.name.lastIndexOf('.')
                            val baseName = getEffectiveBaseName(item)

                            var uniqueBaseName = baseName
                            var counter = 1
                            while (usedNames.contains(uniqueBaseName)) {
                                uniqueBaseName = "$baseName-$counter"
                                counter++
                            }
                            usedNames.add(uniqueBaseName)

                            withContext(Dispatchers.IO) {
                                if (format == SvgExportFormat.ZIP_SVG_EPS_JPG) {
                                    if (isSvg) {
                                        val zipName = "${uniqueBaseName}_bundle.zip"
                                        FileHelper.saveStreamToDownloads(context, zipName, "application/zip") { outputStream ->
                                            java.util.zip.ZipOutputStream(outputStream.buffered()).use { zos ->
                                                val epsBytes = SvgToEpsConverter.convertSvgToEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)

                                                // 1. EPS inside "EPS file" folder
                                                zos.putNextEntry(java.util.zip.ZipEntry("EPS file/$uniqueBaseName.eps"))
                                                zos.write(epsBytes)
                                                zos.closeEntry()

                                                // 2. EPS directly outside folder
                                                zos.putNextEntry(java.util.zip.ZipEntry("$uniqueBaseName.eps"))
                                                zos.write(epsBytes)
                                                zos.closeEntry()

                                                // 3. High resolution JPG preview outside folder with injected metadata
                                                val rawJpg = SvgRenderer.renderSvgToHighResJpgBytes(context, baseBytes, targetLongEdge = 4000)
                                                if (rawJpg != null) {
                                                    val jpgBytes = XmpInjector.injectIntoJpeg(rawJpg, metaTitle, metaDesc, keywordsList, metaCreator)
                                                    zos.putNextEntry(java.util.zip.ZipEntry("$uniqueBaseName.jpg"))
                                                    zos.write(jpgBytes)
                                                    zos.closeEntry()
                                                }
                                            }
                                        }
                                    } else {
                                        val ext = if (dotIndex != -1) item.name.substring(dotIndex) else ".jpg"
                                        val mimeType = when {
                                            ext.endsWith(".png", true) -> "image/png"
                                            ext.endsWith(".eps", true) -> "application/postscript"
                                            else -> "image/jpeg"
                                        }
                                        FileHelper.saveToDownloads(context, "$uniqueBaseName$ext", mimeType, baseBytes)
                                    }
                                } else if (isSvg) {
                                    if (format == SvgExportFormat.EPS) {
                                        val epsBytes = SvgToEpsConverter.convertSvgToEps(baseBytes, metaTitle, metaDesc, keywordsList, metaCreator)
                                        FileHelper.saveToDownloads(context, "$uniqueBaseName.eps", "application/postscript", epsBytes)
                                    } else {
                                        val svgBytes = XmpInjector.injectIntoSvg(baseBytes, metaTitle, metaDesc, keywordsList)
                                        FileHelper.saveToDownloads(context, "$uniqueBaseName.svg", "image/svg+xml", svgBytes)
                                    }
                                } else {
                                    val isEps = item.name.endsWith(".eps", ignoreCase = true)
                                    val ext = if (dotIndex != -1) item.name.substring(dotIndex) else if (isEps) ".eps" else ".jpg"
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
                                    FileHelper.saveToDownloads(context, "$uniqueBaseName$ext", mimeType, finalBytes)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        completed++
                        System.gc()
                    }
                }

                _downloadStatusText.value = "DOWNLOAD COMPLETE ($total files)"
                _toastFlow.value = "Download Done"
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadStatusText.value = "DOWNLOAD FAILED"
                _toastFlow.value = "Download Failed"
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

    override fun onCleared() {
        super.onCleared()
        // Clean temporary preview files only when ViewModel is completely destroyed (App closed from background)
        try {
            context.cacheDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("preview_svg_") || file.name.startsWith("preview_eps_")) {
                    try { file.delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }
}

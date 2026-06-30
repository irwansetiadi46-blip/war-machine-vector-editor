package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF070E20)
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    
    // --- State Observables ---
    val imagesList by viewModel.imagesList.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    val creator by viewModel.creator.collectAsStateWithLifecycle()
    
    val groqKey by viewModel.groqKey.collectAsStateWithLifecycle()
    val geminiKey by viewModel.geminiKey.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val promptConcept by viewModel.promptConcept.collectAsStateWithLifecycle()
    val isOfflineMode by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    
    val isGeneratingAi by viewModel.isGeneratingAi.collectAsStateWithLifecycle()
    val isInjecting by viewModel.isInjecting.collectAsStateWithLifecycle()
    val injectionProgress by viewModel.injectionProgress.collectAsStateWithLifecycle()
    val injectionStatusText by viewModel.injectionStatusText.collectAsStateWithLifecycle()
    val downloadStatusText by viewModel.downloadStatusText.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastFlow.collectAsStateWithLifecycle()
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val titleCharLimit by viewModel.titleCharLimit.collectAsStateWithLifecycle()
    val descCharLimit by viewModel.descCharLimit.collectAsStateWithLifecycle()
    val keywordsLimit by viewModel.keywordsLimit.collectAsStateWithLifecycle()
    val blacklistWords by viewModel.blacklistWords.collectAsStateWithLifecycle()

    // Key states for API input logic
    var tempGroqKey by remember { mutableStateOf(groqKey) }
    var tempGeminiKey by remember { mutableStateOf(geminiKey) }
    var apiInputsInitialized by remember { mutableStateOf(false) }

    // Initialize temp keys once saved ones load from SharedPreferences
    LaunchedEffect(groqKey, geminiKey) {
        if (!apiInputsInitialized && (groqKey.isNotEmpty() || geminiKey.isNotEmpty())) {
            tempGroqKey = groqKey
            tempGeminiKey = geminiKey
            apiInputsInitialized = true
        }
    }

    // Toast Listener
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    val scope = rememberCoroutineScope()

    val offlineResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selected = result.data?.getStringArrayListExtra("selected_keywords")
            if (selected != null) {
                viewModel.setKeywords(selected.joinToString(","))
                viewModel.setTitle("")
                viewModel.setDescription("")
            }
        }
    }

    // Photo Picker Contract
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    // Permission check for storage on older APIs
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickerLauncher.launch("*/*")
        } else {
            Toast.makeText(context, "Izin penyimpanan dibutuhkan untuk memilih berkas.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestAndPickImages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pickerLauncher.launch("*/*")
        } else {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                pickerLauncher.launch("*/*")
            } else {
                permissionLauncher.launch(permission)
            }
        }
    }

    val mainScrollState = rememberScrollState()
    val isPointingDown by remember { derivedStateOf { mainScrollState.value < (mainScrollState.maxValue / 2) } }

    Box(modifier = modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070E20))
                .verticalScroll(mainScrollState)
        ) {
        // 1. --- STYLISH BANNER HEADER (Orange `#f25c05` Background) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF25C05))
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFF099),
                                Color(0xFFE5A93B),
                                Color(0xFFFFD700),
                                Color(0xFFF3E5AB),
                                Color(0xFFD4AF37)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("premium_label")
            ) {
                Text(
                    text = "★ PREMIUM ★",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "WAR MACHINE HYBRID",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Inject Metadata + Auto Metadata",
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }

        // --- Inner Container with balanced spacing ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 4. --- CARD AUTO METADATA (Background White, Border Orange `#f25c05`) ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color(0xFFF25C05)), RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "AUTO METADATA AI",
                        color = Color(0xFFF25C05),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Model Selection Select Row
                    val aiModels = listOf(
                        Triple("Gemini", "gemini-3.5-flash", "Gemini 3.5 Flash"),
                        Triple("Gemini", "gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite"),
                        Triple("Gemini", "gemini-3.1-pro", "Gemini 3.1 Pro"),
                        Triple("Gemini", "gemini-2.5-flash", "Gemini 2.5 Flash"),
                        Triple("Gemini", "gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
                        Triple("Groq", "llama-3.3-70b-versatile", "Groq Llama-3.3")
                    )
                    
                    var expanded by remember { mutableStateOf(false) }
                    val currentModelLabel = aiModels.find { it.first == selectedProvider && it.second == selectedModel }?.third ?: selectedModel

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Model Aktif:",
                                color = Color(0xFF4B5563),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentModelLabel,
                                color = Color(0xFF1F2937),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        Box {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF25C05)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Ganti Model", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                aiModels.forEach { (provider, model, label) ->
                                    val isSelected = selectedProvider == provider && selectedModel == model
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = null,
                                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFF25C05))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = label,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateDefaultModel(provider)
                                            viewModel.setSelectedModel(model)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // API Key Input Status Field
                    val activeInFocusKey = if (selectedProvider == "Gemini") tempGeminiKey else tempGroqKey
                    var keyVisibility by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = activeInFocusKey,
                        onValueChange = {
                            if (selectedProvider == "Gemini") {
                                tempGeminiKey = it
                            } else {
                                tempGroqKey = it
                            }
                        },
                        enabled = !isOfflineMode,
                        label = { Text("API Key $selectedProvider") },
                        placeholder = { Text("Masukkan API Key Anda...") },
                        singleLine = true,
                        visualTransformation = if (keyVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            if (isOfflineMode) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked in Offline Mode",
                                    tint = Color(0xFFF25C05),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Row {
                                    if (activeInFocusKey.isNotEmpty()) {
                                        IconButton(onClick = {
                                            if (selectedProvider == "Gemini") {
                                                tempGeminiKey = ""
                                            } else {
                                                tempGroqKey = ""
                                            }
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear Key", tint = Color.Gray)
                                        }
                                    }
                                    IconButton(onClick = { keyVisibility = !keyVisibility }) {
                                        Icon(
                                            imageVector = if (keyVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Visibility",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedLabelColor = Color(0xFFF25C05),
                            unfocusedLabelColor = Color(0xFF4B5563),
                            focusedBorderColor = Color(0xFFF25C05),
                            unfocusedBorderColor = Color(0xFFCCCCCC),
                            disabledTextColor = Color(0xFF9CA3AF),
                            disabledBorderColor = Color(0xFFE5E7EB),
                            disabledLabelColor = Color(0xFF9CA3AF)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val savedKeyForProvider = if (selectedProvider == "Gemini") geminiKey else groqKey
                    val (apiBtnBg, apiBtnText) = when {
                        isOfflineMode -> {
                            Color(0xFF6C757D) to "DISABLE"
                        }
                        activeInFocusKey.isEmpty() -> {
                            Color(0xFF6C757D) to "INPUT API"
                        }
                        activeInFocusKey != savedKeyForProvider -> {
                            Color(0xFF22C55E) to "SAVE API"
                        }
                        else -> {
                            Color(0xFF00A8FF) to "ACTIVE"
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveApiKeys(
                                groq = tempGroqKey,
                                gemini = tempGeminiKey,
                                provider = selectedProvider
                            )
                        },
                        enabled = !isOfflineMode && activeInFocusKey.isNotEmpty() && activeInFocusKey != savedKeyForProvider,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = apiBtnBg,
                            disabledContainerColor = apiBtnBg
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("api_save_btn")
                    ) {
                        Text(apiBtnText, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Metadata Configuration", color = Color(0xFF4B5563), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    var titleInput by remember(titleCharLimit) { mutableStateOf(titleCharLimit.toInt().toString()) }
                    var descInput by remember(descCharLimit) { mutableStateOf(descCharLimit.toInt().toString()) }
                    var keywordsInput by remember(keywordsLimit) { mutableStateOf(keywordsLimit.toInt().toString()) }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Title Limit:", fontSize = 11.sp, color = Color(0xFF4B5563), modifier = Modifier.width(65.dp))
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { newValue ->
                                titleInput = newValue
                                newValue.toFloatOrNull()?.let { num ->
                                    if(num in 50f..200f) viewModel.setTitleCharLimit(num)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(65.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color(0xFF1F2937)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF25C05),
                                unfocusedBorderColor = Color(0xFFCCCCCC)
                            )
                        )
                        Slider(
                            value = titleCharLimit,
                            onValueChange = { viewModel.setTitleCharLimit(it) },
                            valueRange = 50f..200f,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Desc Limit:", fontSize = 11.sp, color = Color(0xFF4B5563), modifier = Modifier.width(65.dp))
                        OutlinedTextField(
                            value = descInput,
                            onValueChange = { newValue ->
                                descInput = newValue
                                newValue.toFloatOrNull()?.let { num ->
                                    if(num in 100f..300f) viewModel.setDescCharLimit(num)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(65.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color(0xFF1F2937)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF25C05),
                                unfocusedBorderColor = Color(0xFFCCCCCC)
                            )
                        )
                        Slider(
                            value = descCharLimit,
                            onValueChange = { viewModel.setDescCharLimit(it) },
                            valueRange = 100f..300f,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Keywords:", fontSize = 11.sp, color = Color(0xFF4B5563), modifier = Modifier.width(65.dp))
                        OutlinedTextField(
                            value = keywordsInput,
                            onValueChange = { newValue ->
                                keywordsInput = newValue
                                newValue.toFloatOrNull()?.let { num ->
                                    if(num in 10f..50f) viewModel.setKeywordsLimit(num)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(65.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color(0xFF1F2937)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF25C05),
                                unfocusedBorderColor = Color(0xFFCCCCCC)
                            )
                        )
                        Slider(
                            value = keywordsLimit,
                            onValueChange = { viewModel.setKeywordsLimit(it) },
                            valueRange = 10f..50f,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = blacklistWords,
                        onValueChange = { viewModel.setBlacklistWords(it) },
                        label = { Text("Blacklist Words") },
                        placeholder = { Text("ex: vector, illustration, abstract") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedLabelColor = Color(0xFFF25C05),
                            unfocusedLabelColor = Color(0xFF4B5563),
                            focusedBorderColor = Color(0xFFF25C05),
                            unfocusedBorderColor = Color(0xFFCCCCCC)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = promptConcept,
                        onValueChange = { viewModel.setPromptConcept(it) },
                        label = { Text("Kata Kunci Inti / Deskripsi Singkat") },
                        placeholder = { Text("Contoh: laptop di meja kayu minimalis, aesthetic lighting...") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("concept_prompt_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedLabelColor = Color(0xFFF25C05),
                            unfocusedLabelColor = Color(0xFF4B5563),
                            focusedBorderColor = Color(0xFFF25C05),
                            unfocusedBorderColor = Color(0xFFCCCCCC)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (isOfflineMode) {
                                if (promptConcept.isBlank()) {
                                    Toast.makeText(context, "Konsep tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setGeneratingAi(true)
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            kotlinx.coroutines.delay(600)
                                            val resultKeywords = viewModel.offlineKeywordMatcher.matchKeywords(promptConcept, context)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                viewModel.setGeneratingAi(false)
                                                if (resultKeywords.isEmpty()) {
                                                    Toast.makeText(context, "Masukkan kata kunci inti atau deskripsi yang lebih spesifik!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val intent = android.content.Intent(context, OfflineResultActivity::class.java).apply {
                                                        putStringArrayListExtra("all_keywords", ArrayList(resultKeywords))
                                                    }
                                                    offlineResultLauncher.launch(intent)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                viewModel.setGeneratingAi(false)
                                                Toast.makeText(context, "Error Offline Generation: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            } else {
                                viewModel.generateMetadata()
                            }
                        },
                        enabled = !isGeneratingAi,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF25C05),
                            disabledContainerColor = Color(0xFFF25C05).copy(alpha = 0.7f),
                            contentColor = Color.White,
                            disabledContentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("generate_metadata_btn")
                    ) {
                        if (isGeneratingAi) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generating PROCESS...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GENERATE METADATA", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Petunjuk Penggunaan AI:",
                                color = Color(0xFF1F2937),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "1. Pilih AI Provider & masukkan Kunci API, klik SAVE API untuk mengaktifkan.\n" +
                                       "2. Tulis konsep detail/deskripsi gambar lalu klik GENERATE METADATA.\n" +
                                       "3. ATAU centang satu gambar di galeri, lalu klik GENERATE METADATA untuk analisis visual langsung oleh Gemini.",
                                color = Color(0xFF4B5563),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Dapatkan Gemini API Key di sini",
                                color = Color(0xFF2563EB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://aistudio.google.com/app/apikey")
                                }
                            )
                        }
                    }
                }
            }

            // 2. --- CARD METADATA INJECTOR (Border Blue `#00a8ff`, Background `#111827`) ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color(0xFF00A8FF)), RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101932))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Single, Multi, ALL buttons replacing upload position
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            SelectionMode.SINGLE to "Single",
                            SelectionMode.MULTI to "Multi",
                            SelectionMode.ALL to "ALL"
                        ).forEach { (mode, label) ->
                            val isActive = selectionMode == mode
                            val btnBg = if (isActive) Color(0xFFF25C05) else Color(0xFF00A8FF).copy(alpha = 0.15f)
                            val borderAccent = if (isActive) Color(0xFFF25C05) else Color(0xFF00A8FF)

                            OutlinedButton(
                                onClick = { viewModel.setSelectionMode(mode) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = btnBg,
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.2.dp, borderAccent),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("mode_${label.lowercase()}_btn")
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Separate Container for Upload and Indicator text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF4B5563), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { requestAndPickImages() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF25C05)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("upload_image_btn")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Upload Icon", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Image", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Counter: "X Images : Y Selected"
                        val totalCount = imagesList.size
                        val selectedCount = imagesList.count { it.isSelected }
                        Text(
                            text = "$totalCount Images : $selectedCount Selected",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preview Area Container (Scrollable dynamically)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 850.dp)
                            .background(Color(0xFF050B18), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, Color(0xFF4B5563).copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        if (imagesList.isEmpty()) {
                            Text(
                                text = "Belum ada file gambar.",
                                color = Color.Gray,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .testTag("empty_placeholder_text"),
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(imagesList, key = { it.id }) { item ->
                                    val isPng = item.name.endsWith(".png", ignoreCase = true)
                                    val isEps = item.name.endsWith(".eps", ignoreCase = true)
                                    val borderColor = if (item.hasMetadata) Color(0xFF22C55E) else Color(0xFF4B5563)
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF101932), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFF4B5563), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(100.dp)
                                                .clickable { viewModel.toggleImageSelected(item.id) }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(100.dp)
                                                    .background(Color(0xFF1F2937), RoundedCornerShape(6.dp))
                                                    .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(6.dp))
                                                    .clip(RoundedCornerShape(6.dp))
                                            ) {
                                                if (isEps) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color(0xFF4F46E5).copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center,
                                                            modifier = Modifier.padding(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Description,
                                                                contentDescription = "EPS Vector",
                                                                tint = Color(0xFF818CF8),
                                                                modifier = Modifier.size(32.dp)
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Surface(
                                                                color = Color(0xFF4F46E5),
                                                                shape = RoundedCornerShape(3.dp),
                                                                modifier = Modifier.padding(horizontal = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "EPS VECTOR",
                                                                    color = Color.White,
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    AsyncImage(
                                                        model = item.uri,
                                                        contentDescription = item.name,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Checkbox(
                                                checked = item.isSelected,
                                                onCheckedChange = { viewModel.toggleImageSelected(item.id) },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF00A8FF),
                                                    uncheckedColor = Color(0xFF4B5563)
                                                ),
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = item.name,
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 8.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Individual Metadata",
                                                    color = Color(0xFF00A8FF),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                IconButton(
                                                    onClick = { viewModel.clearIndividualMetadata(item.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Delete, contentDescription = "Clear Metadata", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            OutlinedTextField(
                                                value = item.individualTitle,
                                                onValueChange = { viewModel.updateIndividualTitle(item.id, it) },
                                                label = { 
                                                    val len = item.individualTitle.length
                                                    if (len > 0) Text("Title ($len character)", fontSize = 10.sp) else Text("Title", fontSize = 10.sp)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color(0xFF22C55E),
                                                    unfocusedTextColor = Color(0xFF22C55E),
                                                    focusedLabelColor = Color(0xFF22C55E),
                                                    unfocusedLabelColor = Color(0xFF4B5563)
                                                ),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(
                                                value = item.individualDescription,
                                                onValueChange = { viewModel.updateIndividualDescription(item.id, it) },
                                                label = { 
                                                    val len = item.individualDescription.length
                                                    if (len > 0) Text("Description ($len character)", fontSize = 10.sp) else Text("Description", fontSize = 10.sp)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color(0xFF22C55E),
                                                    unfocusedTextColor = Color(0xFF22C55E),
                                                    focusedLabelColor = Color(0xFF22C55E),
                                                    unfocusedLabelColor = Color(0xFF4B5563)
                                                ),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(
                                                value = item.individualKeywords,
                                                onValueChange = { viewModel.updateIndividualKeywords(item.id, it) },
                                                label = { 
                                                    val len = if (item.individualKeywords.isBlank()) 0 else item.individualKeywords.split(",").map{ k -> k.trim() }.filter{ k -> k.isNotEmpty() }.size
                                                    if (len > 0) Text("Keywords ($len)", fontSize = 10.sp) else Text("Keywords", fontSize = 10.sp)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color(0xFF22C55E),
                                                    unfocusedTextColor = Color(0xFF22C55E),
                                                    focusedLabelColor = Color(0xFF22C55E),
                                                    unfocusedLabelColor = Color(0xFF4B5563)
                                                ),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { viewModel.generateMetadataForSingleImage(item.id) },
                                                    enabled = !item.isGeneratingMetadata,
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8FF)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f).height(38.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    if (item.isGeneratingMetadata) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                                    } else {
                                                        Text("GENERATE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                val canInject = item.individualTitle.isNotBlank() || item.individualDescription.isNotBlank() || item.individualKeywords.isNotBlank()
                                                Button(
                                                    onClick = { viewModel.injectIndividualMetadata(item.id) },
                                                    enabled = canInject && !item.isInjectingIndividual,
                                                    colors = ButtonDefaults.buttonColors(containerColor = if (canInject) Color(0xFF22C55E) else Color(0xFF6C757D)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f).height(38.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    if (item.isInjectingIndividual) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                                    } else {
                                                        Text("INJECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }



                    // Action controls for deleting (Hapus Terpilih / Clear All Images) if list is not empty
                    if (imagesList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        val activeSelectedSize = imagesList.count { it.isSelected }
                        Button(
                            onClick = { viewModel.injectAllIndividualMetadata() },
                            enabled = activeSelectedSize > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("inject_all_btn")
                        ) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = "Inject All Icon", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("INJECT ALL", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.removeSelectedImages() },
                                enabled = activeSelectedSize > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("delete_selected_btn")
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Icon", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hapus Terpilih", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.clearAllImages() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("clear_all_btn")
                            ) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear All Icon", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All Images", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. --- CARD INPUT METADATA (Background White, Border Blue `#00a8ff`) ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color(0xFF00A8FF)), RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "METADATA INPUT",
                        color = Color(0xFF00A8FF),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { viewModel.setTitle(it) },
                        label = { Text("Title (Judul)") },
                        placeholder = { Text("Masukkan Judul Gambar...") },
                        maxLines = 2,
                        trailingIcon = if (title.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { viewModel.setTitle("") },
                                    modifier = Modifier.testTag("clear_title_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Hapus Judul",
                                        tint = Color(0xFFF25C05)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meta_title_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedLabelColor = Color(0xFF00A8FF),
                            unfocusedLabelColor = Color(0xFF4B5563),
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0xFFCCCCCC)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { viewModel.setDescription(it) },
                        label = { Text("Description (Deskripsi)") },
                        placeholder = { Text("Tulis deskripsi gambar di sini...") },
                        maxLines = 4,
                        trailingIcon = if (description.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { viewModel.setDescription("") },
                                    modifier = Modifier.testTag("clear_desc_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Hapus Deskripsi",
                                        tint = Color(0xFFF25C05)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meta_desc_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedLabelColor = Color(0xFF00A8FF),
                            unfocusedLabelColor = Color(0xFF4B5563),
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0xFFCCCCCC)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Keywords
                    val keywordsCount = if (keywords.isBlank()) 0 else {
                        keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size
                    }
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { viewModel.setKeywords(it) },
                        label = { Text("Keywords ($keywordsCount)") },
                        placeholder = { Text("Contoh: nature, mountain, sunset, peaceful") },
                        maxLines = 5,
                        trailingIcon = if (keywords.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { viewModel.setKeywords("") },
                                    modifier = Modifier.testTag("clear_keywords_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Hapus Keywords",
                                        tint = Color(0xFFF25C05)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meta_keywords_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedLabelColor = Color(0xFF00A8FF),
                            unfocusedLabelColor = Color(0xFF4B5563),
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0xFFCCCCCC)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Creator
                    OutlinedTextField(
                        value = creator,
                        onValueChange = { viewModel.setCreator(it) },
                        label = { Text("Creator / Author (Pencipta)") },
                        placeholder = { Text("Contoh: War Machine Studio") },
                        singleLine = true,
                        trailingIcon = if (creator.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { viewModel.setCreator("") },
                                    modifier = Modifier.testTag("clear_creator_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Hapus Creator",
                                        tint = Color(0xFFF25C05)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meta_creator_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedLabelColor = Color(0xFF00A8FF),
                            unfocusedLabelColor = Color(0xFF4B5563),
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0xFFCCCCCC)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons (INJECT and DOWNLOAD)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Inject Button is active if we selected images and have inputs
                        val canInject = imagesList.any { it.isSelected } && 
                                (title.isNotBlank() || description.isNotBlank() || keywords.isNotBlank() || creator.isNotBlank())
                        val injectBgColor = if (canInject) Color(0xFF22C55E) else Color(0xFF6C757D)

                        // Download Button is active if we have selected images and any is injected or we have something to download
                        val canDownload = imagesList.any { it.isSelected } 
                        val downloadBgColor = if (canDownload) Color(0xFF22C55E) else Color(0xFF6C757D)

                        Button(
                            onClick = { viewModel.injectMetadata() },
                            enabled = canInject && !isInjecting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = injectBgColor,
                                disabledContainerColor = Color(0xFF6C757D)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("inject_btn")
                        ) {
                            if (isInjecting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("INJECT", fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }

                        Button(
                            onClick = { viewModel.downloadInjectedFiles() },
                            enabled = canDownload && !isInjecting && !isDownloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = downloadBgColor,
                                disabledContainerColor = Color(0xFF6C757D)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("download_btn")
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVING", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DOWNLOAD", fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress indicators container (Always visible, matching the web version)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Determine progress status values
                        val isProgressActive = isInjecting || isDownloading
                        val hasInjectionDone = injectionStatusText.contains("DONE")
                        val hasDownloadDone = downloadStatusText.contains("DONE") || downloadStatusText.contains("SUCCESS")

                        val progressPercent = when {
                            isDownloading || hasDownloadDone -> 1.0f
                            isInjecting -> injectionProgress
                            hasInjectionDone -> 1.0f
                            else -> 0.0f
                        }

                        val progressColor = when {
                            isDownloading || hasDownloadDone -> Color(0xFFF25C05) // Orange for Download
                            isInjecting || hasInjectionDone -> Color(0xFF22C55E) // Green for Inject
                            else -> Color(0xFF9CA3AF) // Gray
                        }

                        val animProgress by animateFloatAsState(
                            targetValue = progressPercent, 
                            label = "injection_download_progress"
                        )

                        // 1. Progress Bar (Rounded, 8dp tall)
                        LinearProgressIndicator(
                            progress = { animProgress },
                            color = progressColor,
                            trackColor = Color(0xFFE5E7EB),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. Centered Progress Text Label with Loading Spinner if active
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color(0xFFF25C05),
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SAVING...",
                                    color = Color(0xFFF25C05),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            } else if (hasDownloadDone) {
                                Text(
                                    text = "DOWNLOAD SUCCESS",
                                    color = Color(0xFF22C55E),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            } else {
                                val textCol = if (hasInjectionDone) Color(0xFF22C55E) else Color(0xFF9CA3AF)
                                Text(
                                    text = injectionStatusText.uppercase(),
                                    color = textCol,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3b. --- CONTAINER PILIH MODE (Background White, Border Blue `#00a8ff`) ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color(0xFF00A8FF)), RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PILIH MODE",
                        color = Color(0xFF00A8FF),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Online Button
                    Button(
                        onClick = { viewModel.setOfflineMode(false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isOfflineMode) Color(0xFF00A8FF) else Color(0xFF6C757D)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("mode_online_btn")
                    ) {
                        Text(
                            text = "Online With API Key",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Offline Button
                    Button(
                        onClick = { viewModel.setOfflineMode(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOfflineMode) Color(0xFF00A8FF) else Color(0xFF6C757D)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("mode_offline_btn")
                    ) {
                        Text(
                            text = "WM Keyworder Offline",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

        } // Close inner Column


        // --- Custom Footer Container ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF25C05))
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "www.masbonet.com",
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.clickable {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                Uri.parse("https://masbonet.blogspot.com/?m=1")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Tidak dapat membuka link", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Text(
                    text = " • Designed by Irwan Setiadi • ",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontStyle = FontStyle.Italic
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Privacy Policy",
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.clickable {
                        showPrivacyPolicy = true
                    }
                )
                Text(
                    text = " • war machine hybrid app version 1.6",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    } // Close outer Column

    // --- Privacy Policy Full-screen Overlay ---
    if (showPrivacyPolicy) {
        PrivacyPolicyScreen(onClose = { showPrivacyPolicy = false })
    }

    // --- Floating Action Button for Scroll ---
    if (mainScrollState.maxValue > 0) {
        SmallFloatingActionButton(
            onClick = {
                scope.launch {
                    if (isPointingDown) {
                        mainScrollState.animateScrollTo(mainScrollState.maxValue)
                    } else {
                        mainScrollState.animateScrollTo(0)
                    }
                }
            },
            shape = CircleShape,
            containerColor = Color(0xFF2A3441),
            contentColor = Color(0xFFF25C05),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = if (isPointingDown) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = "Scroll to top/bottom"
            )
        }
    }
}
}

@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B132B))
            .padding(top = 28.dp, bottom = 24.dp)
            .clickable(enabled = true, onClick = {}) // Block clicks from passing through
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Close Privacy Policy",
                        tint = Color(0xFF6FFFE9),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Kembali ke Aplikasi",
                    color = Color(0xFF6FFFE9),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onClose() }
                )
            }

            // Card Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color(0xFF3A506B)), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header inside card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PRIVACY POLICY",
                            color = Color(0xFF5BC0BE),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "War Machine Hybrid",
                            color = Color(0xFF6FFFE9),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last updated: June 04, 2026",
                            color = Color(0xFFA5B4FC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // Safe custom divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(Color(0xFF3A506B))
                        )
                    }

                    // Section 1
                    PrivacyPolicySection(
                        number = "1. No Data Collection",
                        content = {
                            Text(
                                text = "War Machine Hybrid does not collect, store, or share any personal information or usage data from its users.\n\nWe do not require you to create an account, log in, or provide any personal details such as name, email, phone number, or location.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    )

                    // Section 2
                    PrivacyPolicySection(
                        number = "2. No Internet Required",
                        content = {
                            Text(
                                text = "The App functions entirely offline. No internet permission is requested, and the App never sends any data over the network.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    )

                    // Section 3
                    PrivacyPolicySection(
                        number = "3. Permissions Used",
                        content = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "The App may request the following permission only:",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = "•", color = Color(0xFF5BC0BE), fontSize = 14.sp)
                                    Text(
                                        text = "Storage access (READ/WRITE_EXTERNAL_STORAGE) – This is required solely to allow you to read media files (audio, video, images) and embed/edit metadata into those files. All file processing happens locally on your device. The App never uploads, shares, or transmits your files anywhere.",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    )

                    // Section 4
                    PrivacyPolicySection(
                        number = "4. No Third-Party Services",
                        content = {
                            Text(
                                text = "The App does not integrate any analytics, advertising, crash reporting, or passive monetization SDKs. No data is sent to any external server.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    )

                    // Section 5
                    PrivacyPolicySection(
                        number = "5. Children’s Privacy",
                        content = {
                            Text(
                                text = "The App is safe for all ages. Since no data is collected, there is no risk of unintentional data gathering from children under 13.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    )

                    // Section 6
                    PrivacyPolicySection(
                        number = "6. Changes to This Privacy Policy",
                        content = {
                            Text(
                                text = "If the App is updated in the future to include internet-based features or monetization, this policy will be revised and clearly stated within the App.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    )

                    // Section 7
                    PrivacyPolicySection(
                        number = "7. Contact Us",
                        content = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "If you have any questions regarding this policy, you may contact us at:",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Text(
                                    text = "irwansetiadi46@gmail.com",
                                    color = Color(0xFF6FFFE9),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        try {
                                            val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:irwansetiadi46@gmail.com")
                                            }
                                            context.startActivity(mailIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Tidak ada aplikasi email", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF3A506B))
                    )

                    // Card Footer
                    Text(
                        text = "© 2026 War Machine Hybrid. All rights reserved.",
                        color = Color(0xFF657786),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicySection(
    number: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = number,
            color = Color(0xFF6FFFE9),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

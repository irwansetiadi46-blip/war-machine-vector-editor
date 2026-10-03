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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    
    // --- State Observables ---
    val imagesList by viewModel.imagesList.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    val creator by viewModel.creator.collectAsStateWithLifecycle()
    
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
    val isGlobalProcessing by viewModel.isGlobalProcessing.collectAsStateWithLifecycle()
    val globalProcessingText by viewModel.globalProcessingText.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastFlow.collectAsStateWithLifecycle()
    val svgExportDialogState by viewModel.svgExportDialogState.collectAsStateWithLifecycle()
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val titleCharLimit by viewModel.titleCharLimit.collectAsStateWithLifecycle()
    val descCharLimit by viewModel.descCharLimit.collectAsStateWithLifecycle()
    val keywordsLimit by viewModel.keywordsLimit.collectAsStateWithLifecycle()
    val blacklistWords by viewModel.blacklistWords.collectAsStateWithLifecycle()
    val savedBlacklistWords by viewModel.savedBlacklistWords.collectAsStateWithLifecycle()
    val savedPromptConcept by viewModel.savedPromptConcept.collectAsStateWithLifecycle()
    val isAutoInjectionEnabled by viewModel.isAutoInjectionEnabled.collectAsStateWithLifecycle()

    val blacklistSaveColor = when {
        blacklistWords.isBlank() -> Color(0xFF9CA3AF)
        savedBlacklistWords.isNotBlank() && blacklistWords == savedBlacklistWords -> Color(0xFF10B981)
        else -> Color(0xFFF25C05)
    }

    val conceptSaveColor = when {
        promptConcept.isBlank() -> Color(0xFF9CA3AF)
        savedPromptConcept.isNotBlank() && promptConcept == savedPromptConcept -> Color(0xFF10B981)
        else -> Color(0xFFF25C05)
    }

    // Key states for API input logic
    var tempGeminiKey by remember { mutableStateOf(geminiKey) }
    var apiInputsInitialized by remember { mutableStateOf(false) }

    // Initialize temp keys once saved ones load from SharedPreferences
    LaunchedEffect(geminiKey) {
        if (!apiInputsInitialized && geminiKey.isNotEmpty()) {
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
                if (isAutoInjectionEnabled) {
                    val hasSelected = viewModel.imagesList.value.any { it.isSelected }
                    if (hasSelected) {
                        viewModel.injectMetadata()
                    } else if (viewModel.imagesList.value.isNotEmpty()) {
                        viewModel.selectAllImages(true)
                        viewModel.injectMetadata()
                    }
                }
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
    var scrollContainerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var previewCardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val scrollToPreviewCard: () -> Unit = {
        scope.launch {
            val containerCoords = scrollContainerCoordinates
            val targetCoords = previewCardCoordinates
            if (containerCoords != null && targetCoords != null && containerCoords.isAttached && targetCoords.isAttached) {
                val relativeY = containerCoords.localPositionOf(targetCoords, Offset.Zero).y
                val targetScroll = (mainScrollState.value + relativeY - 20).toInt().coerceIn(0, mainScrollState.maxValue)
                mainScrollState.animateScrollTo(
                    value = targetScroll,
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
                )
            } else {
                mainScrollState.animateScrollTo(
                    value = (mainScrollState.value + 650).coerceAtMost(mainScrollState.maxValue),
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }) {
        svgExportDialogState?.let { state ->
            SvgExportDialog(
                state = state,
                onDismiss = { viewModel.dismissSvgExportDialog() },
                onConfirm = { format -> viewModel.confirmSvgExportFormat(format) }
            )
        }

        val screenHeight = constraints.maxHeight.toFloat()
        var headerHeightPx by remember { mutableFloatStateOf(0f) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070E20))
                .onGloballyPositioned { scrollContainerCoordinates = it }
                .verticalScroll(mainScrollState)
        ) {
        // 1. --- MODERN MINIMALIST WEBSITE-STYLE HEADER ---
        var showHeaderMenu by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0A2558), // Deep tech navy blue
                            Color(0xFF1D4ED8), // Royal blue
                            Color(0xFF00A8FF)  // Vibrant cyan blue
                        )
                    )
                )
                .onGloballyPositioned { coordinates ->
                    headerHeightPx = coordinates.size.height.toFloat()
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Group: Hamburger Menu + WAR MACHINE HYBRID + PREMIUM
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box {
                    IconButton(
                        onClick = { showHeaderMenu = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("header_menu_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu Navigation",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Privacy Policy", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showHeaderMenu = false
                                showPrivacyPolicy = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mode Offline: ${if (isOfflineMode) "ON" else "OFF"}", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    if (isOfflineMode) Icons.Default.Lock else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isOfflineMode) Color(0xFFF25C05) else Color(0xFF22C55E)
                                )
                            },
                            onClick = {
                                showHeaderMenu = false
                                viewModel.setOfflineMode(!isOfflineMode)
                            }
                        )
                        if (imagesList.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Hapus Semua Gambar", fontSize = 13.sp, color = Color(0xFFEF4444)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFEF4444))
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    viewModel.clearAllImages()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "WAR MACHINE HYBRID",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = Color(0x33000000),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0x40FFFFFF)),
                    modifier = Modifier.testTag("premium_label")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_crown),
                            contentDescription = "Simbol Mahkota Pro / Premium",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right Group: Gemini API status pill + Info Icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0x33000000),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0x40FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gemini API",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (geminiKey.isNotEmpty()) Color(0xFF22C55E) else Color(0xFF94A3B8),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(
                    onClick = { showPrivacyPolicy = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("header_info_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Informasi Aplikasi & Privacy Policy",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // --- Inner Container with balanced spacing ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 4. --- CARD AUTO METADATA (Flat SaaS-Style, SurfaceVariant Background) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "AUTO METADATA AI",
                        color = Color(0xFF00A8FF),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Model Selection Select Row
                    val aiModels = listOf(
                        Triple("Gemini", "gemini-3.8-flash", "Gemini 3.8 Flash"),
                        Triple("Gemini", "gemini-3.7-flash", "Gemini 3.7 Flash"),
                        Triple("Gemini", "gemini-3.6-flash", "Gemini 3.6 Flash"),
                        Triple("Gemini", "gemini-3.5-flash", "Gemini 3.5 Flash"),
                        Triple("Gemini", "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite"),
                        Triple("Gemini", "gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite"),
                        Triple("Gemini", "gemini-3.1-pro", "Gemini 3.1 Pro"),
                        Triple("Gemini", "gemini-2.5-flash", "Gemini 2.5 Flash"),
                        Triple("Gemini", "gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite")
                    )
                    
                    var expanded by remember { mutableStateOf(false) }
                    val currentModelLabel = aiModels.find { it.second == selectedModel }?.third ?: selectedModel

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Model Aktif:",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentModelLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        Box {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8FF)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Ganti Model", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                aiModels.forEach { (provider, model, label) ->
                                    val isSelected = selectedModel == model
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = null,
                                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00A8FF))
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
                    var keyVisibility by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = tempGeminiKey,
                        onValueChange = {
                            tempGeminiKey = it
                        },
                        enabled = !isOfflineMode,
                        label = { Text("API Key Google Gemini") },
                        placeholder = { Text("Masukkan API Key Gemini Anda...", color = Color(0xFF64748B)) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = if (keyVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            if (isOfflineMode) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked in Offline Mode",
                                    tint = Color(0xFF00A8FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Row {
                                    if (tempGeminiKey.isNotEmpty()) {
                                        IconButton(onClick = {
                                            tempGeminiKey = ""
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear Key", tint = Color(0xFF94A3B8))
                                        }
                                    }
                                    IconButton(onClick = { keyVisibility = !keyVisibility }) {
                                        Icon(
                                            imageVector = if (keyVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Visibility",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0),
                            focusedLabelColor = Color(0xFF00A8FF),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0x40FFFFFF),
                            disabledTextColor = Color(0xFF64748B),
                            disabledBorderColor = Color(0x20FFFFFF),
                            disabledLabelColor = Color(0xFF64748B)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val (apiBtnBg, apiBtnText) = when {
                        isOfflineMode -> {
                            Color(0xFF64748B) to "DISABLE"
                        }
                        tempGeminiKey.isEmpty() -> {
                            Color(0xFF64748B) to "INPUT API"
                        }
                        tempGeminiKey != geminiKey -> {
                            Color(0xFF22C55E) to "SAVE API"
                        }
                        else -> {
                            Color(0xFF00A8FF) to "ACTIVE"
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveApiKey(tempGeminiKey)
                        },
                        enabled = !isOfflineMode && tempGeminiKey.isNotEmpty() && tempGeminiKey != geminiKey,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = apiBtnBg,
                            disabledContainerColor = apiBtnBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("api_save_btn")
                    ) {
                        Text(apiBtnText, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Metadata Configuration", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    var titleInput by remember(titleCharLimit) { mutableStateOf(titleCharLimit.toInt().toString()) }
                    var descInput by remember(descCharLimit) { mutableStateOf(descCharLimit.toInt().toString()) }
                    var keywordsInput by remember(keywordsLimit) { mutableStateOf(keywordsLimit.toInt().toString()) }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Title Limit:", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(65.dp))
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { newValue ->
                                titleInput = newValue
                                newValue.toFloatOrNull()?.let { num ->
                                    if(num in 10f..150f) viewModel.setTitleCharLimit(num)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(65.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00A8FF),
                                unfocusedBorderColor = Color(0x40FFFFFF)
                            )
                        )
                        OrangeToscaCircleSlider(
                            value = titleCharLimit,
                            onValueChange = { viewModel.setTitleCharLimit(it) },
                            valueRange = 10f..150f,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Desc Limit:", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(65.dp))
                        OutlinedTextField(
                            value = descInput,
                            onValueChange = { newValue ->
                                descInput = newValue
                                newValue.toFloatOrNull()?.let { num ->
                                    if(num in 50f..200f) viewModel.setDescCharLimit(num)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(65.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00A8FF),
                                unfocusedBorderColor = Color(0x40FFFFFF)
                            )
                        )
                        OrangeToscaCircleSlider(
                            value = descCharLimit,
                            onValueChange = { viewModel.setDescCharLimit(it) },
                            valueRange = 50f..200f,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Keywords:", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(65.dp))
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
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(65.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00A8FF),
                                unfocusedBorderColor = Color(0x40FFFFFF)
                            )
                        )
                        OrangeToscaCircleSlider(
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
                        placeholder = { Text("ex: vector, illustration, abstract", color = Color(0xFF64748B)) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                                if (blacklistWords.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.clearBlacklistWordsPermanent() },
                                        modifier = Modifier.size(32.dp).testTag("clear_blacklist_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hapus Blacklist Words",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (blacklistWords.isNotBlank()) {
                                            viewModel.saveBlacklistWordsPermanent()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp).testTag("save_blacklist_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Simpan Blacklist Words",
                                        tint = blacklistSaveColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blacklist_words_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0),
                            focusedLabelColor = Color(0xFF00A8FF),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0x40FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = promptConcept,
                        onValueChange = { viewModel.setPromptConcept(it) },
                        label = { Text("Kata Kunci Inti / Deskripsi Singkat") },
                        placeholder = { Text("Contoh: laptop di meja kayu minimalis, aesthetic lighting...", color = Color(0xFF64748B)) },
                        maxLines = 2,
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                                if (promptConcept.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.clearPromptConceptPermanent() },
                                        modifier = Modifier.size(32.dp).testTag("clear_concept_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hapus Kata Kunci Inti",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (promptConcept.isNotBlank()) {
                                            viewModel.savePromptConceptPermanent()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp).testTag("save_concept_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Simpan Kata Kunci Inti",
                                        tint = conceptSaveColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("concept_prompt_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0),
                            focusedLabelColor = Color(0xFF00A8FF),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0x40FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- Fitur Auto Injection ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("auto_injection_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Fitur Auto Injection :",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Switch(
                            checked = isAutoInjectionEnabled,
                            onCheckedChange = { viewModel.setAutoInjectionEnabled(it) },
                            modifier = Modifier.testTag("auto_injection_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00A8FF),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF475569)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (isGeneratingAi) {
                                viewModel.cancelGlobalGeneration()
                                return@Button
                            }
                            scrollToPreviewCard()
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isGeneratingAi) Color(0xFFEF4444) else Color(0xFFF25C05),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("generate_metadata_btn")
                    ) {
                        if (isGeneratingAi) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel", fontWeight = FontWeight.Bold, color = Color.White)
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
                            .background(Color(0xFF0F172A).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Petunjuk Penggunaan AI:",
                                color = Color(0xFF00A8FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "1. Pilih AI Provider & masukkan Kunci API, klik SAVE API untuk mengaktifkan.\n" +
                                       "2. Tulis konsep detail/deskripsi gambar lalu klik GENERATE METADATA.\n" +
                                       "3. ATAU centang satu gambar di galeri, lalu klik GENERATE METADATA untuk analisis visual langsung oleh Gemini.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Dapatkan Gemini API Key di sini",
                                color = Color(0xFF38BDF8),
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

            // 2. --- WORKSPACE / IMAGE PREVIEW AREA (Clean Flat SaaS-Style) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { previewCardCoordinates = it }
                    .testTag("preview_card_container")
            ) {
                // Single, Multi, ALL buttons (Flat SaaS Segmented Control)
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
                        val btnBg = if (isActive) Color(0xFFF25C05) else MaterialTheme.colorScheme.surfaceVariant
                        val textColor = if (isActive) Color.White else Color(0xFF94A3B8)

                        Button(
                            onClick = { viewModel.setSelectionMode(mode) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = btnBg,
                                contentColor = textColor
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("mode_${label.lowercase()}_btn")
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Upload Button and Counter Row (Flat, borderless SaaS-style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Image List Area (Breathes directly on app background)
                if (imagesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada file gambar.",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.testTag("empty_placeholder_text"),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        imagesList.forEach { item ->
                            val isPng = item.name.endsWith(".png", ignoreCase = true)
                            val isEps = item.name.endsWith(".eps", ignoreCase = true)
                            val isSvg = item.name.endsWith(".svg", ignoreCase = true)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("image_item_${item.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Symmetrical, Compact Thumbnail Column
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(76.dp)
                                            .clickable { viewModel.toggleImageSelected(item.id) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(76.dp)
                                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                                .border(
                                                    1.dp,
                                                    if (item.hasMetadata) Color(0xFF22C55E) else Color(0x33FFFFFF),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clip(RoundedCornerShape(8.dp))
                                        ) {
                                            if (item.previewUri != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0xFF0F172A)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AsyncImage(
                                                        model = item.previewUri,
                                                        contentDescription = item.name,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                    if (isSvg || isEps) {
                                                        val badgeText = if (isEps) "EPS" else "SVG"
                                                        val badgeColor = if (isEps) Color(0xFF4F46E5) else Color(0xFF0F766E)
                                                        Surface(
                                                            color = badgeColor.copy(alpha = 0.85f),
                                                            shape = RoundedCornerShape(bottomStart = 4.dp),
                                                            modifier = Modifier.align(Alignment.TopEnd)
                                                        ) {
                                                            Text(
                                                                text = badgeText,
                                                                color = Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            } else if (isEps || isSvg) {
                                                val badgeText = if (isEps) "EPS VECTOR" else "SVG VECTOR"
                                                val badgeColor = if (isEps) Color(0xFF4F46E5) else Color(0xFF0F766E)
                                                val iconTint = if (isEps) Color(0xFF818CF8) else Color(0xFF14B8A6)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(badgeColor.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                        modifier = Modifier.padding(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Description,
                                                            contentDescription = badgeText,
                                                            tint = iconTint,
                                                            modifier = Modifier.size(26.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Surface(
                                                            color = badgeColor,
                                                            shape = RoundedCornerShape(3.dp),
                                                            modifier = Modifier.padding(horizontal = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = badgeText,
                                                                color = Color.White,
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0xFF0F172A)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AsyncImage(
                                                        model = item.uri,
                                                        contentDescription = item.name,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Checkbox(
                                            checked = item.isSelected,
                                            onCheckedChange = { viewModel.toggleImageSelected(item.id) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF00A8FF),
                                                uncheckedColor = Color(0xFF64748B)
                                            ),
                                            modifier = Modifier.size(22.dp)
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

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Right Form Fields Column (Optimized layout, no clutter header)
                                    Column(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = item.individualTitle,
                                            onValueChange = { viewModel.updateIndividualTitle(item.id, it) },
                                            label = { 
                                                val len = item.individualTitle.length
                                                if (len > 0) Text("Title ($len character)", fontSize = 10.sp) else Text("Title", fontSize = 10.sp)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color(0xFF22C55E),
                                                unfocusedTextColor = Color(0xFF22C55E),
                                                focusedLabelColor = Color(0xFF22C55E),
                                                unfocusedLabelColor = Color(0xFF94A3B8),
                                                focusedBorderColor = Color(0xFF00A8FF),
                                                unfocusedBorderColor = Color(0x40FFFFFF)
                                            ),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                            trailingIcon = {
                                                if (item.individualTitle.isNotEmpty()) {
                                                    IconButton(onClick = { 
                                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.individualTitle))
                                                        Toast.makeText(context, "Title disalin", Toast.LENGTH_SHORT).show()
                                                    }) {
                                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Title", tint = Color(0xFF00A8FF), modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = item.individualDescription,
                                            onValueChange = { viewModel.updateIndividualDescription(item.id, it) },
                                            label = { 
                                                val len = item.individualDescription.length
                                                if (len > 0) Text("Description ($len character)", fontSize = 10.sp) else Text("Description", fontSize = 10.sp)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(72.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color(0xFF22C55E),
                                                unfocusedTextColor = Color(0xFF22C55E),
                                                focusedLabelColor = Color(0xFF22C55E),
                                                unfocusedLabelColor = Color(0xFF94A3B8),
                                                focusedBorderColor = Color(0xFF00A8FF),
                                                unfocusedBorderColor = Color(0x40FFFFFF)
                                            ),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                            trailingIcon = {
                                                if (item.individualDescription.isNotEmpty()) {
                                                    IconButton(onClick = { 
                                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.individualDescription))
                                                        Toast.makeText(context, "Description disalin", Toast.LENGTH_SHORT).show()
                                                    }) {
                                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Description", tint = Color(0xFF00A8FF), modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = item.individualKeywords,
                                            onValueChange = { viewModel.updateIndividualKeywords(item.id, it) },
                                            label = { 
                                                val len = if (item.individualKeywords.isBlank()) 0 else item.individualKeywords.split(",").map{ k -> k.trim() }.filter{ k -> k.isNotEmpty() }.size
                                                if (len > 0) Text("Keywords ($len)", fontSize = 10.sp) else Text("Keywords", fontSize = 10.sp)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(72.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color(0xFF22C55E),
                                                unfocusedTextColor = Color(0xFF22C55E),
                                                focusedLabelColor = Color(0xFF22C55E),
                                                unfocusedLabelColor = Color(0xFF94A3B8),
                                                focusedBorderColor = Color(0xFF00A8FF),
                                                unfocusedBorderColor = Color(0x40FFFFFF)
                                            ),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                            trailingIcon = {
                                                if (item.individualKeywords.isNotEmpty()) {
                                                    IconButton(onClick = { 
                                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.individualKeywords))
                                                        Toast.makeText(context, "Keywords disalin", Toast.LENGTH_SHORT).show()
                                                    }) {
                                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Keywords", tint = Color(0xFF00A8FF), modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Symmetrical, Compact Bottom Actions Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Hapus Metadata Individu (Replaces corner trash button)
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.clearIndividualMetadata(item.id)
                                                        Toast.makeText(context, "Metadata berhasil dihapus", Toast.LENGTH_SHORT).show()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = "Hapus Metadata Individu",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // GENERATE Button
                                            Button(
                                                onClick = {
                                                    if (item.isGeneratingMetadata) {
                                                        viewModel.cancelIndividualGeneration(item.id)
                                                    } else {
                                                        viewModel.generateMetadataForSingleImage(item.id)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (item.isGeneratingMetadata) Color(0xFFEF4444) else Color(0xFF00A8FF)
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                if (item.isGeneratingMetadata) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("CANCEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                } else {
                                                    Text("GENERATE", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // INJECT Button
                                            val canInject = item.individualTitle.isNotBlank() || item.individualDescription.isNotBlank() || item.individualKeywords.isNotBlank()
                                            Button(
                                                onClick = { viewModel.injectIndividualMetadata(item.id) },
                                                enabled = canInject && !item.isInjectingIndividual,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (canInject) Color(0xFF22C55E) else Color(0xFF475569),
                                                    disabledContainerColor = Color(0xFF334155),
                                                    disabledContentColor = Color(0xFF94A3B8)
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                if (item.isInjectingIndividual) {
                                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                                } else {
                                                    Text("INJECT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Download Button
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(
                                                        if (item.hasMetadata) Color(0xFF00A8FF).copy(alpha = 0.15f) else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (item.hasMetadata) Color(0xFF00A8FF) else Color(0x33FFFFFF),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable(enabled = item.hasMetadata) { viewModel.downloadIndividualFile(item.id) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Download File",
                                                    tint = if (item.hasMetadata) Color(0xFF00A8FF) else Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
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
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("inject_all_btn")
                    ) {
                        Icon(Icons.Default.DownloadForOffline, contentDescription = "Inject All Icon", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("INJECT ALL", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.downloadInjectedFiles() },
                        enabled = activeSelectedSize > 0 && !isDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8FF)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("download_all_individual_btn")
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SAVING", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Download All", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DOWNLOAD ALL", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
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
                            shape = RoundedCornerShape(8.dp),
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
                            shape = RoundedCornerShape(8.dp),
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
                    text = " • war machine hybrid app version 2.1.0",
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

    // --- Global Processing Indicator ---
    if (isGlobalProcessing) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
                .background(Color(0xFF0F172A).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = globalProcessingText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // --- Custom Draggable Scroll Handle (Sleek slightly reduced height) ---
    val maxScroll = mainScrollState.maxValue.toFloat()
    if (maxScroll > 0f) {
        val density = LocalDensity.current
        val defaultHeaderHeightPx = with(density) { 52.dp.toPx() }
        val baseHeaderHeightPx = if (headerHeightPx > 0f) headerHeightPx else defaultHeaderHeightPx
        val handleHeightPx = baseHeaderHeightPx * 0.72f
        val handleHeight = with(density) { handleHeightPx.toDp().coerceAtLeast(28.dp) }
        val handleWidth = 8.dp
        val availableTrack = (screenHeight - handleHeightPx).coerceAtLeast(1f)
        val thumbYPercentage = if (maxScroll > 0) mainScrollState.value.toFloat() / maxScroll else 0f
        val thumbY = thumbYPercentage * availableTrack
        
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { androidx.compose.ui.unit.IntOffset(0, thumbY.toInt()) }
                .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                .width(handleWidth)
                .height(handleHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1D4ED8),
                            Color(0xFF00A8FF)
                        )
                    ),
                    CircleShape
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (availableTrack > 0) {
                            val scrollPercentageDelta = delta / availableTrack
                            val newScrollValue = (mainScrollState.value + scrollPercentageDelta * maxScroll).toInt()
                            scope.launch {
                                mainScrollState.scrollTo(newScrollValue.coerceIn(0, maxScroll.toInt()))
                            }
                        }
                    }
                )
        )
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
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed) 0.95f else 1f, label = "scale")
    val isGreen = colors.containerColor == Color(0xFF22C55E)
    val pressedBorderColor = if (isGreen) Color(0xFFF25C05) else Color(0xFF22C55E)
    val actualBorder = if (isPressed) BorderStroke(2.dp, pressedBorderColor) else border

    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.scale(scale),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = actualBorder,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed) 0.90f else 1f, label = "scale")
    val isGreen = colors.containerColor == Color(0xFF22C55E)
    val pressedBorderColor = if (isGreen) Color(0xFFF25C05) else Color(0xFF22C55E)
    
    val borderModifier = if (isPressed) Modifier.border(2.dp, pressedBorderColor, CircleShape) else Modifier

    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.scale(scale).then(borderModifier),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed) 0.95f else 1f, label = "scale")
    val isGreen = colors.containerColor == Color(0xFF22C55E)
    val pressedBorderColor = if (isGreen) Color(0xFFF25C05) else Color(0xFF22C55E)
    val actualBorder = if (isPressed) BorderStroke(2.dp, pressedBorderColor) else border

    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = actualBorder,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun SmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.smallShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed) 0.90f else 1f, label = "scale")
    val isGreen = containerColor == Color(0xFF22C55E)
    val pressedBorderColor = if (isGreen) Color(0xFFF25C05) else Color(0xFF22C55E)
    val borderModifier = if (isPressed) Modifier.border(2.dp, pressedBorderColor, shape) else Modifier

    androidx.compose.material3.SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.scale(scale).then(borderModifier),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content
    )
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

@Composable
fun SvgExportDialog(
    state: SvgExportDialogState,
    onDismiss: () -> Unit,
    onConfirm: (SvgExportFormat) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(SvgExportFormat.SVG) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.5.dp, Color(0xFF00A8FF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF00A8FF).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF00A8FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Save As - Format Export SVG",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (state.isIndividual) "Pilih format export untuk file SVG:" else "Ditemukan ${state.svgCount} file SVG. Pilih format export:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Options List
                val options = listOf(
                    Triple(
                        SvgExportFormat.SVG,
                        "Svg",
                        "File Vector SVG (.svg) dengan Metadata XMP"
                    ),
                    Triple(
                        SvgExportFormat.EPS,
                        "Eps",
                        "Convert Vector EPS (.eps) untuk Microstock dengan Metadata"
                    ),
                    Triple(
                        SvgExportFormat.ZIP_SVG_EPS_JPG,
                        "Zip (Svg+Eps+Jpg)",
                        "Bundel Lengkap Microstock (.svg + .eps + .jpg) dengan Metadata"
                    )
                )

                options.forEach { (format, title, description) ->
                    val isSelected = selectedFormat == format
                    val borderColor = if (isSelected) Color(0xFF00A8FF) else Color(0xFF334155)
                    val bgColor = if (isSelected) Color(0xFF00A8FF).copy(alpha = 0.15f) else Color(0xFF0F172A)

                    Surface(
                        onClick = { selectedFormat = format },
                        shape = RoundedCornerShape(10.dp),
                        color = bgColor,
                        border = BorderStroke(1.5.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedFormat = format },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF00A8FF),
                                    unselectedColor = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Text("Batal", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selectedFormat) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8FF)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrangeToscaCircleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged

    // Default Orange (0xFFF25C05), turns to Tosca Green (0xFF0D9488) when pressed or dragged
    val thumbColor by animateColorAsState(
        targetValue = if (isInteracting) Color(0xFF0D9488) else Color(0xFFF25C05),
        animationSpec = tween(durationMillis = 150),
        label = "thumbColor"
    )

    val thumbElevation by animateDpAsState(
        targetValue = if (isInteracting) 4.dp else 2.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbElevation"
    )

    val thumbSize by animateDpAsState(
        targetValue = if (isInteracting) 22.dp else 18.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbSize"
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(
            thumbColor = thumbColor,
            activeTrackColor = Color(0xFF00A8FF),
            inactiveTrackColor = Color(0xFF334155)
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .shadow(elevation = thumbElevation, shape = CircleShape)
                    .background(color = thumbColor, shape = CircleShape)
                    .border(width = 2.dp, color = Color.White, shape = CircleShape)
            )
        },
        modifier = modifier
    )
}


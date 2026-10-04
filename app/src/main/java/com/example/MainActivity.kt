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
import androidx.activity.compose.BackHandler
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var isBottomBarExpanded by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    var isViewAllExpanded by remember { mutableStateOf(false) }
    var selectedDetailImageId by remember { mutableStateOf<Int?>(null) }

    var previousImageCount by remember { mutableIntStateOf(imagesList.size) }
    LaunchedEffect(imagesList.size) {
        if (imagesList.size > previousImageCount) {
            isBottomBarExpanded = true
        }
        previousImageCount = imagesList.size
    }

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
            isBottomBarExpanded = true
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
        val animatedQuickScrollBottomPadding by animateDpAsState(
            targetValue = if (isBottomBarExpanded) 134.dp else 52.dp,
            label = "quick_scroll_padding"
        )
        val animatedFooterClearance by animateDpAsState(
            targetValue = if (isBottomBarExpanded) 140.dp else 52.dp,
            label = "footer_clearance"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070E20))
        ) {
            // 1. --- FIXED WEBSITE-STYLE HEADER (Always visible, does NOT scroll away) ---
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

        // 2. --- SCROLLABLE MAIN CONTENT AREA ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { scrollContainerCoordinates = it }
                    .verticalScroll(mainScrollState)
            ) {
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
                            text = "Auto Inject Metadata :",
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
                }
            }

            // 2. --- WORKSPACE / IMAGE PREVIEW AREA (Clean Flat SaaS-Style) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { previewCardCoordinates = it }
                    .testTag("preview_card_container")
            ) {
                // Import Button and Counter Row (Compact, side-by-side with total images imported)
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
                        modifier = Modifier.testTag("import_image_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Import Images",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Counter: "X Images Imported"
                    Text(
                        text = "${imagesList.size} Images Imported",
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Controls Row: Switch Grid 3 / Vertical & View All / Hide
                if (imagesList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Switch 3-Kolom vs Vertical
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0x4038BDF8)),
                            modifier = Modifier
                                .height(32.dp)
                                .clickable {
                                    isGridView = !isGridView
                                    isViewAllExpanded = false
                                }
                                .testTag("toggle_grid_vertical_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                    contentDescription = if (isGridView) "Switch to Vertical" else "Switch to Grid 3",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isGridView) "Grid 3" else "Vertical",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Tombol Icon Kaca Pembesar View All / Hide
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isViewAllExpanded) Color(0xFF0284C7) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (isViewAllExpanded) Color(0xFF38BDF8) else Color(0x4038BDF8)),
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { isViewAllExpanded = !isViewAllExpanded }
                                .testTag("toggle_view_all_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = if (isViewAllExpanded) "Hide" else "View All",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isViewAllExpanded) "Hide" else "View All",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Image List Area (Breathes directly on app background)
                if (imagesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(vertical = 80.dp),
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
                    if (isViewAllExpanded) {
                        // Tampilan View All: Menampilkan card preview semua gambar lengkap dengan kolom metadata dan tombol individualnya seperti biasa di halaman utama
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            imagesList.forEach { item ->
                                FullImageCard(
                                    item = item,
                                    viewModel = viewModel,
                                    clipboardManager = clipboardManager
                                )
                            }
                        }
                    } else if (isGridView) {
                        // Tampilan Grid 3 Kolom: Hanya menampilkan preview gambar saja tanpa kolom metadata
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            imagesList.chunked(3).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        CompactGridImageCard(
                                            item = item,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectedDetailImageId = item.id }
                                        )
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        // Tampilan Vertical: Hanya menampilkan preview gambar tanpa kolom metadata
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            imagesList.forEach { item ->
                                CompactVerticalImageCard(
                                    item = item,
                                    onClick = { selectedDetailImageId = item.id }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Petunjuk Penggunaan AI (Paling bawah di bawah Card Preview tepat di atas footer)
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
                        text = "1. Masukkan API Key Gemini, lalu klik SAVE API untuk mengaktifkan.\n" +
                               "2. Import gambar yang ingin diproses melalui tombol Import.\n" +
                               "3. Klik tombol GENERATE melayang di pojok bawah untuk analisis dan pembuatan metadata otomatis.",
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

            // Bottom clearance for floating action bar if images are loaded
            if (imagesList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(72.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

        } // Close inner Column


        // --- Custom Footer Container ---
        Column(
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
                    text = " • war machine hybrid app version 2.1.1",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontStyle = FontStyle.Italic
                )
            }
        }

        // Dynamic clearance so footer is fully visible above bottom action bar
        Spacer(modifier = Modifier.height(animatedFooterClearance))
    } // Close scrollable Column

    // Custom Draggable Scroll Handle (within the scroll area)
    val maxScroll = mainScrollState.maxValue.toFloat()
    if (maxScroll > 0f) {
        val density = LocalDensity.current
        val handleHeight = 36.dp
        val handleWidth = 8.dp
        val scrollTrackHeight = (screenHeight - (if (headerHeightPx > 0f) headerHeightPx else 52f)).coerceAtLeast(1f)
        val availableTrack = (scrollTrackHeight - with(density) { handleHeight.toPx() }).coerceAtLeast(1f)
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
} // Close Box(modifier = Modifier.weight(1f).fillMaxWidth())
} // Close outer Column

    // --- Global Processing & Downloading Indicator (Centered directly above bottom Container Bar) ---
    if (isGlobalProcessing) {
        val indicatorBottomPadding by animateDpAsState(
            targetValue = if (isBottomBarExpanded) 136.dp else 56.dp,
            label = "indicator_bottom"
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = indicatorBottomPadding)
                .background(Color(0xFF0F172A).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                .shadow(10.dp, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = globalProcessingText,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // --- Dedicated Floating Action Bar Container (Permanent, Collapsible) ---
    AnimatedVisibility(
        visible = isBottomBarExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.96f),
            border = BorderStroke(1.dp, Color(0x3338BDF8)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                .testTag("floating_action_bar_container")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hide / Collapse Handle Bar (Click or Swipe Down to Hide)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clickable { isBottomBarExpanded = false }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                if (delta > 8f) isBottomBarExpanded = false
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(Color(0xFF64748B), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Sembunyikan Menu",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 1. GENERATE BATCH BUTTON (Top, Center, Largest Size)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("floating_generate_btn")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
                                )
                            )
                            .clickable {
                                if (isGeneratingAi) {
                                    viewModel.cancelGlobalGeneration()
                                } else {
                                    if (imagesList.isEmpty() && promptConcept.isBlank()) {
                                        Toast.makeText(context, "Import gambar terlebih dahulu atau isi konsep AI!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.generateMetadata()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isGeneratingAi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CANCEL GENERATE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    letterSpacing = 0.5.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Generate Batch",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (imagesList.size > 1) "GENERATE BATCH" else "GENERATE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. HORIZONTAL BUTTONS ROW: Inject All, Download All, Clear All (Same shape)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Button 1: Inject All
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("floating_inject_all_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF059669), Color(0xFF10B981))
                                    )
                                )
                                .clickable(enabled = !isInjecting) {
                                    if (imagesList.isEmpty()) {
                                        Toast.makeText(context, "Belum ada gambar yang di-import!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.injectAllIndividualMetadata()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isInjecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Injecting...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = "Inject All",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Inject All",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // Button 2: Download All
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("floating_download_all_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF0284C7), Color(0xFF0EA5E9))
                                    )
                                )
                                .clickable(enabled = !isDownloading) {
                                    if (imagesList.isEmpty()) {
                                        Toast.makeText(context, "Belum ada gambar untuk didownload!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.downloadInjectedFiles()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Saving...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download All",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Download All",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // Button 3: Clear All
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("floating_clear_all_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFDC2626), Color(0xFFEF4444))
                                    )
                                )
                                .clickable {
                                    if (imagesList.isEmpty()) {
                                        Toast.makeText(context, "Daftar gambar sudah kosong.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.clearAllImages()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear All",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Clear All",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // When Collapsed: Peek Handle Button to restore
    AnimatedVisibility(
        visible = !isBottomBarExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF0F172A).copy(alpha = 0.95f),
            border = BorderStroke(1.dp, Color(0x5538BDF8)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .clickable { isBottomBarExpanded = true }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (delta < -8f) isBottomBarExpanded = true
                    }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Tampilkan Action Bar",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ACTION TOOLS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }

    // --- Floating Action Button for Scroll (Positioned safely above bottom container bar) ---
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
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF38BDF8),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = animatedQuickScrollBottomPadding)
        ) {
            Icon(
                imageVector = if (isPointingDown) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = "Scroll to top/bottom"
            )
        }
    }

    // --- Privacy Policy Full-screen Overlay ---
    if (showPrivacyPolicy) {
        PrivacyPolicyScreen(onClose = { showPrivacyPolicy = false })
    }

    // --- Image Detail Screen (Jendela Halaman Baru Terpisah per Gambar) ---
    selectedDetailImageId?.let { id ->
        val selectedImage = imagesList.find { it.id == id }
        if (selectedImage != null) {
            ImageDetailScreen(
                item = selectedImage,
                onClose = { selectedDetailImageId = null },
                viewModel = viewModel,
                clipboardManager = clipboardManager
            )
        }
    }
}
}

@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit) {
    BackHandler { onClose() }
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

fun getDemandScoreColor(score: Int): Color {
    return when {
        score >= 90 -> Color(0xFFA855F7) // Ungu – Very High
        score >= 75 -> Color(0xFF3B82F6) // Biru – High
        score >= 50 -> Color(0xFF22C55E) // Hijau – Medium-High
        score >= 25 -> Color(0xFFEAB308) // Kuning/Oranye – Medium-Low
        else -> Color(0xFFE2E8F0)        // Putih/Abu-abu – Low
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, fontSize = 8.5.sp, color = Color(0xFF94A3B8))
    }
}

// =========================================================================
// --- COMPACT PREVIEW CARDS & FULL DETAIL SCREEN COMPONENTS ---
// =========================================================================

@Composable
fun CompactGridImageCard(
    item: ImageItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isPng = item.name.endsWith(".png", ignoreCase = true)
    val isEps = item.name.endsWith(".eps", ignoreCase = true)
    val isSvg = item.name.endsWith(".svg", ignoreCase = true)

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .testTag("compact_grid_image_${item.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(
            1.dp,
            if (item.hasMetadata) Color(0xFF22C55E).copy(alpha = 0.8f) else Color(0x3038BDF8)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.previewUri != null) {
                AsyncImage(
                    model = item.previewUri,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isEps || isSvg) {
                val iconTint = if (isEps) Color(0xFF818CF8) else Color(0xFF14B8A6)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEps) "EPS" else "SVG",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Top Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSvg || isEps) {
                    val badgeColor = if (isEps) Color(0xFF4F46E5) else Color(0xFF0F766E)
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = if (isEps) "EPS" else "SVG",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                if (item.hasMetadata) {
                    Surface(
                        color = Color(0xFF22C55E),
                        shape = CircleShape,
                        modifier = Modifier.size(9.dp)
                    ) {}
                }
            }

            // Bottom Filename Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6000000))
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CompactVerticalImageCard(
    item: ImageItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isPng = item.name.endsWith(".png", ignoreCase = true)
    val isEps = item.name.endsWith(".eps", ignoreCase = true)
    val isSvg = item.name.endsWith(".svg", ignoreCase = true)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("compact_vertical_image_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Icon, Filename, Badges & Open Hint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFF00A8FF),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSvg || isEps) {
                    val badgeText = if (isEps) "EPS" else "SVG"
                    val badgeColor = if (isEps) Color(0xFF4F46E5) else Color(0xFF0F766E)
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (item.hasMetadata) {
                    Surface(
                        color = Color(0xFF22C55E).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f)),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "INJECTED",
                            color = Color(0xFF22C55E),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Buka Detail",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Image Preview only
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .heightIn(min = 160.dp, max = 220.dp)
                        .widthIn(min = 160.dp, max = 340.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            if (item.hasMetadata) Color(0xFF22C55E).copy(alpha = 0.6f) else Color(0x33FFFFFF),
                            RoundedCornerShape(10.dp)
                        )
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.previewUri != null) {
                        AsyncImage(
                            model = item.previewUri,
                            contentDescription = item.name,
                            modifier = Modifier
                                .wrapContentSize()
                                .heightIn(min = 160.dp, max = 220.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else if (isEps || isSvg) {
                        val badgeText = if (isEps) "EPS VECTOR" else "SVG VECTOR"
                        val badgeColor = if (isEps) Color(0xFF4F46E5) else Color(0xFF0F766E)
                        val iconTint = if (isEps) Color(0xFF818CF8) else Color(0xFF14B8A6)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(24.dp)
                                .heightIn(min = 130.dp, max = 170.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = badgeText,
                                tint = iconTint,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(color = badgeColor, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = badgeText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.name,
                            modifier = Modifier
                                .wrapContentSize()
                                .heightIn(min = 160.dp, max = 220.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Click hint
            Text(
                text = "Klik gambar untuk melihat & edit metadata lengkap ➔",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullImageCard(
    item: ImageItem,
    viewModel: MainViewModel,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
) {
    val context = LocalContext.current
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 1. Top Header: Filename, Format Badge, Injected Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFF00A8FF),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSvg || isEps) {
                    val badgeText = if (isEps) "EPS" else "SVG"
                    val badgeColor = if (isEps) Color(0xFF4F46E5) else Color(0xFF0F766E)
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (item.hasMetadata) {
                    Surface(
                        color = Color(0xFF22C55E).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f)),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "INJECTED",
                            color = Color(0xFF22C55E),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 2. Centered & Large Image Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .heightIn(min = 180.dp, max = 220.dp)
                        .widthIn(min = 160.dp, max = 340.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            if (item.hasMetadata) Color(0xFF22C55E).copy(alpha = 0.6f) else Color(0x33FFFFFF),
                            RoundedCornerShape(10.dp)
                        )
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.previewUri != null) {
                        AsyncImage(
                            model = item.previewUri,
                            contentDescription = item.name,
                            modifier = Modifier
                                .wrapContentSize()
                                .heightIn(min = 180.dp, max = 220.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else if (isEps || isSvg) {
                        val badgeText = if (isEps) "EPS VECTOR" else "SVG VECTOR"
                        val badgeColor = if (isEps) Color(0xFF4F46E5) else Color(0xFF0F766E)
                        val iconTint = if (isEps) Color(0xFF818CF8) else Color(0xFF14B8A6)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(24.dp)
                                .heightIn(min = 140.dp, max = 180.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = badgeText,
                                tint = iconTint,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = badgeColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.name,
                            modifier = Modifier
                                .wrapContentSize()
                                .heightIn(min = 160.dp, max = 220.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Title TextField
            OutlinedTextField(
                value = item.individualTitle,
                onValueChange = { viewModel.updateIndividualTitle(item.id, it) },
                label = { 
                    val len = item.individualTitle.length
                    Text(if (len > 0) "Title ($len characters)" else "Title", fontSize = 11.sp)
                },
                singleLine = false,
                maxLines = Int.MAX_VALUE,
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
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                trailingIcon = {
                    if (item.individualTitle.isNotEmpty()) {
                        IconButton(onClick = { 
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.individualTitle))
                            Toast.makeText(context, "Title disalin", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Title", tint = Color(0xFF00A8FF), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Description TextField
            OutlinedTextField(
                value = item.individualDescription,
                onValueChange = { viewModel.updateIndividualDescription(item.id, it) },
                label = { 
                    val len = item.individualDescription.length
                    Text(if (len > 0) "Description ($len characters)" else "Description", fontSize = 11.sp)
                },
                singleLine = false,
                maxLines = Int.MAX_VALUE,
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
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                ),
                trailingIcon = {
                    if (item.individualDescription.isNotEmpty()) {
                        IconButton(onClick = { 
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.individualDescription))
                            Toast.makeText(context, "Description disalin", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Description", tint = Color(0xFF00A8FF), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Keywords Section (ImStocker Style FlowRow)
            val effectiveKeywords = item.getEffectiveKeywordItems()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Keywords (${effectiveKeywords.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22C55E)
                        )
                        if (effectiveKeywords.any { it.isTrademark }) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Trademark Alert",
                                    color = Color(0xFFEF4444),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    if (effectiveKeywords.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val textToCopy = effectiveKeywords.joinToString(",") { it.word }
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                                Toast.makeText(context, "Keywords disalin", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy All Keywords",
                                tint = Color(0xFF00A8FF),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LegendDot(Color(0xFFA855F7), "Very High")
                    LegendDot(Color(0xFF3B82F6), "High")
                    LegendDot(Color(0xFF22C55E), "Medium")
                    LegendDot(Color(0xFFEAB308), "Low")
                    LegendDot(Color(0xFFE2E8F0), "Very Low")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Keywords FlowRow
                if (effectiveKeywords.isEmpty()) {
                    Text(
                        text = "Belum ada keywords. Klik GENERATE atau ketik di bawah.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        effectiveKeywords.forEachIndexed { index, kw ->
                            val isTm = kw.isTrademark
                            val dotColor = if (isTm) Color(0xFFEF4444) else getDemandScoreColor(kw.demandScore)

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isTm) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF1E293B),
                                border = BorderStroke(
                                    1.dp,
                                    if (isTm) Color(0xFFEF4444) else Color(0x33FFFFFF)
                                ),
                                modifier = Modifier.clickable {
                                    if (isTm && !kw.replacement.isNullOrBlank()) {
                                        viewModel.replaceTrademarkKeyword(item.id, index)
                                        Toast.makeText(context, "Diganti dengan '${kw.replacement}'", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(dotColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = kw.word,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isTm) Color(0xFFFCA5A5) else Color.White
                                    )
                                    if (isTm && !kw.replacement.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = "Ganti dengan padanan generik",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus kata kunci",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable {
                                                viewModel.removeKeywordFromImage(item.id, index)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // Input Add Keyword
                var newKwText by remember(item.id) { mutableStateOf("") }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKwText,
                        onValueChange = { newKwText = it },
                        placeholder = { Text("Tambah keyword (pisahkan dengan koma)...", fontSize = 10.5.sp, color = Color(0xFF64748B)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0x33FFFFFF)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (newKwText.isNotBlank()) {
                                val words = newKwText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                words.forEach { w -> viewModel.addKeywordToImage(item.id, w) }
                                newKwText = ""
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8FF)),
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Action Buttons Row: Delete metadata, GENERATE, INJECT, DOWNLOAD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hapus Metadata Individu
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                        modifier = Modifier.size(18.dp)
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
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    if (item.isGeneratingMetadata) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GENERATE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    if (item.isInjectingIndividual) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("INJECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Download Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ImageDetailScreen(
    item: ImageItem,
    onClose: () -> Unit,
    viewModel: MainViewModel,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
) {
    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070E20))
            .clickable(enabled = true, onClick = {}) // Block background clicks
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0A2558), Color(0xFF1D4ED8), Color(0xFF00A8FF))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali ke Beranda",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Detail Metadata & Tools",
                        color = Color(0xFFBAE6FD),
                        fontSize = 10.5.sp
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Detail",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FullImageCard(
                    item = item,
                    viewModel = viewModel,
                    clipboardManager = clipboardManager
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}



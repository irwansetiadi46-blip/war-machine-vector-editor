package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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
    val isTouchEffectEnabled by viewModel.isTouchEffectEnabled.collectAsStateWithLifecycle()
    val selectedTouchEffect by viewModel.selectedTouchEffect.collectAsStateWithLifecycle()
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showTouchEffectDialog by remember { mutableStateOf(false) }
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

    // Toast State for custom In-Theme Notification
    var activeToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            activeToast = msg
            delay(1000)
            activeToast = null
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

    // Touch Effect State
    var touchPoints by remember { mutableStateOf(listOf<TouchEffectPoint>()) }
    var tickerTime by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }

    LaunchedEffect(touchPoints) {
        if (touchPoints.isNotEmpty()) {
            while (touchPoints.isNotEmpty()) {
                withFrameMillis {
                    tickerTime = SystemClock.uptimeMillis()
                }
                touchPoints = touchPoints.filter { tickerTime - it.timestamp < it.duration }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(isTouchEffectEnabled, selectedTouchEffect) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            focusManager.clearFocus()
                            if (isTouchEffectEnabled) {
                                val now = SystemClock.uptimeMillis()
                                val newPoints = event.changes.map { change ->
                                    TouchEffectPoint(
                                        id = change.id.value,
                                        x = change.position.x,
                                        y = change.position.y,
                                        timestamp = now,
                                        duration = when (selectedTouchEffect) {
                                            "Neon Sparkle" -> 700L
                                            "Water Ripple" -> 750L
                                            "Duotone Wave" -> 700L
                                            else -> 600L
                                        },
                                        effectType = selectedTouchEffect
                                    )
                                }
                                touchPoints = (touchPoints + newPoints).takeLast(12)
                            }
                        }
                    }
                }
            }
    ) {
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
            targetValue = if (isBottomBarExpanded) 178.dp else 72.dp,
            label = "quick_scroll_padding"
        )
        val animatedFooterClearance by animateDpAsState(
            targetValue = if (isBottomBarExpanded) 175.dp else 65.dp,
            label = "footer_clearance"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070E20), // Top: Deep dark navy/tech slate
                            Color(0xFF0C1329), // Upper mid: Midnight slate
                            Color(0xFF171233), // Lower mid: Deep plum slate
                            Color(0xFF27174A)  // Bottom: Luxurious brighter purple/violet glow
                        )
                    )
                )
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
            // Left Group: Hamburger Menu + WAR MACHINE HYBRID + Pro
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
                            text = { Text("Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF00A8FF))
                            },
                            onClick = {
                                showHeaderMenu = false
                                showSettingsScreen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF38BDF8))
                            },
                            onClick = {
                                showHeaderMenu = false
                                showAbout = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy Policy", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF6FFFE9))
                            },
                            onClick = {
                                showHeaderMenu = false
                                showPrivacyPolicy = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Touch Effect", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFF472B6))
                            },
                            onClick = {
                                showHeaderMenu = false
                                showTouchEffectDialog = true
                            }
                        )
                        if (imagesList.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Clear All", fontSize = 13.sp, color = Color(0xFFEF4444)) },
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

                // Teks Pro warna putih, Background Orange, Border putih, ukuran diperbesar
                Surface(
                    color = Color(0xFFF25C05),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.5.dp, Color.White),
                    shadowElevation = 3.dp,
                    modifier = Modifier.testTag("premium_label")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pro",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right Group: War Engine capsule pill with dark purple background and bright purple border
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF261245),
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, Color(0xFFC084FC)),
                    modifier = Modifier.clickable { showSettingsScreen = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "War Engine",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (geminiKey.isNotEmpty()) Color(0xFF22C55E) else Color(0xFF94A3B8),
                                    shape = CircleShape
                                )
                        )
                    }
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

                Spacer(modifier = Modifier.height(14.dp))

                // Title and Controls Row (Title on Left, Icon Switch & Icon View All on Right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp, start = 2.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Preview",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            letterSpacing = 0.3.sp
                        )
                    }

                    // Right side: Icons only (No button container, comfortable touch targets)
                    if (imagesList.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Switch Grid 3 / Vertical: Pure Icon
                            IconButton(
                                onClick = {
                                    isGridView = !isGridView
                                    isViewAllExpanded = false
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("toggle_grid_vertical_btn")
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                    contentDescription = if (isGridView) "Switch to Vertical" else "Switch to Grid 3",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // View All / Hide: Pure Icon (Search / Magnifying glass)
                            IconButton(
                                onClick = { isViewAllExpanded = !isViewAllExpanded },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("toggle_view_all_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = if (isViewAllExpanded) "Hide" else "View All",
                                    tint = if (isViewAllExpanded) Color(0xFF38BDF8) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Image List Area (Breathes directly on app background)
                if (imagesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(vertical = 160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Belum ada Preview Gambar.",
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.5.sp,
                                modifier = Modifier.testTag("empty_placeholder_text"),
                                fontFamily = FontFamily.Monospace
                            )
                        }
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
            // Generous bottom clearance so the main screen remains spacious and scrollable even when empty
            Spacer(modifier = Modifier.height(if (imagesList.isEmpty()) 380.dp else (animatedFooterClearance + 160.dp)))

        } // Close inner Column
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
            targetValue = if (isBottomBarExpanded) 178.dp else 72.dp,
            label = "indicator_bottom"
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = indicatorBottomPadding)
                .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color(0x8038BDF8))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF00B132B),
                            Color(0xEA1E1B4B)
                        )
                    ),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0x9038BDF8), Color(0x90C084FC), Color(0x9038BDF8))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color(0xFF38BDF8),
                    strokeWidth = 2.2.dp
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = globalProcessingText,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }

    // --- Dedicated Floating Action Bar Container (Modern Glassmorphism Style) ---
    AnimatedVisibility(
        visible = isBottomBarExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val glassShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = glassShape,
                    spotColor = Color(0x7038BDF8),
                    ambientColor = Color(0x40818CF8)
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEE0B132B), // Deep translucent cosmic slate
                            Color(0xDD171938), // Translucent indigo slate
                            Color(0xF2070D1E)  // Translucent midnight base
                        )
                    ),
                    shape = glassShape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x9938BDF8), // Specular light cyan reflection at top
                            Color(0x50818CF8), // Violet refraction mid
                            Color(0x2038BDF8)  // Subtle bottom border
                        )
                    ),
                    shape = glassShape
                )
                .testTag("floating_action_bar_container")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hide / Collapse Handle Bar (Mepet ke tepi atas kontainer bar, tanpa arrow icon)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clickable { isBottomBarExpanded = false }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                if (delta > 8f) isBottomBarExpanded = false
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0x6038BDF8), Color(0xA0C084FC), Color(0x6038BDF8))
                                ),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1. GENERATE BATCH BUTTON (Top, Center, Largest Size - Glowing Glassmorphism)
                val generateShape = RoundedCornerShape(14.dp)
                Surface(
                    shape = generateShape,
                    shadowElevation = 8.dp,
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0x99FFFFFF),
                                Color(0x60E879F9),
                                Color(0x9938BDF8)
                            )
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(generateShape)
                        .testTag("floating_generate_btn")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (isGeneratingAi) {
                                        listOf(Color(0xFFDC2626), Color(0xFFEF4444), Color(0xFFB91C1C))
                                    } else {
                                        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFD946EF))
                                    }
                                )
                            )
                            .clickable {
                                if (isGeneratingAi) {
                                    viewModel.cancelGlobalGeneration()
                                } else {
                                    if (imagesList.isEmpty() && promptConcept.isBlank()) {
                                        viewModel.showToast("No Images")
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
                                    letterSpacing = 0.6.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Generate Batch",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (imagesList.size > 1) "GENERATE BATCH" else "GENERATE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Aksen Garis Pemisah di Bawah Tombol GENERATE (Rapi & Tidak Sesak)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x6038BDF8),
                                    Color(0xA0C084FC),
                                    Color(0x6038BDF8),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. HORIZONTAL BUTTONS ROW: Inject All, Download All, Clear All (Frosted Glass Style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val subBtnShape = RoundedCornerShape(12.dp)

                    // Button 1: Inject All (Frosted Emerald Glass)
                    Surface(
                        shape = subBtnShape,
                        shadowElevation = 4.dp,
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0x9034D399), Color(0x3510B981))
                            )
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(subBtnShape)
                            .testTag("floating_inject_all_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xE6059669), Color(0xCC047857))
                                    )
                                )
                                .clickable(enabled = !isInjecting) {
                                    if (imagesList.isEmpty()) {
                                        viewModel.showToast("No Images")
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
                                    Spacer(modifier = Modifier.width(5.dp))
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
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

                    // Button 2: Download All (Frosted Azure Glass)
                    Surface(
                        shape = subBtnShape,
                        shadowElevation = 4.dp,
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0x9038BDF8), Color(0x350284C7))
                            )
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(subBtnShape)
                            .testTag("floating_download_all_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xE60284C7), Color(0xCC0369A1))
                                    )
                                )
                                .clickable(enabled = !isDownloading) {
                                    if (imagesList.isEmpty()) {
                                        viewModel.showToast("No Injected")
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
                                    Spacer(modifier = Modifier.width(5.dp))
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
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

                    // Button 3: Clear All (Frosted Crimson Glass)
                    Surface(
                        shape = subBtnShape,
                        shadowElevation = 4.dp,
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0x90F87171), Color(0x35DC2626))
                            )
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(subBtnShape)
                            .testTag("floating_clear_all_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xE6DC2626), Color(0xCCB91C1C))
                                    )
                                )
                                .clickable {
                                    if (imagesList.isEmpty()) {
                                        viewModel.showToast("No Images")
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
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
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

    // When Collapsed: Peek Handle Button to restore (Glassmorphism Pill)
    AnimatedVisibility(
        visible = !isBottomBarExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(Color(0x9038BDF8), Color(0x90C084FC), Color(0x9038BDF8))
                )
            ),
            shadowElevation = 14.dp,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .shadow(14.dp, CircleShape, spotColor = Color(0x7038BDF8))
                .clickable { isBottomBarExpanded = true }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (delta < -8f) isBottomBarExpanded = true
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xEE0B132B), Color(0xEE1E1B4B), Color(0xEE0F172A))
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Tampilkan Action Bar",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "ACTION TOOLS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.6.sp
                    )
                }
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
            containerColor = Color(0xFFF25C05),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = animatedQuickScrollBottomPadding)
        ) {
            Icon(
                imageVector = if (isPointingDown) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = "Scroll to top/bottom",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // --- Auto Metadata AI Settings Full-screen Overlay ---
    if (showSettingsScreen) {
        AutoMetadataSettingsScreen(
            onClose = { showSettingsScreen = false },
            viewModel = viewModel
        )
    }

    // --- About Full-screen Overlay ---
    if (showAbout) {
        AboutScreen(
            onClose = { showAbout = false },
            onOpenPrivacyPolicy = {
                showAbout = false
                showPrivacyPolicy = true
            }
        )
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

    // --- Touch Effect Configuration Dialog ---
    if (showTouchEffectDialog) {
        TouchEffectDialog(
            isEnabled = isTouchEffectEnabled,
            selectedEffect = selectedTouchEffect,
            onToggleEnabled = { viewModel.setTouchEffectEnabled(it) },
            onSelectEffect = { viewModel.setSelectedTouchEffect(it) },
            onDismiss = { showTouchEffectDialog = false }
        )
    }

    // --- Global Non-blocking Touch Effect Overlay Canvas (Pass-Through) ---
    GlobalTouchOverlayCanvas(
        touchPoints = touchPoints,
        tickerTime = tickerTime,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(995f)
    )

    // --- Floating Top In-App Toast Notification (Matching App Theme, Fast & Unobtrusive) ---
    InAppToastNotification(
        message = activeToast,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 12.dp)
            .zIndex(999f)
    )
}
}

@Composable
fun InAppToastNotification(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(animationSpec = tween(150)) + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(200, easing = FastOutSlowInEasing)
        ) + scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(180)
        ) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
        modifier = modifier
    ) {
        if (!message.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = when {
                        message.contains("Error", ignoreCase = true) || message.contains("Failed", ignoreCase = true) -> Color(0xFFEF4444)
                        message.contains("Saved", ignoreCase = true) || message.contains("Injected", ignoreCase = true) || message.contains("Copied", ignoreCase = true) || message.contains("Generated", ignoreCase = true) || message.contains("Cleared", ignoreCase = true) -> Color(0xFF10B981)
                        else -> Color(0xFFF25C05)
                    }.copy(alpha = 0.8f)
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val isError = message.contains("Error", ignoreCase = true) || message.contains("Failed", ignoreCase = true)
                    val isSuccess = message.contains("Saved", ignoreCase = true) || message.contains("Injected", ignoreCase = true) || message.contains("Copied", ignoreCase = true) || message.contains("Generated", ignoreCase = true) || message.contains("Cleared", ignoreCase = true)
                    
                    val iconColor = when {
                        isError -> Color(0xFFEF4444)
                        isSuccess -> Color(0xFF10B981)
                        else -> Color(0xFFF25C05)
                    }

                    val iconVector = when {
                        isError -> Icons.Default.Info
                        isSuccess -> Icons.Default.CheckCircle
                        else -> Icons.Default.Info
                    }

                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

// =========================================================================
// --- GLOBAL TOUCH EFFECT OVERLAY & CONFIGURATION DIALOG ---
// =========================================================================

data class TouchEffectPoint(
    val id: Long,
    val x: Float,
    val y: Float,
    val timestamp: Long,
    val duration: Long = 650L,
    val effectType: String
)

@Composable
fun GlobalTouchOverlayCanvas(
    touchPoints: List<TouchEffectPoint>,
    tickerTime: Long,
    modifier: Modifier = Modifier
) {
    if (touchPoints.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val now = tickerTime
        for (pt in touchPoints) {
            val elapsed = now - pt.timestamp
            val progress = (elapsed.toFloat() / pt.duration.toFloat()).coerceIn(0f, 1f)
            val center = Offset(pt.x, pt.y)

            when (pt.effectType) {
                "Water Ripple" -> {
                    // 3 Concentric circular harmonic water waves
                    val wave1R = progress * 145f
                    val alpha1 = (1f - progress) * 0.85f
                    if (alpha1 > 0f) {
                        drawCircle(
                            color = Color(0xFF00B4D8).copy(alpha = alpha1),
                            radius = wave1R,
                            center = center,
                            style = Stroke(width = (4f * (1f - progress)).coerceAtLeast(1.2f))
                        )
                    }

                    val p2 = (progress - 0.15f).coerceAtLeast(0f) / 0.85f
                    if (p2 in 0.001f..1f) {
                        val wave2R = p2 * 110f
                        val alpha2 = (1f - p2) * 0.65f
                        drawCircle(
                            color = Color(0xFF48CAE4).copy(alpha = alpha2),
                            radius = wave2R,
                            center = center,
                            style = Stroke(width = (3f * (1f - p2)).coerceAtLeast(1f))
                        )
                    }

                    val p3 = (progress - 0.30f).coerceAtLeast(0f) / 0.70f
                    if (p3 in 0.001f..1f) {
                        val wave3R = p3 * 75f
                        val alpha3 = (1f - p3) * 0.45f
                        drawCircle(
                            color = Color(0xFF90E0EF).copy(alpha = alpha3),
                            radius = wave3R,
                            center = center,
                            style = Stroke(width = 1.8f * (1f - p3))
                        )
                    }
                }

                "Neon Sparkle" -> {
                    // Expanding central flash
                    val coreAlpha = (1f - progress) * 0.9f
                    if (coreAlpha > 0f) {
                        drawCircle(
                            color = Color(0xFFFFF066).copy(alpha = coreAlpha),
                            radius = 18f * (1f - progress),
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFFFF007F).copy(alpha = coreAlpha * 0.6f),
                            radius = 32f * (1f - progress),
                            center = center,
                            style = Stroke(width = 2f)
                        )
                    }

                    // 8 Radiating particle sparkles
                    val particleColors = listOf(
                        Color(0xFFFFEE00),
                        Color(0xFFFF007F),
                        Color(0xFF00F0FF),
                        Color(0xFF7000FF),
                        Color(0xFFFF9E00),
                        Color(0xFF00FF66),
                        Color(0xFFFF0055),
                        Color(0xFF00FFFF)
                    )
                    val starAlpha = (1f - progress).coerceIn(0f, 1f)
                    for (i in 0..7) {
                        val angle = (i * (PI * 2.0 / 8.0) + (progress * 0.7)).toFloat()
                        val dist = 10f + progress * 95f
                        val px = pt.x + cos(angle) * dist
                        val py = pt.y + sin(angle) * dist
                        val pColor = particleColors[i % particleColors.size]
                        val pSize = (6f * (1f - progress)).coerceAtLeast(1.5f)

                        drawCircle(
                            color = pColor.copy(alpha = starAlpha),
                            radius = pSize,
                            center = Offset(px, py)
                        )
                        // Cross flare on star particles
                        val flareLen = pSize * 2f
                        drawLine(
                            color = Color.White.copy(alpha = starAlpha * 0.8f),
                            start = Offset(px - flareLen, py),
                            end = Offset(px + flareLen, py),
                            strokeWidth = 1.2f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = starAlpha * 0.8f),
                            start = Offset(px, py - flareLen),
                            end = Offset(px, py + flareLen),
                            strokeWidth = 1.2f
                        )
                    }
                }

                "Duotone Wave" -> {
                    // Wave 1: Vivid Magenta Shockwave
                    val r1 = progress * 140f
                    val alpha1 = (1f - progress) * 0.85f
                    if (alpha1 > 0f) {
                        drawCircle(
                            color = Color(0xFFF72585).copy(alpha = alpha1),
                            radius = r1,
                            center = center,
                            style = Stroke(width = (6f * (1f - progress)).coerceAtLeast(1.5f))
                        )
                    }

                    // Wave 2: Electric Cyan Shockwave (Offset phase)
                    val p2 = (progress * 1.15f).coerceAtMost(1f)
                    val r2 = p2 * 115f
                    val alpha2 = (1f - p2) * 0.8f
                    if (alpha2 > 0f) {
                        drawCircle(
                            color = Color(0xFF00F5D4).copy(alpha = alpha2),
                            radius = r2,
                            center = center,
                            style = Stroke(width = (5f * (1f - p2)).coerceAtLeast(1.2f))
                        )
                    }

                    // Center duotone glow
                    val coreAlpha = (1f - progress) * 0.4f
                    if (coreAlpha > 0f) {
                        drawCircle(
                            color = Color(0xFF7209B7).copy(alpha = coreAlpha),
                            radius = 28f * (1f - progress),
                            center = center
                        )
                    }
                }

                else -> { // "Glowing Ring" (Default)
                    val ringRadius = 12f + progress * 125f
                    val ringAlpha = (1f - progress) * 0.9f
                    if (ringAlpha > 0f) {
                        // Outer Neon Cyan Ring
                        drawCircle(
                            color = Color(0xFF38BDF8).copy(alpha = ringAlpha),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = (7f * (1f - progress)).coerceAtLeast(1.5f))
                        )
                        // Ambient Electric Violet Glow Ring
                        drawCircle(
                            color = Color(0xFF818CF8).copy(alpha = ringAlpha * 0.6f),
                            radius = (ringRadius - 4f).coerceAtLeast(0f),
                            center = center,
                            style = Stroke(width = (4f * (1f - progress)).coerceAtLeast(1f))
                        )
                        // Center Core Dot
                        val coreR = 8f * (1f - progress)
                        if (coreR > 0f) {
                            drawCircle(
                                color = Color.White.copy(alpha = ringAlpha),
                                radius = coreR,
                                center = center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TouchEffectDialog(
    isEnabled: Boolean,
    selectedEffect: String,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectEffect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val effects = listOf(
        Pair("Glowing Ring", "Gelombang cincin neon futuristik (Default)"),
        Pair("Water Ripple", "Riak gelombang air multi-layer konsentris"),
        Pair("Neon Sparkle", "Letupan partikel bintang neon berkilau"),
        Pair("Duotone Wave", "Pulsasi gelombang halo kontras duotone")
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = Color(0xFF38BDF8))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFFF472B6).copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, Color(0xFFF472B6).copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color(0xFFF472B6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Touch Effect",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Kustomisasi animasi sentuhan layar",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                // Switch Section (Global Toggle)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, if (isEnabled) Color(0xFF38BDF8).copy(alpha = 0.4f) else Color(0x20FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Global Touch Effect",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isEnabled) "Efek sentuhan aktif" else "Efek sentuhan nonaktif",
                                color = if (isEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { onToggleEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00A8FF),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section Label
                Text(
                    text = "PILIHAN EFEK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) Color(0xFF38BDF8) else Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Radio Options List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    effects.forEach { (name, desc) ->
                        val isSelected = isEnabled && selectedEffect == name
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                !isEnabled -> Color(0xFF1E293B).copy(alpha = 0.25f)
                                isSelected -> Color(0xFF00A8FF).copy(alpha = 0.15f)
                                else -> Color(0xFF1E293B).copy(alpha = 0.4f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    !isEnabled -> Color(0x15FFFFFF)
                                    isSelected -> Color(0xFF00A8FF).copy(alpha = 0.7f)
                                    else -> Color(0x25FFFFFF)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isEnabled) {
                                    onSelectEffect(name)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = if (isEnabled) { { onSelectEffect(name) } } else null,
                                    enabled = isEnabled,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF00A8FF),
                                        unselectedColor = Color(0xFF64748B),
                                        disabledSelectedColor = Color(0xFF475569),
                                        disabledUnselectedColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isEnabled) Color.White else Color(0xFF64748B)
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 10.5.sp,
                                        color = if (isEnabled) Color(0xFF94A3B8) else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Clean Close Icon in top-right corner of container (no circle button background)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoMetadataSettingsScreen(
    onClose: () -> Unit,
    viewModel: MainViewModel
) {
    BackHandler { onClose() }
    val focusManager = LocalFocusManager.current
    val geminiKey by viewModel.geminiKey.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val isOfflineMode by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val titleCharLimit by viewModel.titleCharLimit.collectAsStateWithLifecycle()
    val descCharLimit by viewModel.descCharLimit.collectAsStateWithLifecycle()
    val keywordsLimit by viewModel.keywordsLimit.collectAsStateWithLifecycle()
    val blacklistWords by viewModel.blacklistWords.collectAsStateWithLifecycle()
    val promptConcept by viewModel.promptConcept.collectAsStateWithLifecycle()
    val isAutoInjectionEnabled by viewModel.isAutoInjectionEnabled.collectAsStateWithLifecycle()

    val blacklistSaveColor = if (blacklistWords.isNotBlank()) Color(0xFF22C55E) else Color(0xFF64748B)
    val conceptSaveColor = if (promptConcept.isNotBlank()) Color(0xFF22C55E) else Color(0xFF64748B)

    var tempGeminiKey by remember(geminiKey) { mutableStateOf(geminiKey) }
    var keyVisibility by remember { mutableStateOf(false) }

    var titleInput by remember(titleCharLimit) { mutableStateOf(titleCharLimit.toInt().toString()) }
    var descInput by remember(descCharLimit) { mutableStateOf(descCharLimit.toInt().toString()) }
    var keywordsInput by remember(keywordsLimit) { mutableStateOf(keywordsLimit.toInt().toString()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070E20))
            .clickable(enabled = true, onClick = { focusManager.clearFocus() })
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
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Settings - Auto Metadata AI",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Pengaturan War Engine AI & Injeksi Metadata",
                        color = Color(0xFF93C5FD),
                        fontSize = 11.sp
                    )
                }
            }

            // Scrollable Content Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card AUTO METADATA AI
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F172A)
                    ),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF00A8FF),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "WAR ENGINE CONFIGURATION",
                                    color = Color(0xFF00A8FF),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Surface(
                                color = if (geminiKey.isNotEmpty()) Color(0xFF22C55E).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (geminiKey.isNotEmpty()) Color(0xFF22C55E) else Color(0xFFEF4444))
                            ) {
                                Text(
                                    text = if (geminiKey.isNotEmpty()) "ACTIVE" else "API MISSING",
                                    color = if (geminiKey.isNotEmpty()) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Model Selection
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
                                    text = "Model AI Aktif:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = currentModelLabel,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
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
                                    Text("Ganti Model", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
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

                        // API Key Input
                        OutlinedTextField(
                            value = tempGeminiKey,
                            onValueChange = { tempGeminiKey = it },
                            enabled = !isOfflineMode,
                            label = { Text("API Key Google Gemini") },
                            placeholder = { Text("Masukkan API Key Gemini Anda...", color = Color(0xFF64748B)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
                                            IconButton(onClick = { tempGeminiKey = "" }) {
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
                            modifier = Modifier.fillMaxWidth().testTag("api_key_field"),
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
                            isOfflineMode -> Color(0xFF64748B) to "DISABLE"
                            tempGeminiKey.isEmpty() -> Color(0xFF64748B) to "INPUT API"
                            tempGeminiKey != geminiKey -> Color(0xFF22C55E) to "SAVE API"
                            else -> Color(0xFF00A8FF) to "ACTIVE"
                        }

                        Button(
                            onClick = { viewModel.saveApiKey(tempGeminiKey) },
                            enabled = !isOfflineMode && tempGeminiKey.isNotEmpty() && tempGeminiKey != geminiKey,
                            colors = ButtonDefaults.buttonColors(containerColor = apiBtnBg, disabledContainerColor = apiBtnBg),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp).testTag("api_save_btn")
                        ) {
                            Text(apiBtnText, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text("Metadata Character & Limit Configuration", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(10.dp))

                        // Title Limit
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Title Limit:", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(65.dp))
                            OutlinedTextField(
                                value = titleInput,
                                onValueChange = { newValue ->
                                    titleInput = newValue
                                    newValue.toFloatOrNull()?.let { num ->
                                        if (num in 10f..150f) viewModel.setTitleCharLimit(num)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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

                        Spacer(modifier = Modifier.height(6.dp))

                        // Desc Limit
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Desc Limit:", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(65.dp))
                            OutlinedTextField(
                                value = descInput,
                                onValueChange = { newValue ->
                                    descInput = newValue
                                    newValue.toFloatOrNull()?.let { num ->
                                        if (num in 50f..200f) viewModel.setDescCharLimit(num)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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

                        Spacer(modifier = Modifier.height(6.dp))

                        // Keywords Limit
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Keywords:", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(65.dp))
                            OutlinedTextField(
                                value = keywordsInput,
                                onValueChange = { newValue ->
                                    keywordsInput = newValue
                                    newValue.toFloatOrNull()?.let { num ->
                                        if (num in 10f..50f) viewModel.setKeywordsLimit(num)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Blacklist Words
                        OutlinedTextField(
                            value = blacklistWords,
                            onValueChange = { viewModel.setBlacklistWords(it) },
                            label = { Text("Blacklist Words") },
                            placeholder = { Text("ex: vector, illustration, abstract", color = Color(0xFF64748B)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                if (blacklistWords.isNotBlank()) {
                                    viewModel.saveBlacklistWordsPermanent()
                                }
                            }),
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
                            modifier = Modifier.fillMaxWidth().testTag("blacklist_words_field"),
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

                        // Prompt Concept / Kata Kunci Inti
                        OutlinedTextField(
                            value = promptConcept,
                            onValueChange = { viewModel.setPromptConcept(it) },
                            label = { Text("Kata Kunci Inti / Deskripsi Singkat") },
                            placeholder = { Text("Contoh: laptop di meja kayu minimalis, aesthetic lighting...", color = Color(0xFF64748B)) },
                            maxLines = 2,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                if (promptConcept.isNotBlank()) {
                                    viewModel.savePromptConceptPermanent()
                                }
                            }),
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
                            modifier = Modifier.fillMaxWidth().testTag("concept_prompt_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color(0xFFE2E8F0),
                                focusedLabelColor = Color(0xFF00A8FF),
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                focusedBorderColor = Color(0xFF00A8FF),
                                unfocusedBorderColor = Color(0x40FFFFFF)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Auto Injection Row
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
            }
        }
    }
}

@Composable
fun AboutScreen(
    onClose: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070E20))
            .clickable(enabled = true, onClick = {}) // Block clicks from passing through
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
                    .padding(horizontal = 8.dp, vertical = 12.dp),
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
                        text = "TENTANG APLIKASI",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "War Machine Hybrid v2.1.2 Pro",
                        color = Color(0xFFBAE6FD),
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Halaman About",
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
                // 1. App Identity Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFF1E1435),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(listOf(Color(0xFFC084FC), Color(0xFFA855F7)))
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = "Pro Edition",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = TextStyle(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFFF3E8FF), Color(0xFFD8B4FE), Color(0xFFF472B6))
                                    ),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }

                        Text(
                            text = "WAR MACHINE HYBRID",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AI-Powered Microstock Metadata & Vector Conversion Engine",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "War Machine Hybrid adalah solusi komprehensif untuk microstocker dan desainer vektor. Dilengkapi dengan War Engine berbasis Gemini AI untuk visual recognition cerdas, injeksi metadata XMP otomatis ke berbagai format, serta konverter SVG ke Adobe Illustrator AI-Compatible EPS berstandar microstock.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Justify
                        )
                    }
                }

                // 2. Petunjuk Penggunaan AI (Dipindahkan dari Halaman Utama)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF00A8FF).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF00A8FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Petunjuk Penggunaan AI",
                                color = Color(0xFF00A8FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val instructions = listOf(
                            "1. Masukkan API Key Gemini pada kolom War Engine / Gemini API, lalu klik SAVE API untuk mengaktifkan fitur cerdas.",
                            "2. Import gambar yang ingin diproses melalui tombol Import Images (mendukung format PNG, JPG, SVG, dan EPS).",
                            "3. Konfigurasi preferensi Auto Inject Metadata (Default ON) dan opsi seleksi sesuai kebutuhan Anda.",
                            "4. Klik tombol GENERATE BATCH (atau GENERATE jika satu gambar) pada Bar Bawah untuk memulai analisis visual dan pembuatan metadata secara otomatis.",
                            "5. Periksa hasil judul, deskripsi, dan keywords. Klik card gambar untuk membuka jendela detail mandiri guna mengedit atau menyesuaikan kata kunci.",
                            "6. Klik tombol Download All atau simpan per gambar untuk memperoleh file berinjeksi metadata XMP siap upload ke pasar microstock."
                        )

                        instructions.forEach { step ->
                            Text(
                                text = step,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    uriHandler.openUri("https://aistudio.google.com/app/apikey")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dapatkan Gemini API Key di Google AI Studio ➔",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3. Fitur Utama & Keunggulan
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Fitur Utama",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val features = listOf(
                            "• Injeksi Metadata XMP Standar Industri: Menyimpan Title, Description, Keywords, dan Creator langsung ke dalam file EPS, SVG, PNG, dan JPEG.",
                            "• SVG to Adobe Illustrator EPS: Mengonversi vektor SVG menjadi format EPS Illustrator 8.0 dengan PostScript Shading (ShadingType 2 & 3) native dan hirarki grup terstruktur.",
                            "• Standar Resolusi Microstock: Artboard otomatis diskalakan ke ukuran optimal (≥ 16 MP) guna memenuhi regulasi Adobe Stock, Shutterstock, Freepik, dan platform terkemuka.",
                            "• Analisis Trademark & Demand Score: Memeriksa keamanan kata kunci dari pelanggaran merek dagang dan memberikan rekomendasi demand score."
                        )

                        features.forEach { feat ->
                            Text(
                                text = feat,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                }

                // 4. Informasi Pengembang, Website, & Lisensi (Dipindahkan dari Footer Halaman Utama)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Informasi Pengembang & Layanan",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0x4038BDF8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            Uri.parse("https://masbonet.blogspot.com/?m=1")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Tidak dapat membuka link", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = Color(0xFF00A8FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "www.masbonet.com",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Text(
                            text = "Designed by Irwan Setiadi",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "war machine hybrid app version 2.1.2",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = onOpenPrivacyPolicy,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Buka Privacy Policy",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "© 2026 War Machine Hybrid. All rights reserved.",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070E20))
            .clickable(enabled = true, onClick = {}) // Block clicks from passing through
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar matching App Theme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0A2558), Color(0xFF1D4ED8), Color(0xFF00A8FF))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
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
                        text = "PRIVACY POLICY",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "War Machine Hybrid v2.1.2 Pro",
                        color = Color(0xFFBAE6FD),
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Privacy Policy",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Scrollable Policy Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x5538BDF8)),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = "Version 2.1.2 • October 2026",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "KEBIJAKAN PRIVASI",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Komitmen Keamanan & Privasi Data Pengguna",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "War Machine Hybrid dirancang dengan prinsip Zero-Knowledge dan Local-First Architecture. Kami menghargai hak privasi, kerahasiaan karya seni komersial, dan kedaulatan data Anda. Kebijakan ini menjelaskan bagaimana data diproses secara aman dalam aplikasi versi 2.1.2.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Justify
                        )
                    }
                }

                // Section 1: Tanpa Pengumpulan Data Pribadi
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "1. Tanpa Pengumpulan Data & Pelacakan (Zero Tracking)",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "War Machine Hybrid TIDAK mengumpulkan, menyimpan, membuat profil, atau menjual informasi pribadi Anda. Aplikasi ini:\n" +
                                    "• Tidak memerlukan pendaftaran akun, login pihak ketiga, nomor telepon, atau identitas pribadi.\n" +
                                    "• Bebas dari SDK iklan pihak ketiga, pelacak analitik, crash-reporting eksternal, atau background telemetry.\n" +
                                    "• Tidak memantau kebiasaan penggunaan atau aktivitas kreatif Anda.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 2: War Engine AI & Gemini API
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "2. Integrasi War Engine AI & Kunci API Gemini",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Fitur analisis visual dan pembuatan metadata otomatis memanfaatkan Gemini API (War Engine):\n" +
                                    "• API Key pribadi Anda disimpan secara aman dan terenkripsi di penyimpanan lokal perangkat (SharedPreferences privat). API Key tersebut TIDAK PERNAH dikirim ke server pengembang atau pihak ketiga mana pun.\n" +
                                    "• Saat proses Generate metadata, gambar dikirim secara langsung dari perangkat Anda ke endpoint resmi Google Gemini API melalui koneksi aman HTTPS terenkripsi. Tidak ada server perantara yang menyimpan atau mencegat berkas Anda.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 3: Konversi Vektor & Rendering Lokal
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "3. Pemrosesan Vektor & Konversi 100% On-Device",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Semua modul manipulasi grafis bekerja sepenuhnya secara lokal (on-device) pada CPU/GPU perangkat Anda:\n" +
                                    "• Konverter SVG ke Adobe Illustrator AI-Compatible EPS (PostScript ShadingType 2 & 3, compound path, dan hierarki grup) diproses langsung tanpa cloud server.\n" +
                                    "• Mesin rendering EPS, decoder TIFF, dan ekstraksi bitmap bekerja sepenuhnya di dalam memori lokal aplikasi.\n" +
                                    "• Desain vektor orisinal Anda tidak pernah meninggalkan perangkat.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 4: Injeksi Metadata XMP & Kepemilikan Hak Cipta
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "4. Injeksi Metadata XMP & Kepemilikan Penuh Hak Cipta",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Injeksi metadata standar industri (Title, Description, Keywords, Creator) ditulis langsung ke struktur internal file (EPS, SVG, PNG, JPG) di penyimpanan lokal Anda.\n" +
                                    "• Pengguna memegang 100% hak cipta, kepemilikan komersial, dan hak distribusi atas seluruh karya seni serta metadata yang dihasilkan.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 5: Izin Akses Berkas & Storage Access Framework
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "5. Izin Penyimpanan & Akses Berkas",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aplikasi mengadopsi standar keamanan Android modern (Photo Picker & Storage Access Framework):\n" +
                                    "• Aplikasi hanya membaca berkas media yang Anda pilih secara eksplisit untuk diproses.\n" +
                                    "• Hasil ekspor gambar dan file berinjeksi disimpan langsung ke folder unduhan atau lokasi penyimpanan yang Anda tentukan sendiri tanpa memerlukan izin akses luas yang invasif.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 6: Kontrol & Penghapusan Data Mandiri
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "6. Kontrol & Penghapusan Data Mandiri",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Anda memiliki kontrol penuh atas berkas yang diimpor. Kapan pun Anda dapat menekan opsi 'Clear All' di menu header untuk membersihkan seluruh antrean dan memori lokal aplikasi secara instan.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 7: Pengembang & Kontak
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "7. Kontak & Pengembang",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Text(
                            text = "Jika Anda memiliki pertanyaan mengenai kebijakan privasi atau aplikasi ini, silakan hubungi kami:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0x4038BDF8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            Uri.parse("https://masbonet.blogspot.com/?m=1")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Tidak dapat membuka link", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = Color(0xFF00A8FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "www.masbonet.com",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        Text(
                            text = "Email: irwansetiadi46@gmail.com",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                try {
                                    val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:irwansetiadi46@gmail.com")
                                    }
                                    context.startActivity(mailIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Tidak ada aplikasi email", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        Text(
                            text = "Designed & Developed by Irwan Setiadi",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )

                        Text(
                            text = "© 2026 War Machine Hybrid v2.1.2. All rights reserved.",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
            color = Color(0xFF38BDF8),
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
// --- PROCESS STATUS BADGE (Processing, Waiting, Success, Failed) ---
// =========================================================================

@Composable
fun ImageProcessStatusBadge(
    status: ProcessStatus,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val effectiveStatus = if (isGenerating) ProcessStatus.PROCESSING else status
    if (effectiveStatus == ProcessStatus.IDLE) return

    val bgColor: Color
    val borderColor: Color
    val textColor: Color
    val labelText: String
    val iconVector: ImageVector?

    when (effectiveStatus) {
        ProcessStatus.PROCESSING -> {
            bgColor = Color(0xEE0284C7)
            borderColor = Color(0xFF38BDF8)
            textColor = Color.White
            labelText = "Processing…"
            iconVector = null
        }
        ProcessStatus.WAITING -> {
            bgColor = Color(0xEE334155)
            borderColor = Color(0xFF94A3B8)
            textColor = Color(0xFFF1F5F9)
            labelText = "Waiting…"
            iconVector = null
        }
        ProcessStatus.SUCCESS -> {
            bgColor = Color(0xEE16A34A)
            borderColor = Color(0xFF4ADE80)
            textColor = Color.White
            labelText = "Success"
            iconVector = Icons.Default.CheckCircle
        }
        ProcessStatus.FAILED -> {
            bgColor = Color(0xEEDC2626)
            borderColor = Color(0xFFF87171)
            textColor = Color.White
            labelText = "Failed"
            iconVector = Icons.Default.ErrorOutline
        }
        ProcessStatus.IDLE -> return
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(if (isCompact) 4.dp else 6.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isCompact) 5.dp else 8.dp,
                vertical = if (isCompact) 2.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (effectiveStatus == ProcessStatus.PROCESSING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isCompact) 9.dp else 12.dp),
                    color = Color.White,
                    strokeWidth = if (isCompact) 1.5.dp else 2.dp
                )
                Spacer(modifier = Modifier.width(if (isCompact) 3.dp else 5.dp))
            } else if (effectiveStatus == ProcessStatus.WAITING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isCompact) 8.dp else 11.dp),
                    color = Color(0xFFCBD5E1),
                    strokeWidth = if (isCompact) 1.2.dp else 1.8.dp
                )
                Spacer(modifier = Modifier.width(if (isCompact) 3.dp else 5.dp))
            } else if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (isCompact) 9.dp else 13.dp)
                )
                Spacer(modifier = Modifier.width(if (isCompact) 3.dp else 5.dp))
            }

            Text(
                text = labelText,
                color = textColor,
                fontSize = if (isCompact) 8.sp else 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
            when {
                item.processStatus == ProcessStatus.FAILED -> Color(0xFFEF4444)
                item.processStatus == ProcessStatus.PROCESSING || item.isGeneratingMetadata -> Color(0xFF00A8FF)
                item.hasMetadata || item.processStatus == ProcessStatus.SUCCESS -> Color(0xFF22C55E).copy(alpha = 0.8f)
                else -> Color(0x3038BDF8)
            }
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

            // Bottom-Left Process Status Badge (Processing…, Waiting…, Success, Failed)
            if (item.processStatus != ProcessStatus.IDLE || item.isGeneratingMetadata) {
                ImageProcessStatusBadge(
                    status = item.processStatus,
                    isGenerating = item.isGeneratingMetadata,
                    isCompact = true,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 22.dp)
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
                            when {
                                item.processStatus == ProcessStatus.FAILED -> Color(0xFFEF4444)
                                item.processStatus == ProcessStatus.PROCESSING || item.isGeneratingMetadata -> Color(0xFF00A8FF)
                                item.hasMetadata || item.processStatus == ProcessStatus.SUCCESS -> Color(0xFF22C55E).copy(alpha = 0.6f)
                                else -> Color(0x33FFFFFF)
                            },
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

                    // Bottom-Left Process Status Badge (Processing…, Waiting…, Success, Failed)
                    if (item.processStatus != ProcessStatus.IDLE || item.isGeneratingMetadata) {
                        ImageProcessStatusBadge(
                            status = item.processStatus,
                            isGenerating = item.isGeneratingMetadata,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
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
    val focusManager = LocalFocusManager.current
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
                            when {
                                item.processStatus == ProcessStatus.FAILED -> Color(0xFFEF4444)
                                item.processStatus == ProcessStatus.PROCESSING || item.isGeneratingMetadata -> Color(0xFF00A8FF)
                                item.hasMetadata || item.processStatus == ProcessStatus.SUCCESS -> Color(0xFF22C55E).copy(alpha = 0.6f)
                                else -> Color(0x33FFFFFF)
                            },
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

                    // Bottom-Left Process Status Badge (Processing…, Waiting…, Success, Failed)
                    if (item.processStatus != ProcessStatus.IDLE || item.isGeneratingMetadata) {
                        ImageProcessStatusBadge(
                            status = item.processStatus,
                            isGenerating = item.isGeneratingMetadata,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row: Delete metadata, GENERATE / CANCEL, INJECT, DOWNLOAD (Positioned under Image, above Title)
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
                            viewModel.showToast("Cleared")
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
                val isProcessingThis = item.isGeneratingMetadata || item.processStatus == ProcessStatus.PROCESSING
                val isWaitingThis = item.processStatus == ProcessStatus.WAITING
                Button(
                    onClick = {
                        if (isProcessingThis || isWaitingThis) {
                            viewModel.cancelIndividualGeneration(item.id)
                        } else {
                            viewModel.generateMetadataForSingleImage(item.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isProcessingThis -> Color(0xFFEF4444)
                            isWaitingThis -> Color(0xFF475569)
                            item.processStatus == ProcessStatus.FAILED -> Color(0xFF00A8FF)
                            else -> Color(0xFF00A8FF)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    if (isProcessingThis) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else if (isWaitingThis) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), color = Color.White, strokeWidth = 1.8.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Icon(
                            imageVector = if (item.processStatus == ProcessStatus.FAILED) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.processStatus == ProcessStatus.FAILED) "RETRY" else "GENERATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
                            viewModel.showToast("Copied")
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
                            viewModel.showToast("Copied")
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

                    // Right side: Clear text (only when keywords are not empty) and Copy button
                    if (effectiveKeywords.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Clear",
                                color = Color(0xFFEF4444),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.clearKeywordsFromImage(item.id)
                                        viewModel.showToast("Cleared")
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val textToCopy = effectiveKeywords.joinToString(",") { it.word }
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                                    viewModel.showToast("Copied")
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
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Add Keyword (di atas keywords tepat di bawah text Keywords)
                var newKwText by remember(item.id) { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = newKwText,
                        onValueChange = { newKwText = it },
                        placeholder = { Text("Tambah keyword (pisahkan dengan koma)...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (newKwText.isNotBlank()) {
                                val words = newKwText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                words.forEach { w -> viewModel.addKeywordToImage(item.id, w) }
                                newKwText = ""
                            }
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00A8FF),
                            unfocusedBorderColor = Color(0x33FFFFFF)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8FF)),
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                        text = "Belum ada keywords. Klik GENERATE atau ketik di atas.",
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
                                        viewModel.showToast("Replaced")
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



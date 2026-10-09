package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ApiDatabaseScreen(
    onClose: () -> Unit,
    viewModel: MainViewModel
) {
    BackHandler { onClose() }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    val isMultiApiEnabled by viewModel.isMultiApiEnabled.collectAsStateWithLifecycle()
    val apiKeysList by viewModel.apiKeysList.collectAsStateWithLifecycle()
    val activeApiKeyIndex by viewModel.activeApiKeyIndex.collectAsStateWithLifecycle()
    val currentActiveKey by viewModel.geminiKey.collectAsStateWithLifecycle()

    var inputKeyText by remember { mutableStateOf("") }
    var showInputKeyVisibility by remember { mutableStateOf(false) }
    var showClearConfirmationDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070E20))
            .clickable(onClick = {
                keyboardController?.hide()
                focusManager.clearFocus()
            })
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Header Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0A2558), Color(0xFF6B21A8), Color(0xFFA855F7))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("api_db_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali ke Beranda",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = Color(0xFFE9D5FF),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "API DATABASE",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Multi API Key Management & Failover",
                        color = Color(0xFFE9D5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                // --- 1. Multi API ON/OFF Switch Card ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("multi_api_switch_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMultiApiEnabled) Color(0xFF161938) else Color(0xFF101932)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isMultiApiEnabled) Color(0xFFA855F7) else Color(0xFF1E293B)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Multi API Mode",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isMultiApiEnabled) Color(0xFF22C55E).copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, if (isMultiApiEnabled) Color(0xFF22C55E) else Color(0xFF64748B))
                                    ) {
                                        Text(
                                            text = if (isMultiApiEnabled) "ON (AUTO-FAILOVER)" else "OFF",
                                            color = if (isMultiApiEnabled) Color(0xFF22C55E) else Color(0xFF94A3B8),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = if (isMultiApiEnabled)
                                        "Ketika terjadi limit kuota atau error API saat proses generate, sistem otomatis beralih ke API Key berikutnya dan melanjutkan tugas dari titik terjadinya error."
                                    else
                                        "Multi API dinonaktifkan. Sistem hanya menggunakan satu API Key aktif secara standar tanpa pengalihan otomatis.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = isMultiApiEnabled,
                                onCheckedChange = { viewModel.setMultiApiEnabled(it) },
                                modifier = Modifier.testTag("multi_api_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFA855F7),
                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                    uncheckedTrackColor = Color(0xFF1E293B)
                                )
                            )
                        }
                    }
                }

                // --- 2. Input API Key Field & +Add Button ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101932)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "TAMBAH API KEY",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inputKeyText,
                                    onValueChange = { inputKeyText = it },
                                    label = { Text("API Key Google Gemini", fontSize = 12.sp) },
                                    placeholder = {
                                        Text(
                                            "Tempel API Key baru di sini...",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.5.sp
                                        )
                                    },
                                    singleLine = true,
                                    visualTransformation = if (showInputKeyVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (inputKeyText.isNotEmpty()) {
                                                IconButton(onClick = { inputKeyText = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = "Hapus Input",
                                                        tint = Color(0xFF94A3B8),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { showInputKeyVisibility = !showInputKeyVisibility }) {
                                                Icon(
                                                    imageVector = if (showInputKeyVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle Visibility",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFA855F7),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedLabelColor = Color(0xFFA855F7),
                                        unfocusedLabelColor = Color(0xFF94A3B8)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("api_input_field")
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        if (inputKeyText.isNotBlank()) {
                                            viewModel.addApiKey(inputKeyText)
                                            inputKeyText = ""
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                    },
                                    enabled = inputKeyText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFA855F7),
                                        disabledContainerColor = Color(0xFF334155)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("add_api_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Add",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 3. Stored API Keys List Header ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LIST API KEY",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF334155)
                            ) {
                                Text(
                                    text = "${apiKeysList.size}",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (apiKeysList.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showClearConfirmationDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFEF4444)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("clear_api_list_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear List", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // --- 4. Stored API Items or Empty State ---
                if (apiKeysList.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyOff,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Belum Ada API Key Tersimpan",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Masukkan API Key di kolom input atas atau simpan API Key dari halaman Settings / War Engine.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(apiKeysList) { index, key ->
                        val isActive = (index == activeApiKeyIndex) || (key == currentActiveKey)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_item_card_$index")
                                .clickable {
                                    viewModel.selectActiveApiKey(index)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) Color(0xFF1E1438) else Color(0xFF101932)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.5.dp,
                                if (isActive) Color(0xFFA855F7) else Color(0xFF1E293B)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = { viewModel.selectActiveApiKey(index) },
                                    modifier = Modifier.testTag("api_radio_btn_$index"),
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFA855F7),
                                        unselectedColor = Color(0xFF64748B)
                                    )
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "API Key #${index + 1}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF22C55E).copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, Color(0xFF22C55E))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = Color(0xFF22C55E),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "ACTIVE",
                                                        color = Color(0xFF22C55E),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF334155)
                                            ) {
                                                Text(
                                                    text = "STANDBY",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val maskedKey = if (key.length > 10) {
                                        "${key.take(6)}••••••••${key.takeLast(4)}"
                                    } else {
                                        "••••••••"
                                    }

                                    Text(
                                        text = maskedKey,
                                        color = if (isActive) Color(0xFFE9D5FF) else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.removeApiKey(index) },
                                    modifier = Modifier.testTag("api_delete_btn_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus API",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 5. Information & Guide Box ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PETUNJUK MULTI API",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "• Radio Button menentukan API Key mana yang saat ini aktif digunakan.\n" +
                                        "• Jika hanya ada satu API, maka otomatis aktif dan terinput ke Settings/War Engine.\n" +
                                        "• Jika Multi API ON, saat kuota habis atau error terjadi, proses generate akan beralih ke API Key berikutnya (Api Switch) dan langsung melanjutkan proses dari titik terjadinya error tanpa mengulang dari awal.\n" +
                                        "• Menyimpan API di halaman Settings/War Engine juga otomatis mendaftarkannya ke list ini tanpa duplikasi.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Confirmation Dialog for Clear List ---
    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            title = {
                Text(
                    text = "Hapus Semua API Key?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Seluruh daftar API Key yang tersimpan akan dikosongkan. Anda perlu menginput kembali API Key untuk menggunakan fitur AI.",
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmationDialog = false
                        viewModel.clearApiKeysList()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Hapus Semua", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmationDialog = false }) {
                    Text("Batal", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

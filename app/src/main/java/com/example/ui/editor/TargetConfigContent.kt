package com.example.ui.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class TargetConfigData(
    val name: String,
    val actionType: String,
    val holdDurationMs: Long,
    val allowMultiMatch: Boolean,
    val threshold: Float,
    val delayAfterTapMs: Long,
    val maxTaps: Int,
    val priority: Int,
    val timeoutSeconds: Int,
    val restrictRegion: Boolean,
    val regionX: Int,
    val regionY: Int,
    val regionWidth: Int,
    val regionHeight: Int,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

@Composable
fun TargetConfigContent(
    croppedBitmap: Bitmap,
    defaultPriority: Int,
    onSaveTarget: (TargetConfigData) -> Unit
) {
    val context = LocalContext.current
    var targetName by remember { mutableStateOf("Target_${System.currentTimeMillis() % 10000}") }
    var threshold by remember { mutableFloatStateOf(0.8f) }
    var delayAfterTap by remember { mutableStateOf("1000") }
    var maxTaps by remember { mutableStateOf("0") }
    var priority by remember { mutableStateOf(defaultPriority.toString()) }
    var restrictRegion by remember { mutableStateOf(false) }
    var regX by remember { mutableStateOf("0") }
    var regY by remember { mutableStateOf("0") }
    var regW by remember { mutableStateOf("300") }
    var regH by remember { mutableStateOf("300") }
    var timeoutSecsText by remember { mutableStateOf("10") }
    var actionType by remember { mutableStateOf("TAP") }
    var holdDurationMsText by remember { mutableStateOf("2000") }
    var allowMultiMatch by remember { mutableStateOf(false) }
    var offsetX by remember { mutableIntStateOf(0) }
    var offsetY by remember { mutableIntStateOf(0) }
    var offsetXText by remember { mutableStateOf("0") }
    var offsetYText by remember { mutableStateOf("0") }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyberCyan,
        unfocusedBorderColor = BorderSlate,
        focusedLabelColor = CyberCyan,
        unfocusedLabelColor = TextMuted,
        cursorColor = CyberCyan,
        focusedTextColor = Color.White,
        unfocusedTextColor = TextLight,
        focusedContainerColor = SlateCardBg.copy(alpha = 0.3f),
        unfocusedContainerColor = SlateCardBg.copy(alpha = 0.15f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Review Hasil Pemotongan & Konfigurasi",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White,
            letterSpacing = (-0.5).sp
        )

        // Preview Cropped Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(1.dp, BorderSlate, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = croppedBitmap.asImageBitmap(),
                contentDescription = "Preview Crop",
                modifier = Modifier.wrapContentSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Configuration Form fields
        OutlinedTextField(
            value = targetName,
            onValueChange = { targetName = it },
            label = { Text("Nama Target (cth: Tombol Mulai)") },
            colors = textFieldColors,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Tipe Tindakan Otomatis Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlateCardBg)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tipe Tindakan Otomatis",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                val actionTypes = listOf(
                    Triple("TAP", "Ketuk (Click)", "Simulasi sentuhan biasa sekali ketuk di tengah gambar."),
                    Triple("LONG_PRESS", "Tekan & Tahan", "Berguna untuk menurunkan pasukan game (hold)."),
                    Triple("WAIT_ONLY", "Tunggu Saja", "Hanya menunggu gambar muncul lalu lanjut tanpa menyentuh."),
                    Triple("WAIT_DISAPPEAR", "Tunggu Gambar Hilang", "Menunggu layar berubah sampai gambar ini tidak ada lagi.")
                )

                actionTypes.forEach { (type, title, desc) ->
                    val isSelected = actionType == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CyberCyan.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CyberCyan else BorderSlate.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { actionType = type }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { actionType = type },
                            colors = RadioButtonDefaults.colors(selectedColor = CyberCyan, unselectedColor = TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, fontWeight = FontWeight.Bold, color = if (isSelected) CyberCyan else Color.White, fontSize = 13.sp)
                            Text(text = desc, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (actionType == "LONG_PRESS") {
            OutlinedTextField(
                value = holdDurationMsText,
                onValueChange = { holdDurationMsText = it },
                label = { Text("Durasi Tekan Tahan (ms)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = textFieldColors,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Multi-Buy Shop Mode Toggle Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (allowMultiMatch) CyberCyan else BorderSlate, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlateCardBg)
        ) {
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mode Multi-Beli Toko (Multi-Match)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (allowMultiMatch) CyberCyan else Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Jika di layar muncul beberapa item serupa sekaligus (cth: 2 kartu EXP), ketuk semuanya berurutan sebelum lanjut ke Reroll.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = allowMultiMatch,
                    onCheckedChange = { allowMultiMatch = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = CyberCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SlateCardBg
                    )
                )
            }
        }

        // Threshold Slider
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlateCardBg)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Threshold Confidence (Akurasi): ${String.format("%.2f", threshold)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    valueRange = 0.5f..1.0f,
                    colors = SliderDefaults.colors(activeTrackColor = CyberCyan, thumbColor = CyberCyan)
                )
                Text(
                    "Makin besar angkanya, makin ketat deteksi visual gambarnya.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        OutlinedTextField(
            value = delayAfterTap,
            onValueChange = { delayAfterTap = it },
            label = { Text("Delay Setelah Click (ms)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = textFieldColors,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = maxTaps,
            onValueChange = { maxTaps = it },
            label = { Text("Maks Ketukan Sesi (0 = Tanpa Batas)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = textFieldColors,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = priority,
            onValueChange = { priority = it },
            label = { Text("Urutan Step Sequence (0 = Step Pertama, 1 = Step Kedua, dst)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = textFieldColors,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = timeoutSecsText,
            onValueChange = { timeoutSecsText = it },
            label = { Text("Timeout Gambar Tidak Ditemukan (Detik)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = textFieldColors,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Restrict Region toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Batasi Area Pencarian", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Mencari di area koordinat tertentu (lebih hemat baterai & cepat)", fontSize = 11.sp, color = TextMuted)
            }
            Switch(
                checked = restrictRegion,
                onCheckedChange = { restrictRegion = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberCyan,
                    checkedTrackColor = CyberCyan.copy(alpha = 0.3f)
                )
            )
        }

        if (restrictRegion) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateCardBg)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Koordinat Region Layar (px)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = regX,
                            onValueChange = { regX = it },
                            label = { Text("X") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = regY,
                            onValueChange = { regY = it },
                            label = { Text("Y") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = regW,
                            onValueChange = { regW = it },
                            label = { Text("Lebar") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = regH,
                            onValueChange = { regH = it },
                            label = { Text("Tinggi") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Kompensasi Posisi Sentuh (Offset X & Y) Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlateCardBg)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kompensasi Posisi Klik (Offset)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "X: ${if (offsetX > 0) "+$offsetX" else "$offsetX"} px | Y: ${if (offsetY > 0) "+$offsetY" else "$offsetY"} px",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = CyberCyan
                    )
                }

                Text(
                    text = "Gunakan nilai negatif (-) untuk menggeser klik ke KIRI / ATAS, atau nilai positif (+) untuk menggeser ke KANAN / BAWAH.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )

                // 1. Pengaturan Offset X (Horizontal / Kiri - Kanan)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Offset X (Horizontal):",
                            fontSize = 12.sp,
                            color = TextLight,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when {
                                offsetX < 0 -> "← Geser ke KIRI ${-offsetX} px"
                                offsetX > 0 -> "Geser ke KANAN $offsetX px →"
                                else -> "Tepat di Tengah (0 px)"
                            },
                            fontSize = 11.sp,
                            color = if (offsetX != 0) CyberCyan else TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val newVal = offsetX - 10
                                offsetX = newVal
                                offsetXText = newVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text("-10", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val newVal = offsetX - 5
                                offsetX = newVal
                                offsetXText = newVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text("-5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = offsetXText,
                            onValueChange = {
                                offsetXText = it
                                offsetX = it.toIntOrNull() ?: 0
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = {
                                val newVal = offsetX + 5
                                offsetX = newVal
                                offsetXText = newVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text("+5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val newVal = offsetX + 10
                                offsetX = newVal
                                offsetXText = newVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text("+10", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Presets X Cepat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(-50, -25, -10, 0, 10, 25, 50).forEach { value ->
                            val isCurrent = offsetX == value
                            OutlinedButton(
                                onClick = {
                                    offsetX = value
                                    offsetXText = value.toString()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isCurrent) CyberCyan.copy(alpha = 0.25f) else Color.Transparent,
                                    contentColor = if (isCurrent) CyberCyan else TextMuted
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrent) CyberCyan else BorderSlate.copy(alpha = 0.6f)
                                )
                            ) {
                                Text(
                                    text = if (value > 0) "+$value" else "$value",
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 2. Pengaturan Offset Y (Vertikal / Atas - Bawah)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Offset Y (Vertikal):",
                            fontSize = 12.sp,
                            color = TextLight,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when {
                                offsetY < 0 -> "↑ Geser ke ATAS ${-offsetY} px"
                                offsetY > 0 -> "Geser ke BAWAH $offsetY px ↓"
                                else -> "Tepat di Tengah (0 px)"
                            },
                            fontSize = 11.sp,
                            color = if (offsetY != 0) CyberCyan else TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val newVal = offsetY - 10
                                offsetY = newVal
                                offsetYText = newVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text("-10", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val newVal = offsetY - 5
                                offsetY = newVal
                                offsetYText = newVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text("-5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = offsetYText,
                            onValueChange = {
                                offsetYText = it
                                offsetY = it.toIntOrNull() ?: 0
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = {
                                val newVal = offsetY + 5
                                offsetY = newVal
                                offsetYText = newVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text("+5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val newVal = offsetY + 10
                                offsetY = newVal
                                offsetYText = newVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                        ) {
                            Text("+10", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Presets Y Cepat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(-50, -25, -10, 0, 10, 25, 50).forEach { value ->
                            val isCurrent = offsetY == value
                            OutlinedButton(
                                onClick = {
                                    offsetY = value
                                    offsetYText = value.toString()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isCurrent) CyberCyan.copy(alpha = 0.25f) else Color.Transparent,
                                    contentColor = if (isCurrent) CyberCyan else TextMuted
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrent) CyberCyan else BorderSlate.copy(alpha = 0.6f)
                                )
                            ) {
                                Text(
                                    text = if (value > 0) "+$value" else "$value",
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Target Button
        Button(
            onClick = {
                if (targetName.isBlank()) {
                    Toast.makeText(context, "Nama target harus diisi", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                onSaveTarget(
                    TargetConfigData(
                        name = targetName,
                        actionType = actionType,
                        holdDurationMs = holdDurationMsText.toLongOrNull() ?: 1000L,
                        allowMultiMatch = allowMultiMatch,
                        threshold = threshold,
                        delayAfterTapMs = delayAfterTap.toLongOrNull() ?: 1000L,
                        maxTaps = maxTaps.toIntOrNull() ?: 0,
                        priority = priority.toIntOrNull() ?: defaultPriority,
                        timeoutSeconds = timeoutSecsText.toIntOrNull() ?: 10,
                        restrictRegion = restrictRegion,
                        regionX = regX.toIntOrNull() ?: 0,
                        regionY = regY.toIntOrNull() ?: 0,
                        regionWidth = regW.toIntOrNull() ?: 300,
                        regionHeight = regH.toIntOrNull() ?: 300,
                        offsetX = offsetX,
                        offsetY = offsetY
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldReady,
                contentColor = ObsidianBg
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("SIMPAN GAMBAR TARGET", fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp)
        }
    }
}

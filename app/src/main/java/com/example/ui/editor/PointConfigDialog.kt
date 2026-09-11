package com.example.ui.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TargetImage
import com.example.ui.theme.*

@Composable
fun PointConfigDialog(
    initialTarget: TargetImage?,
    totalExistingPoints: Int,
    onSavePoint: (name: String, x: Float, y: Float, delayMs: Long, actionType: String, holdDurationMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var pointNameText by remember {
        mutableStateOf(initialTarget?.name ?: "Titik ${totalExistingPoints + 1}")
    }
    var pointXText by remember {
        mutableStateOf(initialTarget?.pointX?.toInt()?.toString() ?: "540")
    }
    var pointYText by remember {
        mutableStateOf(initialTarget?.pointY?.toInt()?.toString() ?: "960")
    }
    var pointActionType by remember {
        mutableStateOf(initialTarget?.actionType ?: "TAP")
    }
    var pointHoldMsText by remember {
        mutableStateOf(initialTarget?.holdDurationMs?.toString() ?: "1000")
    }

    var pointDelayUnit by remember {
        val rawDelay = initialTarget?.delayAfterTapMs ?: 500L
        val unit = when {
            rawDelay >= 60000L && rawDelay % 60000L == 0L -> "mnt"
            rawDelay >= 1000L && rawDelay % 1000L == 0L -> "dtk"
            else -> "ms"
        }
        mutableStateOf(unit)
    }

    var pointDelayText by remember {
        val rawDelay = initialTarget?.delayAfterTapMs ?: 500L
        val text = when {
            rawDelay >= 60000L && rawDelay % 60000L == 0L -> (rawDelay / 60000L).toString()
            rawDelay >= 1000L && rawDelay % 1000L == 0L -> (rawDelay / 1000L).toString()
            else -> rawDelay.toString()
        }
        mutableStateOf(text)
    }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialTarget == null) "Tambah Titik Sentuh Manual" else "Edit Titik Sentuh",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = pointNameText,
                    onValueChange = { pointNameText = it },
                    label = { Text("Nama Titik (cth: Tombol Menu)") },
                    colors = textFieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pointXText,
                        onValueChange = { pointXText = it },
                        label = { Text("Posisi X (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pointYText,
                        onValueChange = { pointYText = it },
                        label = { Text("Posisi Y (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Action Type Picker
                Text("Tindakan:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pointActionType == "TAP",
                        onClick = { pointActionType = "TAP" },
                        label = { Text("Ketuk (Tap)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                    FilterChip(
                        selected = pointActionType == "LONG_PRESS",
                        onClick = { pointActionType = "LONG_PRESS" },
                        label = { Text("Tahan (Hold)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }

                if (pointActionType == "LONG_PRESS") {
                    OutlinedTextField(
                        value = pointHoldMsText,
                        onValueChange = { pointHoldMsText = it },
                        label = { Text("Durasi Tahan (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Delay and Unit Picker
                Text("Delay / Jeda Waktu Setelah Sentuh:", fontWeight = FontWeight.Bold, color = EmeraldReady, fontSize = 12.sp)
                OutlinedTextField(
                    value = pointDelayText,
                    onValueChange = { pointDelayText = it },
                    label = { Text("Nilai Waktu") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ms" to "Milidetik (ms)", "dtk" to "Detik (dtk)", "mnt" to "Menit (mnt)").forEach { (unitKey, label) ->
                        FilterChip(
                            selected = pointDelayUnit == unitKey,
                            onClick = { pointDelayUnit = unitKey },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldReady.copy(alpha = 0.25f),
                                selectedLabelColor = EmeraldReady
                            )
                        )
                    }
                }

                val num = pointDelayText.toDoubleOrNull() ?: 500.0
                val multiplier = when (pointDelayUnit) {
                    "mnt" -> 60000L
                    "dtk" -> 1000L
                    else -> 1L
                }
                val calculatedMs = (num * multiplier).toLong()

                Text(
                    text = "⏱️ Jeda eksekusi: $calculatedMs ms (${calculatedMs / 1000f} detik)",
                    fontSize = 11.sp,
                    color = EmeraldReady,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Tips: Anda juga dapat mengatur posisi titik langsung di atas aplikasi/game dengan menggeser pin saat floating overlay aktif, dan sentuh pin untuk membuka dialog pengaturan cepat!",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val x = pointXText.toFloatOrNull() ?: 540f
                    val y = pointYText.toFloatOrNull() ?: 960f
                    val numVal = pointDelayText.toDoubleOrNull() ?: 500.0
                    val mult = when (pointDelayUnit) {
                        "mnt" -> 60000L
                        "dtk" -> 1000L
                        else -> 1L
                    }
                    val delay = (numVal * mult).toLong().coerceAtLeast(10L)
                    val holdMs = pointHoldMsText.toLongOrNull() ?: 1000L
                    val name = if (pointNameText.isNotBlank()) pointNameText else "Titik Sentuh"

                    onSavePoint(name, x, y, delay, pointActionType, holdMs)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldReady, contentColor = ObsidianBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Simpan Titik", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextMuted)
            }
        },
        containerColor = ObsidianBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
    )
}

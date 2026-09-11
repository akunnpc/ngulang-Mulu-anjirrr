package com.example.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.TargetImage
import com.example.ui.theme.*
import java.io.File

@Composable
fun TargetListItem(
    target: TargetImage,
    stepIndex: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditPoint: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Target Icon or Preview Thumbnail
            if (target.targetType == "POINT") {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldReady.copy(alpha = 0.15f))
                        .border(1.dp, EmeraldReady.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Adjust,
                            contentDescription = null,
                            tint = EmeraldReady,
                            modifier = Modifier.size(28.dp)
                        )
                        Text("${stepIndex + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldReady)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(File(target.filePath)),
                        contentDescription = target.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Meta Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Langkah ${stepIndex + 1}: ${target.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                val actionLabel = when (target.actionType) {
                    "LONG_PRESS" -> "Tekan Tahan (${target.holdDurationMs}ms)"
                    "WAIT_ONLY" -> "Tunggu Saja"
                    "WAIT_DISAPPEAR" -> "Tunggu Hilang"
                    else -> "Ketuk"
                }

                if (target.targetType == "POINT") {
                    Text(
                        "Titik Koordinat: (${target.pointX.toInt()}, ${target.pointY.toInt()})",
                        fontSize = 12.sp,
                        color = EmeraldReady,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Tindakan: $actionLabel | Delay: ${target.delayAfterTapMs}ms",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                } else {
                    Text(
                        "Tindakan: $actionLabel",
                        fontSize = 12.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Akurasi: ${target.threshold} | Delay: ${target.delayAfterTapMs}ms\nTimeout: ${target.timeoutSeconds}s",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    if (target.maxTaps > 0) {
                        Text(
                            "Tap Maks: ${target.tapCount}/${target.maxTaps}",
                            fontSize = 11.sp,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Edit Button for Manual Point
            if (target.targetType == "POINT") {
                IconButton(onClick = onEditPoint) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Titik", tint = EmeraldReady)
                }
            }

            // Reorder Actions
            if (canMoveUp) {
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Pindah Ke Atas", tint = CyberCyan)
                }
            }

            if (canMoveDown) {
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Pindah Ke Bawah", tint = CyberCyan)
                }
            }

            // Delete Action
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF4444))
            }
        }
    }
}

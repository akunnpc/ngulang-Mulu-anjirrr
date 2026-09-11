package com.example.ui.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun TargetTypeSelectDialog(
    onSelectImageTarget: () -> Unit,
    onSelectPointTarget: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pilih Jenis Target",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Pilih metode penargetan yang ingin ditambahkan ke alur otomatisasi:",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                // Option 1: Visual Image Match
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectImageTarget() },
                    colors = CardDefaults.cardColors(containerColor = SlateCardBg.copy(alpha = 0.6f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Deteksi Gambar Visual", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("Mencocokkan potongan ikon/tombol di layar secara cerdas.", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }

                // Option 2: Manual Touch Point
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPointTarget() },
                    colors = CardDefaults.cardColors(containerColor = SlateCardBg.copy(alpha = 0.6f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldReady.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.dp, EmeraldReady.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = EmeraldReady, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Titik Sentuh Manual (Pin)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("Mengetuk koordinat layar tetap (X, Y) atau pin yang dapat digeser di layar.", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = CyberCyan)
            }
        },
        containerColor = ObsidianBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
    )
}

package com.example.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.BitmapHelper

@Composable
fun ImageCropperContent(
    bitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit,
    onCropFailed: () -> Unit
) {
    var cropLeft by remember { mutableFloatStateOf(0.2f) }
    var cropRight by remember { mutableFloatStateOf(0.8f) }
    var cropTop by remember { mutableFloatStateOf(0.2f) }
    var cropBottom by remember { mutableFloatStateOf(0.8f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Geser slider untuk menentukan area pemotongan target",
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            fontSize = 14.sp
        )

        // Main Image Bounding Box Visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Tech-themed holographic border targeting the cropped area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, CyberCyan.copy(alpha = 0.7f))
            )
        }

        // Sliders for Cropping Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlateCardBg)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Koordinat Potong (Kiri - Kanan):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kiri: ", fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(44.dp))
                    Slider(
                        value = cropLeft,
                        onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.1f) },
                        colors = SliderDefaults.colors(activeTrackColor = CyberCyan, thumbColor = CyberCyan),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kanan: ", fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(44.dp))
                    Slider(
                        value = cropRight,
                        onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.1f) },
                        colors = SliderDefaults.colors(activeTrackColor = CyberCyan, thumbColor = CyberCyan),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Koordinat Potong (Atas - Bawah):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Atas: ", fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(44.dp))
                    Slider(
                        value = cropTop,
                        onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.1f) },
                        colors = SliderDefaults.colors(activeTrackColor = CyberCyan, thumbColor = CyberCyan),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bawah: ", fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(44.dp))
                    Slider(
                        value = cropBottom,
                        onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.1f) },
                        colors = SliderDefaults.colors(activeTrackColor = CyberCyan, thumbColor = CyberCyan),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Crop Confirm Button
        Button(
            onClick = {
                val cropped = BitmapHelper.cropBitmap(bitmap, cropLeft, cropRight, cropTop, cropBottom)
                if (cropped != null) {
                    onCropConfirmed(cropped)
                } else {
                    onCropFailed()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = CyberCyan,
                contentColor = ObsidianBg
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("LAKUKAN PEMOTONGAN", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp)
        }
    }
}

package com.example

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.*
import com.example.ui.editor.*
import com.example.ui.theme.*
import com.example.util.BitmapHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class EditorSubScreen {
    MAIN_EDITOR,
    IMAGE_CROPPER,
    TARGET_CONFIG
}

class ProfileEditorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }

    private lateinit var repository: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(this)
        repository = ProfileRepository(db.autoTapDao())

        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)

        setContent {
            MyApplicationTheme {
                ProfileEditorScreen(
                    profileId = profileId,
                    repository = repository,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profileId: Long,
    repository: ProfileRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentProfileId by remember { mutableStateOf(profileId) }
    var profileName by remember { mutableStateOf("") }
    var captureInterval by remember { mutableStateOf("500") }
    var packageName by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var loopSequence by remember { mutableStateOf(false) }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var currentSubScreen by remember { mutableStateOf(EditorSubScreen.MAIN_EDITOR) }

    // Dialog flags
    var showAppPicker by remember { mutableStateOf(false) }
    var showTargetTypeDialog by remember { mutableStateOf(false) }
    var showPointConfigDialog by remember { mutableStateOf(false) }
    var editingPointTarget by remember { mutableStateOf<TargetImage?>(null) }

    val profileWithTargets by remember(currentProfileId) {
        if (currentProfileId != -1L) {
            repository.getProfileWithTargets(currentProfileId)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(profileWithTargets) {
        profileWithTargets?.let { pwt ->
            profileName = pwt.profile.name
            captureInterval = pwt.profile.captureIntervalMs.toString()
            packageName = pwt.profile.packageName
            appName = pwt.profile.appName
            loopSequence = pwt.profile.loopSequence
        }
    }

    val targetImages = profileWithTargets?.targets ?: emptyList()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val bmp = BitmapHelper.loadBitmapFromUri(context, it)
                if (bmp != null) {
                    originalBitmap = bmp
                    currentSubScreen = EditorSubScreen.IMAGE_CROPPER
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentSubScreen) {
                            EditorSubScreen.MAIN_EDITOR -> "Edit Profil Auto Tap"
                            EditorSubScreen.IMAGE_CROPPER -> "Crop Gambar Target"
                            EditorSubScreen.TARGET_CONFIG -> "Konfigurasi Gambar Target"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (currentSubScreen) {
                            EditorSubScreen.MAIN_EDITOR -> onBack()
                            EditorSubScreen.IMAGE_CROPPER -> currentSubScreen = EditorSubScreen.MAIN_EDITOR
                            EditorSubScreen.TARGET_CONFIG -> currentSubScreen = EditorSubScreen.IMAGE_CROPPER
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = CyberCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBg,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = ObsidianBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentSubScreen) {
                EditorSubScreen.MAIN_EDITOR -> {
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

                    val selectedAppIcon = remember(packageName) {
                        if (packageName.isNotEmpty()) {
                            try {
                                context.packageManager.getApplicationIcon(packageName)
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Pengaturan Profil",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )

                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("Nama Profil") },
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = captureInterval,
                            onValueChange = { captureInterval = it },
                            label = { Text("Interval Capture (ms)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Target Application Section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = SlateCardBg.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Aplikasi Target (Auto-Launch)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    if (packageName.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                packageName = ""
                                                appName = ""
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Hapus", color = Color(0xFFEF4444), fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (packageName.isEmpty()) {
                                    Button(
                                        onClick = { showAppPicker = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberCyan,
                                            contentColor = ObsidianBg
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("PILIH APLIKASI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .border(1.dp, BorderSlate.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .clickable { showAppPicker = true }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (selectedAppIcon != null) {
                                            Image(
                                                painter = rememberAsyncImagePainter(selectedAppIcon),
                                                contentDescription = appName,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Android, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = appName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = packageName,
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }

                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Loop Sequence Toggle Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = SlateCardBg)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Loop Sequence",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (loopSequence) "Ulangi dari Langkah 1 terus-menerus" else "Berhenti setelah langkah terakhir selesai",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                Switch(
                                    checked = loopSequence,
                                    onCheckedChange = { loopSequence = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = CyberCyan,
                                        checkedTrackColor = CyberCyan.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }

                        // Targets Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daftar Target Eksekusi",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Button(
                                onClick = { showTargetTypeDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah Target", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Targets Listing
                        if (targetImages.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = SlateCardBg.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.TouchApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = TextMuted
                                    )
                                    Text("Belum ada target otomasi", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        "Tambahkan deteksi gambar visual atau titik sentuh manual (koordinat pin) untuk mulai mengotomatisasi.",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        } else {
                            val sortedTargets = targetImages.sortedBy { it.priority }
                            sortedTargets.forEachIndexed { index, target ->
                                TargetListItem(
                                    target = target,
                                    stepIndex = index,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < sortedTargets.size - 1,
                                    onMoveUp = {
                                        coroutineScope.launch {
                                            val mutable = sortedTargets.toMutableList()
                                            java.util.Collections.swap(mutable, index, index - 1)
                                            mutable.forEachIndexed { i, t ->
                                                repository.updateTargetImage(t.copy(priority = i))
                                            }
                                        }
                                    },
                                    onMoveDown = {
                                        coroutineScope.launch {
                                            val mutable = sortedTargets.toMutableList()
                                            java.util.Collections.swap(mutable, index, index + 1)
                                            mutable.forEachIndexed { i, t ->
                                                repository.updateTargetImage(t.copy(priority = i))
                                            }
                                        }
                                    },
                                    onEditPoint = {
                                        editingPointTarget = target
                                        showPointConfigDialog = true
                                    },
                                    onDelete = {
                                        coroutineScope.launch {
                                            repository.deleteTargetImage(target)
                                            if (target.filePath.isNotBlank()) {
                                                try { File(target.filePath).delete() } catch (e: Exception) {}
                                            }
                                            Toast.makeText(context, "Target dihapus", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Save Profile Button
                        Button(
                            onClick = {
                                val interval = captureInterval.toLongOrNull() ?: 500L
                                if (profileName.isBlank()) {
                                    Toast.makeText(context, "Nama profil tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch {
                                    if (currentProfileId == -1L) {
                                        repository.insertProfile(
                                            Profile(
                                                name = profileName.trim(),
                                                captureIntervalMs = interval,
                                                packageName = packageName,
                                                appName = appName,
                                                loopSequence = loopSequence
                                            )
                                        )
                                    } else {
                                        repository.updateProfile(
                                            Profile(
                                                id = currentProfileId,
                                                name = profileName.trim(),
                                                captureIntervalMs = interval,
                                                packageName = packageName,
                                                appName = appName,
                                                loopSequence = loopSequence
                                            )
                                        )
                                    }
                                    onBack()
                                }
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
                            Text("SIMPAN PENGATURAN PROFIL", fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp)
                        }
                    }

                    // Dialogs
                    if (showAppPicker) {
                        AppPickerDialog(
                            context = context,
                            onAppSelected = { app ->
                                packageName = app.packageName
                                appName = app.name
                                showAppPicker = false
                            },
                            onDismiss = { showAppPicker = false }
                        )
                    }

                    if (showTargetTypeDialog) {
                        TargetTypeSelectDialog(
                            onSelectImageTarget = {
                                showTargetTypeDialog = false
                                imagePickerLauncher.launch("image/*")
                            },
                            onSelectPointTarget = {
                                showTargetTypeDialog = false
                                editingPointTarget = null
                                showPointConfigDialog = true
                            },
                            onDismiss = { showTargetTypeDialog = false }
                        )
                    }

                    if (showPointConfigDialog) {
                        PointConfigDialog(
                            initialTarget = editingPointTarget,
                            totalExistingPoints = targetImages.count { it.targetType == "POINT" },
                            onSavePoint = { name, x, y, delayMs, actionType, holdMs ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    var pId = currentProfileId
                                    if (pId == -1L) {
                                        val interval = captureInterval.toLongOrNull() ?: 500L
                                        val nameToSave = profileName.ifBlank { "Profil Baru" }
                                        pId = repository.insertProfile(
                                            Profile(
                                                name = nameToSave,
                                                captureIntervalMs = interval,
                                                packageName = packageName,
                                                appName = appName,
                                                loopSequence = loopSequence
                                            )
                                        )
                                        withContext(Dispatchers.Main) {
                                            currentProfileId = pId
                                            if (profileName.isBlank()) profileName = nameToSave
                                        }
                                    }

                                    val existing = editingPointTarget
                                    if (existing != null) {
                                        repository.updateTargetImage(
                                            existing.copy(
                                                name = name,
                                                pointX = x,
                                                pointY = y,
                                                actionType = actionType,
                                                holdDurationMs = holdMs,
                                                delayAfterTapMs = delayMs
                                            )
                                        )
                                    } else {
                                        val newTarget = TargetImage(
                                            profileId = pId,
                                            name = name,
                                            filePath = "",
                                            threshold = 0.8f,
                                            delayAfterTapMs = delayMs,
                                            maxTaps = 0,
                                            priority = targetImages.size,
                                            targetType = "POINT",
                                            pointX = x,
                                            pointY = y,
                                            actionType = actionType,
                                            holdDurationMs = holdMs
                                        )
                                        repository.insertTargetImage(newTarget)
                                    }

                                    withContext(Dispatchers.Main) {
                                        showPointConfigDialog = false
                                        Toast.makeText(context, "Titik sentuh tersimpan! (Delay: ${delayMs}ms)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDismiss = { showPointConfigDialog = false }
                        )
                    }
                }

                EditorSubScreen.IMAGE_CROPPER -> {
                    originalBitmap?.let { bitmap ->
                        ImageCropperContent(
                            bitmap = bitmap,
                            onCropConfirmed = { cropped ->
                                croppedBitmap = cropped
                                currentSubScreen = EditorSubScreen.TARGET_CONFIG
                            },
                            onCropFailed = {
                                Toast.makeText(context, "Potong gagal. Periksa koordinat.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                EditorSubScreen.TARGET_CONFIG -> {
                    croppedBitmap?.let { cropped ->
                        TargetConfigContent(
                            croppedBitmap = cropped,
                            defaultPriority = targetImages.size,
                            onSaveTarget = { data ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    val targetFile = BitmapHelper.saveBitmapToFile(context, cropped, "target")

                                    var pId = currentProfileId
                                    if (pId == -1L) {
                                        val interval = captureInterval.toLongOrNull() ?: 500L
                                        val nameToSave = profileName.ifBlank { "Profil Baru" }
                                        pId = repository.insertProfile(
                                            Profile(
                                                name = nameToSave,
                                                captureIntervalMs = interval,
                                                packageName = packageName,
                                                appName = appName,
                                                loopSequence = loopSequence
                                            )
                                        )
                                        withContext(Dispatchers.Main) {
                                            currentProfileId = pId
                                            if (profileName.isBlank()) profileName = nameToSave
                                        }
                                    }

                                    val targetImage = TargetImage(
                                        profileId = pId,
                                        name = data.name,
                                        filePath = targetFile.absolutePath,
                                        threshold = data.threshold,
                                        delayAfterTapMs = data.delayAfterTapMs,
                                        maxTaps = data.maxTaps,
                                        priority = data.priority,
                                        restrictRegion = data.restrictRegion,
                                        regionX = data.regionX,
                                        regionY = data.regionY,
                                        regionWidth = data.regionWidth,
                                        regionHeight = data.regionHeight,
                                        timeoutSeconds = data.timeoutSeconds,
                                        actionType = data.actionType,
                                        holdDurationMs = data.holdDurationMs,
                                        offsetX = data.offsetX,
                                        offsetY = data.offsetY
                                    )

                                    repository.insertTargetImage(targetImage)

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Target disimpan!", Toast.LENGTH_SHORT).show()
                                        currentSubScreen = EditorSubScreen.MAIN_EDITOR
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

package com.satpam.piket.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.satpam.piket.domain.ShiftManager
import com.satpam.piket.ui.viewmodel.FaceLoginState
import com.satpam.piket.ui.viewmodel.FaceLoginViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceLoginScreen(
    onNavigateBack: () -> Unit,
    onCheckInSuccess: (absensiId: String) -> Unit,
    viewModel: FaceLoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val currentShift by viewModel.currentShift.collectAsState()
    val shiftId by viewModel.shiftId.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var tipeAbsen by remember { mutableStateOf("MASUK") }
    var keteranganPiket by remember { mutableStateOf("Situasi aman terkendali") }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (!hasCameraPermission) permissionsToRequest.add(Manifest.permission.CAMERA)
        if (!hasLocationPermission) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Update waktu setiap detik
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateTime()
            kotlinx.coroutines.delay(1000)
        }
    }

    // Navigasi saat sukses
    LaunchedEffect(state) {
        if (state is FaceLoginState.CheckInSuccess) {
            val absensi = (state as FaceLoginState.CheckInSuccess).absensi
            onCheckInSuccess(absensi.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Absen Wajah & Lokasi", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D47A1))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
        ) {
            // Info shift
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1565C0))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(ShiftManager.getShiftLabel(currentShift), color = Color.White, fontSize = 13.sp)
                    Text(shiftId, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Text(currentTime, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            // Camera preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreviewWithAnalysis(
                        modifier = Modifier.fillMaxSize(),
                        onFrameCaptured = { bitmap ->
                            if (state is FaceLoginState.Idle || state is FaceLoginState.Scanning) {
                                viewModel.processFrame(bitmap)
                            }
                        }
                    )

                    // Overlay frame guide
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2
                        val cy = size.height / 2.2f
                        val radius = minOf(size.width, size.height) * 0.35f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f),
                            radius = radius,
                            center = Offset(cx, cy),
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }

                    // Status label
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        StatusBadge(state = state)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FaceRetouchingNatural, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Izin kamera dan lokasi diperlukan", color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            permissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }) {
                            Text("Izinkan Akses")
                        }
                    }
                }
            }

            // State: CheckingLocation
            if (state is FaceLoginState.CheckingLocation) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D47A1))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Memverifikasi titik lokasi GPS...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Memeriksa jarak radius kantor", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }

            // Confirm panel (muncul jika wajah dikenali)
            if (state is FaceLoginState.Recognized) {
                val recognizedState = state as FaceLoginState.Recognized
                val petugas = recognizedState.petugas
                val hasMasuk = recognizedState.hasMasuk

                // Auto sesuaikan pilihan tipeAbsen: jika sudah masuk arahkan ke Lepas Piket, jika belum kunci ke MASUK
                LaunchedEffect(petugas.id, hasMasuk) {
                    tipeAbsen = if (hasMasuk) "KELUAR" else "MASUK"
                }

                val isMasuk = tipeAbsen == "MASUK"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isMasuk) Color(0xFF1B5E20) else Color(0xFF880E4F))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(petugas.nama, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${petugas.jabatan} • ${petugas.lokasi.ifBlank { "Kantor UP3 Baubau" }}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pilihan Tipe Absen: Masuk Piket vs Lepas Piket
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = isMasuk,
                            onClick = { tipeAbsen = "MASUK" },
                            label = { Text("🟢 Piket Masuk", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color(0xFF1B5E20),
                                containerColor = Color.White.copy(alpha = 0.2f),
                                labelColor = Color.White
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        FilterChip(
                            selected = !isMasuk,
                            onClick = {
                                if (hasMasuk) {
                                    tipeAbsen = "KELUAR"
                                }
                            },
                            enabled = hasMasuk,
                            label = {
                                Text(
                                    if (hasMasuk) "🔴 Lepas Piket (Keluar)" else "🔒 Lepas Piket (Terkunci)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color(0xFF880E4F),
                                containerColor = Color.White.copy(alpha = 0.2f),
                                labelColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledLabelColor = Color.White.copy(alpha = 0.45f)
                            )
                        )
                    }

                    // Banner peringatan jika belum pernah absen masuk
                    if (!hasMasuk) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF3CD),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFF856404),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Belum ada riwayat Absen Masuk. Tombol Lepas Piket tidak dapat dipilih.",
                                    color = Color(0xFF856404),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input Keterangan Piket (akan muncul di laporan pergantian shift)
                    OutlinedTextField(
                        value = keteranganPiket,
                        onValueChange = { keteranganPiket = it },
                        label = { Text("Keterangan Piket", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp) },
                        placeholder = { Text("Contoh: Situasi aman terkendali", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                            cursorColor = Color.White
                        )
                    )

                    // Template cepat keterangan
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Aman terkendali", "Kondusif", "Pagar & pintu terkunci").forEach { template ->
                            SuggestionChip(
                                onClick = { keteranganPiket = template },
                                label = { Text(template, fontSize = 10.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.resetState() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("Ulangi") }
                        Button(
                            onClick = {
                                if (isMasuk || hasMasuk) {
                                    viewModel.confirmCheckIn(petugas, tipeAbsen, keteranganPiket)
                                }
                            },
                            enabled = isMasuk || hasMasuk,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMasuk) Color(0xFF2E7D32) else Color(0xFFAD1457),
                                disabledContainerColor = Color.Gray
                            )
                        ) {
                            Text(if (isMasuk) "✓ Absen Masuk" else "✓ Lepas Piket")
                        }
                    }
                }
            }
        }
    }

    // Modal Peringatan: Lokasi Terlalu Jauh!
    if (state is FaceLoginState.LocationTooFar) {
        val tooFarState = state as FaceLoginState.LocationTooFar
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            icon = {
                Icon(
                    Icons.Filled.LocationOff,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(52.dp)
                )
            },
            title = {
                Text(
                    "Lokasi Terlalu Jauh!",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Anda tidak dapat melakukan absensi karena posisi GPS berada di luar batas radius yang ditentukan.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    HorizontalDivider()
                    Text("👤 Petugas: ${tooFarState.petugas.nama}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("📍 Lokasi: ${tooFarState.namaLokasi}", fontSize = 13.sp)
                    Text(
                        "📏 Jarak Anda: ±${String.format(java.util.Locale.US, "%.1f", tooFarState.distanceMeters)} meter",
                        fontSize = 13.sp,
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold
                    )
                    Text("🎯 Batas Radius: ${tooFarState.radiusMeter} meter", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Silakan mendekat ke area lokasi (${tooFarState.namaLokasi}) maksimal dalam radius ${tooFarState.radiusMeter} meter untuk dapat melakukan absensi.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontStyle = FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetState() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Tutup & Ulangi")
                }
            }
        )
    }

    // Modal Peringatan: Masalah GPS / Izin
    if (state is FaceLoginState.LocationError) {
        val errMsg = (state as FaceLoginState.LocationError).message
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFA000),
                    modifier = Modifier.size(44.dp)
                )
            },
            title = { Text("Peringatan GPS", fontWeight = FontWeight.Bold) },
            text = { Text(errMsg, fontSize = 13.sp) },
            confirmButton = {
                Button(onClick = { viewModel.resetState() }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Modal Peringatan: Error Validasi / Sistem
    if (state is FaceLoginState.Error) {
        val errMsg = (state as FaceLoginState.Error).message
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(44.dp)
                )
            },
            title = { Text("Peringatan Piket", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)) },
            text = { Text(errMsg, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetState() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
fun StatusBadge(state: FaceLoginState) {
    val (text, bgColor) = when (state) {
        is FaceLoginState.Idle -> "Arahkan wajah ke kamera" to Color(0x99000000)
        is FaceLoginState.Scanning -> "Memindai wajah..." to Color(0x99000000)
        is FaceLoginState.FaceDetected -> state.label to Color(0xBB1565C0)
        is FaceLoginState.Recognized -> "✓ ${(state).petugas.nama}" to Color(0xBB1B5E20)
        is FaceLoginState.NotRecognized -> "Wajah tidak dikenal" to Color(0xBBC62828)
        is FaceLoginState.CheckingLocation -> "Memverifikasi lokasi GPS..." to Color(0xBB0D47A1)
        is FaceLoginState.LocationTooFar -> "Lokasi Terlalu Jauh!" to Color(0xBBC62828)
        is FaceLoginState.LocationError -> "Error Lokasi GPS" to Color(0xBBC62828)
        is FaceLoginState.Error -> state.message to Color(0xBBC62828)
        else -> "" to Color.Transparent
    }
    if (text.isNotEmpty()) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CameraPreviewWithAnalysis(
    modifier: Modifier = Modifier,
    onFrameCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var lastAnalysisTime = remember { 0L }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val now = System.currentTimeMillis()
                            // Analisa setiap 500ms agar tidak terlalu berat
                            if (now - lastAnalysisTime > 500) {
                                lastAnalysisTime = now
                                try {
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val rawBitmap = imageProxy.toBitmap()
                                    val portraitBitmap = com.satpam.piket.domain.FaceRecognitionManager.rotateToPortrait(
                                        rawBitmap,
                                        rotation,
                                        isFrontCamera = true
                                    )
                                    onFrameCaptured(portraitBitmap)
                                } catch (e: Exception) {
                                    Log.e("CameraAnalysis", "Error: ${e.message}")
                                }
                            }
                            imageProxy.close()
                        }
                    }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    Log.e("CameraSetup", "Error: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

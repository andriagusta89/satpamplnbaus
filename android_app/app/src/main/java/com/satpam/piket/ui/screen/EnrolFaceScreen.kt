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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.satpam.piket.ui.viewmodel.EnrolFaceState
import com.satpam.piket.ui.viewmodel.EnrolFaceViewModel
import java.util.concurrent.Executors

/**
 * Layar Enrol Wajah Petugas
 *
 * Alur:
 * 1. Admin membuka layar ini setelah mengisi nama & jabatan
 * 2. Kamera depan aktif, wajah petugas dideteksi via ML Kit
 * 3. Saat wajah terdeteksi (lingkaran hijau), tombol "Ambil Foto" aktif
 * 4. Foto disimpan sebagai referensi dan petugas didaftarkan ke database
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrolFaceScreen(
    nama: String,
    jabatan: String,
    lokasi: String,
    onNavigateBack: () -> Unit,
    onEnrolSuccess: () -> Unit,
    viewModel: EnrolFaceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Navigasi otomatis saat enrol berhasil
    LaunchedEffect(state) {
        if (state is EnrolFaceState.Success) {
            kotlinx.coroutines.delay(1200)
            onEnrolSuccess()
        }
    }

    val snackbarState = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        if (state is EnrolFaceState.Error) {
            snackbarState.showSnackbar((state as EnrolFaceState.Error).message)
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enrol Wajah: $nama", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D47A1))
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
        ) {

            // Info petugas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1565C0))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(nama, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(jabatan, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    if (lokasi.isNotBlank()) {
                        Text(
                            "📍 $lokasi",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Instruksi
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color(0xFF1A237E),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, null, tint = Color(0xFF90CAF9), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Posisikan wajah petugas di dalam lingkaran. Pastikan pencahayaan cukup.",
                        color = Color(0xFF90CAF9),
                        fontSize = 12.sp
                    )
                }
            }

            // Camera area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    EnrolCameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrameCaptured = { bitmap ->
                            if (state is EnrolFaceState.Idle || state is EnrolFaceState.Scanning) {
                                viewModel.detectFace(bitmap)
                            }
                        }
                    )

                    // Lingkaran panduan wajah (hijau = wajah terdeteksi)
                    val circleColor = when (state) {
                        is EnrolFaceState.FaceDetected -> Color(0xFF4CAF50)
                        is EnrolFaceState.Capturing -> Color(0xFFFF9800)
                        is EnrolFaceState.Success -> Color(0xFF4CAF50)
                        else -> Color.White.copy(alpha = 0.5f)
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2
                        val cy = size.height / 2.2f
                        val radius = minOf(size.width, size.height) * 0.37f
                        drawCircle(
                            color = circleColor,
                            radius = radius,
                            center = Offset(cx, cy),
                            style = Stroke(width = 5.dp.toPx())
                        )
                    }

                    // Status label
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    ) {
                        EnrolStatusBadge(state = state)
                    }

                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Camera, null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Izin kamera diperlukan", color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Izinkan Kamera")
                        }
                    }
                }
            }

            // Tombol capture & status bawah
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is EnrolFaceState.Capturing -> {
                        CircularProgressIndicator(color = Color(0xFF2196F3))
                        Spacer(Modifier.height(8.dp))
                        Text("Menyimpan foto wajah...", color = Color.White, fontSize = 13.sp)
                    }

                    is EnrolFaceState.SendingToServer -> {
                        CircularProgressIndicator(color = Color(0xFF90CAF9))
                        Spacer(Modifier.height(8.dp))
                        Text("Mengirim ke server untuk approval...", color = Color.White, fontSize = 13.sp)
                    }

                    is EnrolFaceState.Success -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Enrol $nama berhasil!",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "⏳ Menunggu persetujuan admin di dashboard",
                            color = Color(0xFF90CAF9),
                            fontSize = 12.sp
                        )
                    }

                    else -> {
                        val isFaceReady = state is EnrolFaceState.FaceDetected
                        Button(
                            onClick = { viewModel.captureAndRegister(nama, jabatan, lokasi) },
                            enabled = isFaceReady,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1565C0),
                                disabledContainerColor = Color(0xFF37474F)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isFaceReady) "📸 Ambil Foto & Daftarkan"
                                else "Tunggu wajah terdeteksi...",
                                fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Lingkaran hijau = wajah siap difoto",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnrolStatusBadge(state: EnrolFaceState) {
    val (text, bgColor) = when (state) {
        is EnrolFaceState.Idle -> "Arahkan wajah ke dalam lingkaran" to Color(0x99000000)
        is EnrolFaceState.Scanning -> "Mendeteksi wajah..." to Color(0x99000000)
        is EnrolFaceState.FaceDetected -> "✓ Wajah terdeteksi – tekan tombol untuk enrol" to Color(0xBB1B5E20)
        is EnrolFaceState.Capturing -> "Mengambil foto..." to Color(0xBBE65100)
        is EnrolFaceState.SendingToServer -> "Mengirim ke server..." to Color(0xBB1565C0)
        is EnrolFaceState.Success -> "✓ Berhasil! Menunggu persetujuan admin." to Color(0xBB1B5E20)
        is EnrolFaceState.Error -> "❌ ${state.message}" to Color(0xBBC62828)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}



@Composable
fun EnrolCameraPreview(
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
                            if (now - lastAnalysisTime > 600) {
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
                                    Log.e("EnrolCamera", "Error: ${e.message}")
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
                    Log.e("EnrolCameraSetup", "Error: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

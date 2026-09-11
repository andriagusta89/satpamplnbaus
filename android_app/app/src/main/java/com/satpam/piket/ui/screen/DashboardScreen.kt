package com.satpam.piket.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.satpam.piket.data.model.Absensi
import com.satpam.piket.data.model.Shift
import com.satpam.piket.data.model.StatusKeterlambatan
import com.satpam.piket.domain.ShiftManager
import com.satpam.piket.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToFaceLogin: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val currentShift by viewModel.currentShift.collectAsState()
    val shiftId by viewModel.shiftId.collectAsState()
    val absensiList by viewModel.absensiList.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()

    // Dialog Notifikasi Update APK
    if (showUpdateDialog && availableUpdate != null) {
        val update = availableUpdate!!
        AlertDialog(
            onDismissRequest = {
                if (!update.forceUpdate) viewModel.dismissUpdateDialog()
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = Color(0xFF1565C0),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Pembaruan Tersedia!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0D47A1)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Versi ${update.versionName} (Build ${update.versionCode}) siap diunduh.",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Versi Anda: ${viewModel.getCurrentVersionName()} (Build ${viewModel.getCurrentVersionCode()})",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    if (update.releaseNotes.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Catatan Rilis:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF333333)
                        )
                        Text(
                            text = update.releaseNotes,
                            fontSize = 12.sp,
                            color = Color(0xFF555555),
                            lineHeight = 16.sp
                        )
                    }
                    if (update.forceUpdate) {
                        Text(
                            text = "⚠️ Pembaruan ini wajib dilakukan untuk melanjutkan absensi.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.downloadAndInstallUpdate() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download (Google Drive)")
                }
            },
            dismissButton = {
                if (!update.forceUpdate) {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text("Nanti Saja", color = Color.Gray)
                    }
                }
            }
        )
    }

    // Refresh setiap menit
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refresh()
            kotlinx.coroutines.delay(60_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard Piket", color = Color.White) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, "Pengaturan", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D47A1))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToFaceLogin,
                icon = { Icon(Icons.Filled.FaceRetouchingNatural, null) },
                text = { Text("ABSEN SEKARANG") },
                containerColor = Color(0xFF1565C0),
                contentColor = Color.White
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5)),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Banner notifikasi update APK jika tersedia
            if (availableUpdate != null && !showUpdateDialog) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Color(0xFF1976D2))
                                Column {
                                    Text(
                                        "Pembaruan v${availableUpdate!!.versionName} tersedia!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0D47A1)
                                    )
                                    Text(
                                        "Unduh APK terbaru dari Google Drive",
                                        fontSize = 11.sp,
                                        color = Color(0xFF1565C0)
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.downloadAndInstallUpdate() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Update", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Shift info card
            item {
                ShiftInfoCard(currentShift, shiftId, currentTime, unsyncedCount,
                    onSync = { viewModel.triggerManualSync() })
            }

            // Stats row
            item {
                val tepat = absensiList.count { it.statusKeterlambatan == StatusKeterlambatan.TEPAT }
                val terlambat = absensiList.count { it.statusKeterlambatan == StatusKeterlambatan.TERLAMBAT }
                StatsRow(total = absensiList.size, tepat = tepat, terlambat = terlambat)
            }

            // Header
            item {
                Text(
                    "Absensi Shift Ini",
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF212121)
                )
            }

            if (absensiList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.People, null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada absensi shift ini", color = Color.Gray)
                        }
                    }
                }
            } else {
                items(absensiList) { absensi ->
                    AbsensiCard(absensi)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun ShiftInfoCard(shift: Shift, shiftId: String, time: String, unsyncedCount: Int, onSync: () -> Unit) {
    val shiftColor = when (shift) {
        Shift.PAGI -> Color(0xFFFFF8E1)
        Shift.SIANG -> Color(0xFFE3F2FD)
        Shift.MALAM -> Color(0xFF1A237E)
    }
    val textColor = if (shift == Shift.MALAM) Color.White else Color(0xFF212121)

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = shiftColor),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(ShiftManager.getShiftLabel(shift), color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("ID: $shiftId", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Text(time, color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            if (unsyncedCount > 0) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$unsyncedCount data belum sinkron", color = Color(0xFFE65100), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSync) { Text("Sinkron Sekarang", fontSize = 12.sp) }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Semua data tersinkron", color = Color(0xFF2E7D32), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun StatsRow(total: Int, tepat: Int, terlambat: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(Modifier.weight(1f), "$total", "Total", Color(0xFF1565C0))
        StatCard(Modifier.weight(1f), "$tepat", "Tepat", Color(0xFF2E7D32))
        StatCard(Modifier.weight(1f), "$terlambat", "Terlambat", Color(0xFFC62828))
    }
}

@Composable
fun StatCard(modifier: Modifier, value: String, label: String, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color)) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
fun AbsensiCard(absensi: Absensi) {
    val isLate = absensi.statusKeterlambatan == StatusKeterlambatan.TERLAMBAT
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1565C0), shape = RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    absensi.nama.first().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(absensi.nama, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Masuk: ${absensi.jamMasuk}", color = Color.Gray, fontSize = 13.sp)
                if (absensi.jamKeluar != null) {
                    Text("Keluar: ${absensi.jamKeluar}", color = Color.Gray, fontSize = 13.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = if (isLate) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isLate) "TERLAMBAT" else "TEPAT",
                        color = if (isLate) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Icon(
                    if (absensi.isSynced) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    null,
                    tint = if (absensi.isSynced) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

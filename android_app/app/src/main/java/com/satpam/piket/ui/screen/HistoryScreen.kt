package com.satpam.piket.ui.screen

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
import com.satpam.piket.data.model.StatusKeterlambatan
import com.satpam.piket.data.db.AbsensiDao
import com.satpam.piket.domain.ShiftManager
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val absensiDao: AbsensiDao
) : ViewModel() {
    private val _absensiList = MutableStateFlow<List<Absensi>>(emptyList())
    val absensiList: StateFlow<List<Absensi>> = _absensiList.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            absensiDao.getAllAbsensi().collect { _absensiList.value = it }
        }
    }

    fun loadByDate(date: String) {
        viewModelScope.launch {
            absensiDao.getAbsensiByDate(date).collect { _absensiList.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val absensiList by viewModel.absensiList.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Absensi", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D47A1))
            )
        }
    ) { padding ->
        if (absensiList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("Belum ada riwayat absensi", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(absensiList) { absensi ->
                    HistoryAbsensiCard(absensi)
                }
            }
        }
    }
}

@Composable
fun HistoryAbsensiCard(absensi: Absensi) {
    val isLate = absensi.statusKeterlambatan == StatusKeterlambatan.TERLAMBAT
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(absensi.nama, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Surface(
                    color = if (isLate) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isLate) "TERLAMBAT" else "TEPAT WAKTU",
                        color = if (isLate) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Tanggal: ${absensi.tanggal} | Shift: ${absensi.shift.name}", color = Color.Gray, fontSize = 13.sp)
            Text("Shift ID: ${absensi.shiftId}", color = Color.Gray, fontSize = 11.sp)
            Text("Masuk: ${absensi.jamMasuk}", color = Color(0xFF212121), fontSize = 13.sp)
            if (absensi.jamKeluar != null) {
                Text("Keluar: ${absensi.jamKeluar}", color = Color(0xFF212121), fontSize = 13.sp)
            }
            if (absensi.durasi != null) {
                Text("Durasi: ${ShiftManager.formatDuration(absensi.durasi)}", color = Color(0xFF212121), fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (absensi.isSynced) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    null,
                    tint = if (absensi.isSynced) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (absensi.isSynced) "Tersinkron" else "Belum sinkron",
                    fontSize = 11.sp,
                    color = if (absensi.isSynced) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
            }
        }
    }
}

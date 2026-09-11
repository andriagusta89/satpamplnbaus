package com.satpam.piket.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.satpam.piket.data.db.PetugasDao
import com.satpam.piket.data.model.Petugas
import com.satpam.piket.domain.FaceRecognitionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

val API_URL_KEY = stringPreferencesKey("api_url")

/** Daftar lokasi penempatan satpam UP3 Baubau */
val LOKASI_LIST = listOf(
    "Kantor UP3 Baubau",
    "Rujab UP3 Baubau",
    "Gudang UP3 Baubau",
    "ULP Baubau Kota",
    "ULP Raha",
    "ULP Pasarwajo",
    "ULP Mawasangka",
    "ULP Wangi-wangi"
)

const val DEFAULT_API_URL = "https://script.google.com/macros/s/AKfycbxTIZvMf3DGQM5ecrohFobHNhCGwvZGUNOg-av19T4/exec"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val petugasDao: PetugasDao,
    private val faceRecognitionManager: FaceRecognitionManager,
    private val dataStore: DataStore<Preferences>,
    private val appUpdateManager: com.satpam.piket.domain.AppUpdateManager,
    private val apiService: com.satpam.piket.data.network.ApiService
) : ViewModel() {

    private val _petugasList = MutableStateFlow<List<Petugas>>(emptyList())
    val petugasList: StateFlow<List<Petugas>> = _petugasList.asStateFlow()

    private val _apiUrl = MutableStateFlow("")
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _updateCheckStatus = MutableStateFlow<String?>(null)
    val updateCheckStatus: StateFlow<String?> = _updateCheckStatus.asStateFlow()

    private val _updateInfo = MutableStateFlow<com.satpam.piket.data.model.AppVersionInfo?>(null)
    val updateInfo: StateFlow<com.satpam.piket.data.model.AppVersionInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _isSendingPetugas = MutableStateFlow<String?>(null)
    val isSendingPetugas: StateFlow<String?> = _isSendingPetugas.asStateFlow()

    private val _isSyncingPetugas = MutableStateFlow(false)
    val isSyncingPetugas: StateFlow<Boolean> = _isSyncingPetugas.asStateFlow()

    init {
        viewModelScope.launch {
            petugasDao.getAllPetugas().collect { _petugasList.value = it }
        }
        viewModelScope.launch {
            dataStore.data.map { it[API_URL_KEY] ?: "" }.collect { url ->
                // Auto-isi URL default GAS jika belum dikonfigurasi atau masih menggunakan /dev
                if (url.isBlank() || url.endsWith("/dev")) {
                    dataStore.edit { it[API_URL_KEY] = DEFAULT_API_URL }
                    _apiUrl.value = DEFAULT_API_URL
                } else {
                    _apiUrl.value = url
                }
            }
        }
        // Otomatis sinkronkan status approval petugas saat Pengaturan dibuka
        syncPetugasStatus(showToastOnEmpty = false)
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateCheckStatus.value = "Memeriksa versi di server..."
            val update = appUpdateManager.checkUpdate()
            _isCheckingUpdate.value = false
            if (update != null) {
                _updateInfo.value = update
                _updateCheckStatus.value = "Tersedia versi baru: v${update.versionName}"
            } else {
                _updateInfo.value = null
                _updateCheckStatus.value = "Aplikasi sudah menggunakan versi terbaru (v${appUpdateManager.getCurrentVersionName()})"
            }
        }
    }

    fun downloadUpdate() {
        val info = _updateInfo.value ?: return
        appUpdateManager.openDownloadPage(info.downloadUrl)
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun getCurrentVersionName(): String = appUpdateManager.getCurrentVersionName()
    fun getCurrentVersionCode(): Int = appUpdateManager.getCurrentVersionCode()

    fun saveApiUrl(url: String) {
        viewModelScope.launch {
            var cleanUrl = url.trim()
            if (cleanUrl.endsWith("/dev")) {
                cleanUrl = cleanUrl.substring(0, cleanUrl.length - 4) + "/exec"
            }
            if (!cleanUrl.endsWith("/exec")) {
                cleanUrl = if (cleanUrl.endsWith("/")) cleanUrl + "exec" else "$cleanUrl/exec"
            }
            dataStore.edit { it[API_URL_KEY] = cleanUrl }
            _apiUrl.value = cleanUrl
            _message.value = "URL API berhasil disimpan!"
            testServerConnection()
        }
    }

    fun testServerConnection() {
        viewModelScope.launch {
            _message.value = "⏳ Menguji koneksi ke server..."
            try {
                val resp = apiService.getLokasiKonfig()
                if (resp.isSuccessful) {
                    _message.value = "✅ Server Terhubung Online! (${resp.body()?.size ?: 0} lokasi aktif)"
                } else {
                    _message.value = "⚠️ Server merespons: HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _message.value = "❌ Gagal terhubung ke server: ${e.message}"
            }
        }
    }

    fun setMessage(msg: String) {
        _message.value = msg
    }

    fun registerPetugas(nama: String, jabatan: String, fotoPath: String) {
        viewModelScope.launch {
            val allLocal = petugasDao.getAllPetugasList()
            val newId = com.satpam.piket.domain.PetugasIdGenerator.generateNextId(allLocal)
            val petugas = Petugas(
                id = newId,
                nama = nama,
                jabatan = jabatan,
                fotoReferensi = fotoPath
            )
            petugasDao.insertPetugas(petugas)
            _message.value = "Petugas $nama ($newId) berhasil didaftarkan"
        }
    }

    fun deactivatePetugas(id: String) {
        viewModelScope.launch {
            petugasDao.deactivatePetugas(id)
            _message.value = "Petugas dinonaktifkan"
        }
    }

    fun deletePetugas(id: String) {
        viewModelScope.launch {
            petugasDao.deletePetugas(id)
            _message.value = "Data petugas berhasil dihapus dari HP"
        }
    }

    /** Kirim ulang data pendaftaran petugas ke backend jika sebelumnya gagal / pending */
    fun resendPetugasToBackend(petugas: Petugas) {
        viewModelScope.launch {
            _isSendingPetugas.value = petugas.id
            try {
                var fotoBase64 = ""
                val file = java.io.File(petugas.fotoReferensi)
                if (file.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        val portrait = com.satpam.piket.domain.FaceRecognitionManager.ensurePortrait(bitmap)
                        val scaled = scaleBitmap(portrait, 480)
                        val stream = java.io.ByteArrayOutputStream()
                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
                        fotoBase64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                    }
                }
                val response = apiService.submitPendingPetugas(
                    body = com.satpam.piket.data.network.PendingPetugasRequest(
                        id = petugas.id,
                        nama = petugas.nama,
                        jabatan = petugas.jabatan,
                        lokasi = petugas.lokasi,
                        fotoBase64 = fotoBase64
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _message.value = "Pendaftaran ${petugas.nama} berhasil dikirim ke backend!"
                } else {
                    _message.value = "Respon server: ${response.body()?.message ?: ("HTTP " + response.code())}"
                }
            } catch (e: Exception) {
                _message.value = "Gagal kirim ke server: ${e.message}"
            } finally {
                _isSendingPetugas.value = null
            }
        }
    }

    /** Sinkronkan status approval dari backend: update aktif = true bagi petugas yang telah disetujui */
    fun syncPetugasStatus(showToastOnEmpty: Boolean = true) {
        viewModelScope.launch {
            _isSyncingPetugas.value = true
            try {
                val response = apiService.getPetugasList()
                if (response.isSuccessful && response.body() != null) {
                    val serverApproved = response.body()!!
                    val localList = petugasDao.getAllPetugasList()
                    var updatedCount = 0
                    for (local in localList) {
                        if (!local.aktif) {
                            val match = serverApproved.firstOrNull {
                                it.id == local.id || it.nama.trim().equals(local.nama.trim(), ignoreCase = true)
                            }
                            if (match != null) {
                                if (local.id != match.id) {
                                    petugasDao.deletePetugas(local.id)
                                    petugasDao.insertPetugas(local.copy(id = match.id, aktif = true))
                                } else {
                                    petugasDao.updatePetugas(local.copy(aktif = true))
                                }
                                updatedCount++
                            }
                        }
                    }
                    if (updatedCount > 0) {
                        _message.value = "🎉 Berhasil: $updatedCount petugas telah disetujui admin & aktif!"
                    } else if (showToastOnEmpty) {
                        _message.value = "Sinkronisasi selesai. Belum ada persetujuan baru di server."
                    }
                } else if (showToastOnEmpty) {
                    _message.value = "Gagal mengambil data dari server (${response.code()})"
                }
            } catch (e: Exception) {
                if (showToastOnEmpty) {
                    _message.value = "Gagal koneksi sinkronisasi: ${e.message}"
                }
            } finally {
                _isSyncingPetugas.value = false
            }
        }
    }

    private fun scaleBitmap(source: android.graphics.Bitmap, maxDimension: Int = 480): android.graphics.Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (ratio > 1) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return android.graphics.Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEnrolFace: (nama: String, jabatan: String, lokasi: String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val petugasList by viewModel.petugasList.collectAsState()
    val apiUrl by viewModel.apiUrl.collectAsState()
    val message by viewModel.message.collectAsState()
    val updateCheckStatus by viewModel.updateCheckStatus.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isSendingPetugas by viewModel.isSendingPetugas.collectAsState()
    val isSyncingPetugas by viewModel.isSyncingPetugas.collectAsState()

    var showRegisterDialog by remember { mutableStateOf(false) }
    var showEditUrlDialog by remember { mutableStateOf(false) }
    var tempUrlInput by remember { mutableStateOf("") }

    // Show snackbar for messages
    val snackbarState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan Admin", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D47A1))
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 12.dp, top = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "created by @agusta_corp 2026",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Daftar Petugas Section
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader("Manajemen Petugas (${petugasList.size} terdaftar)")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Daftar Petugas",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            TextButton(
                                onClick = { viewModel.syncPetugasStatus() },
                                enabled = !isSyncingPetugas,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (isSyncingPetugas) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF1565C0)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                } else {
                                    Icon(Icons.Filled.Sync, null, modifier = Modifier.size(16.dp), tint = Color(0xFF1565C0))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text("Sinkronkan Approval", fontSize = 12.sp, color = Color(0xFF1565C0))
                            }
                        }

                        petugasList.forEach { petugas ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(38.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        color = if (petugas.aktif) Color(0xFF1565C0) else Color(0xFFE65100),
                                        shape = RoundedCornerShape(19.dp)
                                    ) {
                                        Text(
                                            petugas.nama.firstOrNull()?.toString() ?: "?",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(petugas.nama, fontWeight = FontWeight.Medium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(petugas.jabatan, fontSize = 12.sp, color = Color.Gray)
                                        if (petugas.lokasi.isNotBlank()) {
                                            Text(" • ${petugas.lokasi}", fontSize = 11.sp, color = Color(0xFF1976D2))
                                        }
                                    }
                                    if (!petugas.aktif) {
                                        Surface(
                                            color = Color(0xFFFFF3E0),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                "Menunggu Approval Admin",
                                                color = Color(0xFFE65100),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (petugas.aktif) {
                                    IconButton(onClick = { viewModel.deactivatePetugas(petugas.id) }) {
                                        Icon(Icons.Filled.PersonOff, null, tint = Color(0xFFC62828))
                                    }
                                } else {
                                    // Tombol kirim ulang ke server
                                    val isSending = isSendingPetugas == petugas.id
                                    if (isSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp).padding(2.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF1565C0)
                                        )
                                    } else {
                                        IconButton(onClick = { viewModel.resendPetugasToBackend(petugas) }) {
                                            Icon(
                                                Icons.Filled.CloudUpload,
                                                contentDescription = "Kirim ke Server",
                                                tint = Color(0xFF1565C0)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deletePetugas(petugas.id) }) {
                                        Icon(Icons.Filled.Delete, null, tint = Color.Gray)
                                    }
                                }
                            }
                            if (petugasList.last() != petugas) Divider()
                        }
                        if (petugasList.isEmpty()) {
                            Text("Belum ada petugas terdaftar", color = Color.Gray, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showRegisterDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Filled.PersonAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Daftarkan Petugas Baru")
                        }
                    }
                }
            }

            // Koneksi Server Backend Section
            item {
                SettingsSectionHeader("Koneksi Server Backend")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "URL Web App (Google Apps Script)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            TextButton(
                                onClick = {
                                    tempUrlInput = apiUrl.ifBlank { DEFAULT_API_URL }
                                    showEditUrlDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1565C0))
                                Spacer(Modifier.width(4.dp))
                                Text("Ubah URL", fontSize = 12.sp, color = Color(0xFF1565C0))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = apiUrl.ifBlank { DEFAULT_API_URL },
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color(0xFF333333),
                                modifier = Modifier.padding(8.dp),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasted = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                                        if (pasted.startsWith("https://script.google.com")) {
                                            viewModel.saveApiUrl(pasted)
                                        } else {
                                            viewModel.setMessage("Teks clipboard bukan URL Google Apps Script yang valid.")
                                        }
                                    } else {
                                        viewModel.setMessage("Clipboard kosong. Salin URL Web App dari browser terlebih dahulu.")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tempel URL", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.testServerConnection() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.NetworkCheck, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tes Koneksi", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Pembaruan Aplikasi Section
            item {
                SettingsSectionHeader("Pembaruan Aplikasi (APK)")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Versi Saat Ini",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "v${viewModel.getCurrentVersionName()} (Build ${viewModel.getCurrentVersionCode()})",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }

                            Button(
                                onClick = { viewModel.checkForUpdate() },
                                enabled = !isCheckingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Mengecek...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cek Update", fontSize = 12.sp)
                                }
                            }
                        }

                        if (updateCheckStatus != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = updateCheckStatus!!,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (updateInfo != null) Color(0xFF1565C0) else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showEditUrlDialog) {
        AlertDialog(
            onDismissRequest = { showEditUrlDialog = false },
            title = {
                Text("Atur URL Server Web App", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0D47A1))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Masukkan atau tempel URL Google Apps Script Web App hasil deploy (berakhiran /exec):",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    OutlinedTextField(
                        value = tempUrlInput,
                        onValueChange = { tempUrlInput = it },
                        label = { Text("URL Web App (/exec)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                    Text(
                        "💡 Tips: Anda juga bisa menyalin URL di web lalu menekan tombol 'Tempel URL'.",
                        fontSize = 11.sp,
                        color = Color(0xFF1565C0)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempUrlInput.isNotBlank()) {
                            viewModel.saveApiUrl(tempUrlInput.trim())
                            showEditUrlDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text("Simpan & Hubungkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditUrlDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
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
                        text = "Versi ${info.versionName} (Build ${info.versionCode}) siap diunduh.",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Versi Anda: v${viewModel.getCurrentVersionName()} (Build ${viewModel.getCurrentVersionCode()})",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    if (info.releaseNotes.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Catatan Rilis:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF333333)
                        )
                        Text(
                            text = info.releaseNotes,
                            fontSize = 12.sp,
                            color = Color(0xFF555555),
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.downloadUpdate() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download (Google Drive)")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Tutup", color = Color.Gray)
                }
            }
        )
    }

    if (showRegisterDialog) {
        RegisterPetugasDialog(
            onDismiss = { showRegisterDialog = false },
            onNavigateToEnrol = { nama, jabatan, lokasi ->
                showRegisterDialog = false
                onNavigateToEnrolFace(nama, jabatan, lokasi)
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Color(0xFF1565C0)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterPetugasDialog(
    onDismiss: () -> Unit,
    onNavigateToEnrol: (nama: String, jabatan: String, lokasi: String) -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var jabatan by remember { mutableStateOf("") }
    var lokasi by remember { mutableStateOf("") }
    var lokasiExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daftarkan Petugas Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Nama
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Lengkap") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Jabatan
                OutlinedTextField(
                    value = jabatan,
                    onValueChange = { jabatan = it },
                    label = { Text("Jabatan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Lokasi — dropdown
                ExposedDropdownMenuBox(
                    expanded = lokasiExpanded,
                    onExpandedChange = { lokasiExpanded = !lokasiExpanded }
                ) {
                    OutlinedTextField(
                        value = lokasi,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Lokasi Penempatan") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lokasiExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        placeholder = { Text("Pilih lokasi...") }
                    )
                    ExposedDropdownMenu(
                        expanded = lokasiExpanded,
                        onDismissRequest = { lokasiExpanded = false }
                    ) {
                        LOKASI_LIST.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    lokasi = item
                                    lokasiExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    "📸 Langkah berikutnya: ambil foto wajah petugas menggunakan kamera.",
                    fontSize = 12.sp,
                    color = Color(0xFF1565C0)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nama.isNotBlank() && lokasi.isNotBlank()) onNavigateToEnrol(nama, jabatan, lokasi) },
                enabled = nama.isNotBlank() && lokasi.isNotBlank()
            ) { Text("Lanjut → Foto Wajah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

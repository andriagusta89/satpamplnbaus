package com.satpam.piket.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.satpam.piket.data.db.AbsensiDao
import com.satpam.piket.data.db.PetugasDao
import com.satpam.piket.data.network.ApiService
import com.satpam.piket.data.model.Absensi
import com.satpam.piket.data.model.Shift
import com.satpam.piket.data.network.SyncWorker
import com.satpam.piket.domain.ShiftManager
import com.satpam.piket.data.model.AppVersionInfo
import com.satpam.piket.domain.AppUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.Application

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val absensiDao: AbsensiDao,
    private val petugasDao: PetugasDao,
    private val apiService: ApiService,
    private val application: Application,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<AppVersionInfo?>(null)
    val availableUpdate: StateFlow<AppVersionInfo?> = _availableUpdate.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _currentShift = MutableStateFlow(ShiftManager.getCurrentShift())
    val currentShift: StateFlow<Shift> = _currentShift.asStateFlow()

    private val _shiftId = MutableStateFlow(ShiftManager.generateShiftId())
    val shiftId: StateFlow<String> = _shiftId.asStateFlow()

    private val _absensiList = MutableStateFlow<List<Absensi>>(emptyList())
    val absensiList: StateFlow<List<Absensi>> = _absensiList.asStateFlow()

    private val _unsyncedCount = MutableStateFlow(0)
    val unsyncedCount: StateFlow<Int> = _unsyncedCount.asStateFlow()

    private val _currentTime = MutableStateFlow(ShiftManager.getCurrentTimeString())
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    init {
        loadTodayAbsensi()
        checkForAppUpdates()
        syncApprovedOfficers()
    }

    fun refresh() {
        _currentShift.value = ShiftManager.getCurrentShift()
        _shiftId.value = ShiftManager.generateShiftId()
        _currentTime.value = ShiftManager.getCurrentTimeString()
        loadTodayAbsensi()
        checkForAppUpdates()
        syncApprovedOfficers()
    }

    fun syncApprovedOfficers() {
        viewModelScope.launch {
            try {
                val response = apiService.getPetugasList()
                if (response.isSuccessful && response.body() != null) {
                    val serverApproved = response.body()!!
                    val localList = petugasDao.getAllPetugasList()
                    for (local in localList) {
                        if (!local.aktif) {
                            val match = serverApproved.firstOrNull {
                                it.id == local.id || it.nama.trim().equals(local.nama.trim(), ignoreCase = true)
                            }
                            if (match != null) {
                                petugasDao.updatePetugas(local.copy(aktif = true))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Background sync fails silently
            }
        }
    }

    fun checkForAppUpdates() {
        viewModelScope.launch {
            val update = appUpdateManager.checkUpdate()
            if (update != null) {
                _availableUpdate.value = update
                _showUpdateDialog.value = true
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun downloadAndInstallUpdate() {
        val update = _availableUpdate.value ?: return
        appUpdateManager.openDownloadPage(update.downloadUrl)
    }

    fun getCurrentVersionName(): String = appUpdateManager.getCurrentVersionName()
    fun getCurrentVersionCode(): Int = appUpdateManager.getCurrentVersionCode()

    private fun loadTodayAbsensi() {
        viewModelScope.launch {
            val today = ShiftManager.getCurrentDateString()
            absensiDao.getAbsensiByDate(today).collect { list ->
                _absensiList.value = list
                _unsyncedCount.value = list.count { !it.isSynced }
            }
        }
    }

    fun triggerManualSync() {
        val workManager = WorkManager.getInstance(application)
        workManager.enqueue(SyncWorker.buildOneTimeRequest())
    }
}

// ============================================================
// SATPAM PIKET - Google Apps Script Backend
// Spreadsheet ID: 1yE3Tw0pv2-VpVZCBsQ7suO5s0X4sn3XzfXMlADizU14
// ============================================================

const CONFIG = {
  SPREADSHEET_ID: '1yE3Tw0pv2-VpVZCBsQ7suO5s0X4sn3XzfXMlADizU14',
  FONNTE_TOKEN: 'ISI_TOKEN_FONNTE_ANDA',   // Ganti dengan token Fonnte
  WA_GROUP_ID: 'NOMOR_GRUP@g.us',           // Ganti dengan ID grup WA
  SHEET_ABSENSI: 'absensi',
  SHEET_PETUGAS: 'petugas',
  SHEET_SHIFT_LOG: 'shift_log',
  SHEET_PENDING: 'pending_petugas',
  SHEET_LOKASI: 'lokasi_konfig',
  SHEET_HANDOVER: 'handover_log',
  SHEET_APP_VERSION: 'app_version',
  TOLERANCE_MINUTES: 15
};

// ============================================================
// HTTP HANDLERS
// ============================================================

function doGet(e) {
  const action = (e && e.parameter && e.parameter.action) || 'dashboard';

  if (action === 'getDashboard') {
    const tanggal = (e && e.parameter && e.parameter.tanggal) || getTodayDate();
    return jsonResponse(getDashboardData(tanggal));
  }

  if (action === 'getPetugas') {
    return jsonResponse(getPetugasList());
  }

  if (action === 'getShift') {
    return jsonResponse({
      shift: getCurrentShift(),
      shiftId: generateShiftId(),
      time: Utilities.formatDate(new Date(), 'Asia/Makassar', 'HH:mm:ss')
    });
  }

  // Ambil daftar pending petugas untuk admin
  if (action === 'getPendingList') {
    return jsonResponse(getPendingList());
  }

  // Ambil daftar setting koordinat & radius lokasi
  if (action === 'getLokasiKonfig') {
    return jsonResponse(getLokasiKonfig());
  }

  // Ambil pesan kesiapsiagaan
  if (action === 'getPesanKesiapsiagaan') {
    return jsonResponse({ success: true, pesan: getPesanKesiapsiagaan() });
  }

  // Ambil checklist monitoring 8 lokasi
  if (action === 'getMonitoringChecklist') {
    const tanggal = (e && e.parameter && e.parameter.tanggal) || getTodayDate();
    const shift = (e && e.parameter && e.parameter.shift) || getCurrentShift();
    return jsonResponse(getMonitoringData(shift, tanggal));
  }

  // Ambil versi aplikasi terbaru untuk Android update checker
  if (action === 'getLatestVersion') {
    return jsonResponse(getLatestAppVersion());
  }

  // Ambil riwayat rilis versi aplikasi
  if (action === 'getAppVersionHistory') {
    return jsonResponse(getAppVersionHistory());
  }

  // Ambil URL Web App aktif
  if (action === 'getActiveApiUrl') {
    return jsonResponse({
      success: true,
      apiUrl: getActiveApiUrl()
    });
  }

  // Ambil konfigurasi Fonnte WhatsApp
  if (action === 'getFonnteConfig') {
    return jsonResponse(getFonnteConfig());
  }

  // Ambil ID Petugas berikutnya berformat PAM-YYYY001 dst
  if (action === 'getNextPetugasId') {
    return jsonResponse({
      success: true,
      nextId: generateNextPetugasId()
    });
  }

  // Migrasi ID petugas lama (UUID / format lama) ke format PAM-YYYY001 dst
  if (action === 'migratePetugasIds') {
    return jsonResponse(migratePetugasIdsToPam());
  }

  // Default: Jika file Dashboard.html ada di Apps Script, sajikan.
  // Jika file Dashboard.html dihapus, otomatis alihkan (redirect) ke GitHub Pages.
  try {
    const html = HtmlService.createTemplateFromFile('Dashboard');
    try {
      html.data = getDashboardData(getTodayDate());
    } catch (err) {
      html.data = {};
    }
    html.activeApiUrl = getActiveApiUrl();
    return html.evaluate()
      .setTitle('Dashboard Piket Satpam')
      .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
  } catch (noHtmlErr) {
    return HtmlService.createHtmlOutput(
      '<!DOCTYPE html><html><head>' +
      '<meta http-equiv="refresh" content="0; url=https://andriagusta89.github.io/satpamplnbaus/">' +
      '<script>window.location.replace("https://andriagusta89.github.io/satpamplnbaus/");<\/script>' +
      '</head><body style="font-family:sans-serif; text-align:center; padding-top:50px;">' +
      '<h3>Mengarahkan ke Dashboard Utama...</h3>' +
      '<p><a href="https://andriagusta89.github.io/satpamplnbaus/">Klik di sini jika tidak otomatis dialihkan</a></p>' +
      '</body></html>'
    ).setTitle('Dashboard Piket Satpam');
  }
}

function doPost(e) {
  try {
    let data = {};
    let action = (e && e.parameter && e.parameter.action) || '';

    if (e && e.postData && e.postData.contents) {
      try {
        data = JSON.parse(e.postData.contents);
        if (!action && data.action) action = data.action;
      } catch (jsonErr) {
        // Bukan JSON murni (misal multipart/form-data atau urlencoded)
        if (e && e.parameter) {
          data = Object.assign({}, e.parameter);
          if (!action && data.action) action = data.action;
        }
      }
    } else if (e && e.parameter) {
      data = Object.assign({}, e.parameter);
      if (!action && data.action) action = data.action;
    }

    if (!action) action = 'submitAbsensi';
    let result = null;

    if (action === 'submitAbsensi') {
      result = submitAbsensi(data);
    } else if (action === 'uploadFoto') {
      result = uploadFoto(data);
    } else if (action === 'registerPetugas') {
      result = registerPetugas(data);
    } else if (action === 'submitPendingPetugas') {
      result = submitPendingPetugas(data);
    } else if (action === 'approvePetugas') {
      result = approvePetugas(data);
    } else if (action === 'rejectPetugas') {
      result = rejectPetugas(data);
    } else if (action === 'saveLokasiKonfig') {
      result = saveLokasiKonfig(data);
    } else if (action === 'triggerHandover') {
      result = processShiftHandover(data.lokasi, data.shift);
    } else if (action === 'savePesanKesiapsiagaan') {
      result = savePesanKesiapsiagaan(data.pesan);
    } else if (action === 'sendChecklistWA') {
      result = sendMonitoringChecklistUpdate(data.shift, data.tanggal, true);
    } else if (action === 'sendLaporanLengkapWA') {
      result = sendLaporanLengkapShift(data.shift, data.tanggal);
    } else if (action === 'publishAppVersion') {
      result = publishAppVersion(data);
    } else if (action === 'uploadApkToDrive') {
      result = uploadApkToDrive(data);
    } else if (action === 'saveActiveApiUrl') {
      result = saveActiveApiUrl(data.apiUrl);
    } else if (action === 'saveFonnteConfig') {
      result = saveFonnteConfig(data);
    } else if (action === 'testFonnteConnection') {
      result = testFonnteConnection(data);
    } else if (action === 'getNextPetugasId') {
      result = { success: true, nextId: generateNextPetugasId() };
    } else if (action === 'migratePetugasIds') {
      result = migratePetugasIdsToPam();
    } else {
      result = { success: false, message: 'Action tidak dikenal: ' + action };
    }

    return jsonResponse(result);
  } catch (err) {
    return jsonResponse({ success: false, message: 'Error doPost: ' + err.message });
  }
}


// ============================================================
// ABSENSI FUNCTIONS
// ============================================================

function submitAbsensi(data) {
  try {
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = ss.getSheetByName(CONFIG.SHEET_ABSENSI);

    if (!sheet) return jsonResponse({ success: false, message: 'Sheet absensi tidak ditemukan. Jalankan setupSheets() dahulu.' });

    // Upload foto langsung jika disertakan dalam data (atomic upload)
    let fotoUrl = data.fotoUrl || '';
    const rawPhoto = data.fotoBase64 || data.foto || data.base64;
    if (rawPhoto && !fotoUrl) {
      try {
        const folder = getOrCreateFolder('SatpamPiket_Foto');
        let base64Clean = String(rawPhoto).trim();
        if (base64Clean.indexOf(',') > -1) {
          base64Clean = base64Clean.split(',')[1];
        }
        base64Clean = base64Clean.replace(/\s+/g, '+');
        const blob = Utilities.newBlob(
          Utilities.base64Decode(base64Clean),
          'image/jpeg',
          'absen_' + data.id + '.jpg'
        );
        const file = folder.createFile(blob);
        try {
          file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
        } catch (shareErr) {
          Logger.log('Warning setSharing submitAbsensi: ' + shareErr.message);
        }
        fotoUrl = 'https://lh3.googleusercontent.com/d/' + file.getId();
        data.fotoUrl = fotoUrl;
      } catch (uploadErr) {
        Logger.log('Gagal upload foto absensi di submitAbsensi: ' + uploadErr.message);
      }
    }

    // Cek duplikasi
    const existing = findRowById(sheet, data.id);
    if (existing > 0) {
      // Jika sudah ada dan sekarang ada fotoUrl baru, perbarui foto di sheet
      if (fotoUrl) {
        try {
          const headers = sheet.getDataRange().getValues()[0];
          const colFoto = headers.indexOf('fotoUrl') + 1;
          const targetCol = colFoto > 0 ? colFoto : 14;
          sheet.getRange(existing, targetCol).setValue(fotoUrl);
        } catch (updateErr) {
          Logger.log('Gagal update foto row existing: ' + updateErr.message);
        }
      }
      return jsonResponse({ success: true, message: 'Data sudah ada (duplicate)', data: { id: data.id, fotoUrl: fotoUrl } });
    }

    const tipeAbsen = data.tipeAbsen || 'MASUK';

    // Safeguard: Petugas tidak bisa Lepas Piket (KELUAR) jika belum pernah ada riwayat MASUK
    if (tipeAbsen === 'KELUAR') {
      const hasMasuk = checkPetugasHasMasukRecord(sheet, data.petugasId, data.nama);
      if (!hasMasuk) {
        return jsonResponse({
          success: false,
          message: 'Gagal Lepas Piket: Petugas ' + (data.nama || data.petugasId) + ' belum melakukan Absen Masuk.'
        });
      }
    }

    const statusLate = calculateLateStatus(data.jamMasuk, data.shift);
    const row = [
      data.id,
      data.shiftId,
      data.petugasId,
      data.nama,
      data.lokasi || '',
      data.tanggal,
      data.shift,
      tipeAbsen,
      data.jamMasuk || '',
      data.jamKeluar || '',
      data.durasi || '',
      statusLate,
      data.keterangan || '',
      fotoUrl,  // fotoUrl (tersimpan langsung jika fotoBase64 disertakan)
      data.latitude || '',
      data.longitude || '',
      new Date().toISOString()
    ];

    sheet.appendRow(row);

    // Kirim pembaruan checklist monitoring 8 lokasi ke grup WA setiap ada absen masuk maupun keluar
    sendMonitoringChecklistUpdate(data.shift, data.tanggal);

    // Kirim notifikasi WA real-time perorangan
    if (tipeAbsen === 'MASUK') {
      sendCheckInNotification(data, statusLate);
      if (data.lokasi) {
        processShiftHandover(data.lokasi, data.shift);
      }
    }

    return jsonResponse({ success: true, message: 'Absensi berhasil disimpan', data: { id: data.id, fotoUrl: fotoUrl } });
  } catch (err) {
    return jsonResponse({ success: false, message: 'Error submitAbsensi: ' + err.message });
  }
}

function uploadFoto(data) {
  try {
    const id = data.id || (data.parameter && data.parameter.id);
    const rawPhoto = data.fotoBase64 || data.foto || data.base64;

    // Simpan base64 foto ke Google Drive
    if (!rawPhoto || !id) {
      return jsonResponse({ success: false, message: 'Data foto tidak lengkap' });
    }

    const folder = getOrCreateFolder('SatpamPiket_Foto');
    let base64Clean = String(rawPhoto).trim();
    if (base64Clean.indexOf(',') > -1) {
      base64Clean = base64Clean.split(',')[1];
    }
    base64Clean = base64Clean.replace(/\s+/g, '+');
    const blob = Utilities.newBlob(
      Utilities.base64Decode(base64Clean),
      'image/jpeg',
      'absen_' + id + '.jpg'
    );
    const file = folder.createFile(blob);
    try {
      file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
    } catch (shareErr) {
      Logger.log('Warning setSharing uploadFoto: ' + shareErr.message);
    }

    const fotoUrl = 'https://lh3.googleusercontent.com/d/' + file.getId();

    // Update fotoUrl di sheet
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = ss.getSheetByName(CONFIG.SHEET_ABSENSI);
    const row = findRowById(sheet, id);
    let rowDataObj = null;

    if (row > 0) {
      const headers = sheet.getDataRange().getValues()[0];
      const colFoto = headers.indexOf('fotoUrl') + 1;
      const targetCol = colFoto > 0 ? colFoto : 14;
      sheet.getRange(row, targetCol).setValue(fotoUrl);

      const fullRow = sheet.getRange(row, 1, 1, headers.length).getValues()[0];
      rowDataObj = {};
      headers.forEach((h, i) => rowDataObj[h] = fullRow[i]);
    }

    // Jika ini adalah absen MASUK dan ada lokasi, otomatis periksa dan proses laporan pergantian shift
    if (rowDataObj && rowDataObj.lokasi) {
      processShiftHandover(rowDataObj.lokasi, rowDataObj.shift);
    }

    return jsonResponse({ success: true, message: 'Foto berhasil diupload', data: fotoUrl });
  } catch (err) {
    return jsonResponse({ success: false, message: 'Error uploadFoto: ' + err.message });
  }
}

function getPetugasList() {
  try {
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = ss.getSheetByName(CONFIG.SHEET_PETUGAS);
    if (!sheet) return [];

    const data = sheet.getDataRange().getValues();
    if (data.length <= 1) return [];
    const headers = data[0];
    const list = data.slice(1).map(row => {
      const obj = {};
      headers.forEach((h, i) => obj[h] = row[i]);
      let createdMs = Date.now();
      if (obj.createdAt) {
        const parsed = new Date(obj.createdAt).getTime();
        if (!isNaN(parsed)) createdMs = parsed;
      }
      let fotoRef = String(obj.fotoReferensi || obj.fotoUrl || '');
      if (fotoRef) {
        const dId = extractDriveId(fotoRef);
        if (dId) {
          fotoRef = 'https://lh3.googleusercontent.com/d/' + dId;
        }
      }
      return {
        id: String(obj.id || ''),
        nama: String(obj.nama || ''),
        jabatan: String(obj.jabatan || ''),
        lokasi: String(obj.lokasi || ''),
        fotoReferensi: fotoRef,
        aktif: obj.aktif === true || String(obj.aktif).toUpperCase() === 'TRUE',
        createdAt: createdMs
      };
    }).filter(p => p.aktif);

    return JSON.parse(JSON.stringify(list));
  } catch (err) {
    return [];
  }
}

function registerPetugas(data) {
  try {
    let assignedId = String(data.id || '').trim();
    if (!assignedId || !assignedId.startsWith('PAM-')) {
      assignedId = generateNextPetugasId();
    }
    data.id = assignedId;

    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = ss.getSheetByName(CONFIG.SHEET_PETUGAS);
    sheet.appendRow([
      assignedId,
      data.nama,
      data.jabatan,
      data.lokasi || '',
      data.fotoReferensi || '',
      true,
      new Date().toISOString(),
      new Date().toISOString(),
      'admin'
    ]);
    return jsonResponse({ success: true, message: 'Petugas ' + data.nama + ' (' + assignedId + ') berhasil didaftarkan', id: assignedId });
  } catch (err) {
    return jsonResponse({ success: false, message: err.message });
  }
}

// ============================================================
// APPROVAL FUNCTIONS
// ============================================================

/**
 * Terima pendaftaran petugas baru dari Android (status = PENDING).
 * Foto referensi dikirim sebagai base64 dan disimpan ke Google Drive.
 * Data disimpan ke sheet pending_petugas menunggu persetujuan admin.
 *
 * Body: { id, nama, jabatan, fotoBase64 }
 */
function submitPendingPetugas(data) {
  try {
    if (!data.nama) {
      return { success: false, message: 'Data tidak lengkap (nama diperlukan)' };
    }

    // Pastikan ID memiliki format resmi PAM-YYYY001 dst (contoh: PAM-2026001)
    let assignedId = String(data.id || '').trim();
    if (!assignedId || !assignedId.startsWith('PAM-')) {
      assignedId = generateNextPetugasId();
    }
    data.id = assignedId;

    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    let sheet = ss.getSheetByName(CONFIG.SHEET_PENDING);
    if (!sheet) {
      sheet = ss.insertSheet(CONFIG.SHEET_PENDING);
      sheet.appendRow([
        'id', 'nama', 'jabatan', 'lokasi', 'fotoBase64', 'fotoUrl', 'status',
        'submittedAt', 'reviewedAt', 'reviewNote'
      ]);
    }

    // Upload foto ke Drive jika ada
    let fotoUrl = '';
    if (data.fotoBase64) {
      try {
        const folder = getOrCreateFolder('SatpamPiket_FotoEnrol');
        let base64Clean = String(data.fotoBase64).trim();
        if (base64Clean.indexOf(',') > -1) {
          base64Clean = base64Clean.split(',')[1];
        }
        const blob = Utilities.newBlob(
          Utilities.base64Decode(base64Clean),
          'image/jpeg',
          'enrol_' + data.id + '.jpg'
        );
        const file = folder.createFile(blob);
        try {
          file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
        } catch (shareErr) {
          Logger.log('Warning setSharing submitPendingPetugas: ' + shareErr.message);
        }
        fotoUrl = 'https://lh3.googleusercontent.com/d/' + file.getId();
      } catch (uploadErr) {
        Logger.log('Gagal upload foto enrol: ' + uploadErr.message);
      }
    }

    // Cek duplikasi
    const existing = findRowById(sheet, data.id);
    if (existing > 0) {
      // Jika data sudah terdaftar tapi foto belum ada di sheet dan sekarang ada fotoUrl baru, perbarui kolom foto
      if (fotoUrl) {
        try {
          const headers = sheet.getDataRange().getValues()[0];
          const colFoto = headers.indexOf('fotoUrl') + 1;
          const targetCol = colFoto > 0 ? colFoto : 6;
          const currentVal = sheet.getRange(existing, targetCol).getValue();
          if (!currentVal) {
            sheet.getRange(existing, targetCol).setValue(fotoUrl);
          }
        } catch (updateErr) {
          Logger.log('Gagal update foto row existing: ' + updateErr.message);
        }
      }
      return { success: true, message: 'Pendaftaran sudah ada sebelumnya', data: { id: data.id, fotoUrl: fotoUrl } };
    }

    // Hindari overflow limit 50.000 karakter per cell Google Sheets dengan tidak menulis base64 mentah ke cell
    sheet.appendRow([
      data.id,
      data.nama,
      data.jabatan || '',
      data.lokasi || '',       // lokasi penempatan petugas
      '',                      // fotoBase64 cell dikosongkan untuk mencegah limit 50k char cell
      fotoUrl,                 // URL Drive untuk preview di dashboard
      'PENDING',
      new Date().toISOString(),
      '',                      // reviewedAt
      ''                       // reviewNote
    ]);

    // Notifikasi WA ke admin
    sendApprovalNotification(data.nama, data.jabatan || '');

    return {
      success: true,
      message: 'Pendaftaran ' + data.nama + ' berhasil dikirim, menunggu persetujuan admin.',
      data: { id: data.id, fotoUrl: fotoUrl }
    };
  } catch (err) {
    Logger.log('Error submitPendingPetugas: ' + err.message);
    return { success: false, message: 'Error submitPendingPetugas: ' + err.message };
  }
}

/**
 * Ambil daftar semua petugas yang masih PENDING.
 * Dipanggil dari dashboard admin.
 */
function getPendingList() {
  try {
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = ss.getSheetByName(CONFIG.SHEET_PENDING);
    if (!sheet) return { pending: [], approved: [], rejected: [] };

    const data = sheet.getDataRange().getValues();
    if (data.length <= 1) return { pending: [], approved: [], rejected: [] };
    const headers = data[0];

    const rows = data.slice(1).map(row => {
      const obj = {};
      headers.forEach((h, i) => {
        const val = row[i];
        if (val instanceof Date) {
          obj[h] = Utilities.formatDate(val, 'Asia/Makassar', "yyyy-MM-dd'T'HH:mm:ss'Z'");
        } else if (typeof val === 'number' || typeof val === 'boolean') {
          obj[h] = val;
        } else {
          obj[h] = (val === null || val === undefined) ? '' : String(val);
        }
      });
      if (obj.fotoUrl) {
        const dId = extractDriveId(String(obj.fotoUrl));
        if (dId) {
          obj.fotoUrl = 'https://lh3.googleusercontent.com/d/' + dId;
        }
      }
      return obj;
    });

    const pending = rows.filter(r => String(r.status).trim().toUpperCase() === 'PENDING');
    const approved = rows.filter(r => String(r.status).trim().toUpperCase() === 'APPROVED');
    const rejected = rows.filter(r => String(r.status).trim().toUpperCase() === 'REJECTED');

    return JSON.parse(JSON.stringify({
      pending: pending,
      approved: approved,
      rejected: rejected
    }));
  } catch (err) {
    return { error: err.message, pending: [], approved: [], rejected: [] };
  }
}

/**
 * Admin menyetujui petugas pending.
 * - Status di pending_petugas diubah ke APPROVED
 * - Petugas ditambahkan ke sheet petugas (resmi aktif)
 *
 * Body: { id, approvedBy, note }
 */
function approvePetugas(data) {
  try {
    if (!data.id) return { success: false, message: 'ID pendaftaran diperlukan' };

    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const pendingSheet = ss.getSheetByName(CONFIG.SHEET_PENDING);
    if (!pendingSheet) return { success: false, message: 'Sheet pending_petugas tidak ditemukan' };

    // Cari baris pending
    const rowIdx = findRowById(pendingSheet, data.id);
    if (rowIdx < 0) return { success: false, message: 'Pendaftaran dengan ID ' + data.id + ' tidak ditemukan' };

    const pendingData = pendingSheet.getDataRange().getValues();
    const headers = pendingData[0];
    const rowData = pendingData[rowIdx - 1]; // rowIdx adalah 1-based row number
    const pending = {};
    headers.forEach((h, i) => pending[h] = rowData[i]);

    // Cek kalau sudah di-review
    if (pending.status === 'APPROVED') {
      return { success: false, message: 'Petugas ini sudah disetujui sebelumnya' };
    }
    if (pending.status === 'REJECTED') {
      return { success: false, message: 'Petugas ini sudah ditolak sebelumnya' };
    }

    const now = new Date().toISOString();
    const approvedBy = data.approvedBy || 'admin';

    // Pastikan ID memiliki format resmi PAM-YYYY001 dst
    let officialId = String(pending.id || '').trim();
    if (!officialId || !officialId.startsWith('PAM-')) {
      officialId = generateNextPetugasId();
      pendingSheet.getRange(rowIdx, 1).setValue(officialId);
      pending.id = officialId;
    }

    // Update status di sheet pending
    // Kolom: id(1), nama(2), jabatan(3), lokasi(4), fotoBase64(5), fotoUrl(6), status(7), submittedAt(8), reviewedAt(9), reviewNote(10)
    pendingSheet.getRange(rowIdx, 7).setValue('APPROVED');
    pendingSheet.getRange(rowIdx, 9).setValue(now);
    pendingSheet.getRange(rowIdx, 10).setValue(data.note || 'Disetujui');

    // Tambahkan ke sheet petugas resmi
    const petugasSheet = ss.getSheetByName(CONFIG.SHEET_PETUGAS);
    if (!petugasSheet) return { success: false, message: 'Sheet petugas tidak ditemukan' };

    // Cek apakah ID sudah ada di petugas (hindari duplikat)
    const petugasExisting = findRowById(petugasSheet, pending.id);
    if (petugasExisting < 0) {
      const pData = petugasSheet.getDataRange().getValues();
      const pHeaders = (pData && pData.length > 0) ? pData[0] : [];
      if (pHeaders.length > 0) {
        const newRow = pHeaders.map(h => {
          const colName = String(h).trim();
          if (colName === 'id') return pending.id;
          if (colName === 'nama') return pending.nama;
          if (colName === 'jabatan') return pending.jabatan;
          if (colName === 'lokasi') return pending.lokasi || '';
          if (colName === 'fotoReferensi' || colName === 'fotoUrl') return pending.fotoUrl || '';
          if (colName === 'aktif') return true;
          if (colName === 'submittedAt') return pending.submittedAt || now;
          if (colName === 'createdAt' || colName === 'approvedAt') return now;
          if (colName === 'approvedBy') return approvedBy;
          return '';
        });
        petugasSheet.appendRow(newRow);
      } else {
        petugasSheet.appendRow([
          pending.id,
          pending.nama,
          pending.jabatan,
          pending.lokasi || '',
          pending.fotoUrl || '',
          true,
          pending.submittedAt || now,
          now,
          approvedBy
        ]);
      }
    }

    // Notifikasi WA: petugas disetujui
    sendApprovalResultNotification(pending.nama, 'APPROVED', data.note || '', pending.id);

    return {
      success: true,
      message: 'Petugas ' + pending.nama + ' (' + pending.id + ') berhasil disetujui dan aktif.',
      data: { id: pending.id, nama: pending.nama }
    };
  } catch (err) {
    return { success: false, message: 'Error approvePetugas: ' + err.message };
  }
}

/**
 * Admin menolak petugas pending.
 * Status diubah ke REJECTED disertai alasan.
 *
 * Body: { id, approvedBy, note }
 */
function rejectPetugas(data) {
  try {
    if (!data.id) return { success: false, message: 'ID pendaftaran diperlukan' };

    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const pendingSheet = ss.getSheetByName(CONFIG.SHEET_PENDING);
    if (!pendingSheet) return { success: false, message: 'Sheet pending_petugas tidak ditemukan' };

    const rowIdx = findRowById(pendingSheet, data.id);
    if (rowIdx < 0) return { success: false, message: 'Pendaftaran dengan ID ' + data.id + ' tidak ditemukan' };

    const pendingData = pendingSheet.getDataRange().getValues();
    const headers = pendingData[0];
    const rowData = pendingData[rowIdx - 1];
    const pending = {};
    headers.forEach((h, i) => pending[h] = rowData[i]);

    if (pending.status !== 'PENDING') {
      return { success: false, message: 'Pendaftaran ini sudah diproses sebelumnya (status: ' + pending.status + ')' };
    }

    const now = new Date().toISOString();

    // Update status di sheet pending
    // Kolom: id(1), nama(2), jabatan(3), lokasi(4), fotoBase64(5), fotoUrl(6), status(7), submittedAt(8), reviewedAt(9), reviewNote(10)
    pendingSheet.getRange(rowIdx, 7).setValue('REJECTED');
    pendingSheet.getRange(rowIdx, 9).setValue(now);
    pendingSheet.getRange(rowIdx, 10).setValue(data.note || 'Ditolak oleh admin');

    // Notifikasi WA: petugas ditolak
    sendApprovalResultNotification(pending.nama, 'REJECTED', data.note || 'Ditolak oleh admin', pending.id);

    return {
      success: true,
      message: 'Pendaftaran ' + pending.nama + ' telah ditolak.',
      data: { id: pending.id }
    };
  } catch (err) {
    return { success: false, message: 'Error rejectPetugas: ' + err.message };
  }
}

// Notifikasi WA saat ada pendaftaran baru masuk
function sendApprovalNotification(nama, jabatan, id) {
  try {
    const message = `🆕 *PENDAFTARAN PETUGAS BARU*\n` +
      `━━━━━━━━━━━━━━━━\n` +
      (id ? `🆔 ID      : *${id}*\n` : '') +
      `👤 Nama    : ${nama}\n` +
      `💼 Jabatan : ${jabatan || '-'}\n` +
      `⏳ Status  : *MENUNGGU APPROVAL ADMIN*\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `Silakan buka dashboard admin untuk menyetujui atau menolak.\n` +
      `_SatpamPiket System_`;
    sendWhatsApp(message);
  } catch (err) {
    Logger.log('Error WA approval notif: ' + err.message);
  }
}

// Notifikasi WA saat pendaftaran di-approve / reject
function sendApprovalResultNotification(nama, result, note, id) {
  try {
    const emoji = result === 'APPROVED' ? '✅' : '❌';
    const label = result === 'APPROVED' ? 'DISETUJUI' : 'DITOLAK';
    const message = `${emoji} *HASIL PENDAFTARAN PETUGAS*\n` +
      `━━━━━━━━━━━━━━━━\n` +
      (id ? `🆔 ID      : *${id}*\n` : '') +
      `👤 Nama    : ${nama}\n` +
      `📊 Status  : *${label}*\n` +
      (note ? `📝 Catatan : ${note}\n` : '') +
      `━━━━━━━━━━━━━━━━\n` +
      `_SatpamPiket System_`;
    sendWhatsApp(message);
  } catch (err) {
    Logger.log('Error WA approval result notif: ' + err.message);
  }
}

// ============================================================
// PETUGAS ID GENERATOR & MIGRATION (PAM-YYYY001 dst)
// ============================================================

/**
 * Menghasilkan ID Petugas resmi berikutnya dengan format:
 * PAM-YYYY001, PAM-YYYY002, dan seterusnya (contoh: PAM-2026001).
 * Memeriksa sheet 'petugas' dan 'pending_petugas' agar urutan selalu unik dan berkelanjutan.
 */
function generateNextPetugasId() {
  const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
  const year = Utilities.formatDate(new Date(), 'Asia/Makassar', 'yyyy');
  const prefix = 'PAM-' + year;
  let maxSeq = 0;

  // 1. Cek di sheet petugas resmi
  const petugasSheet = ss.getSheetByName(CONFIG.SHEET_PETUGAS);
  if (petugasSheet) {
    const data = petugasSheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const idStr = String(data[i][0] || '').trim();
      if (idStr.startsWith(prefix)) {
        const numPart = parseInt(idStr.substring(prefix.length), 10);
        if (!isNaN(numPart) && numPart > maxSeq) {
          maxSeq = numPart;
        }
      }
    }
  }

  // 2. Cek juga di sheet pending_petugas agar tidak bentrok
  const pendingSheet = ss.getSheetByName(CONFIG.SHEET_PENDING);
  if (pendingSheet) {
    const data = pendingSheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const idStr = String(data[i][0] || '').trim();
      if (idStr.startsWith(prefix)) {
        const numPart = parseInt(idStr.substring(prefix.length), 10);
        if (!isNaN(numPart) && numPart > maxSeq) {
          maxSeq = numPart;
        }
      }
    }
  }

  const nextSeq = maxSeq + 1;
  const seqStr = String(nextSeq).padStart(3, '0');
  return prefix + seqStr;
}

/**
 * Migrasikan seluruh ID petugas lama (seperti UUID atau petugas-001)
 * ke format standar PAM-YYYY001 dan seterusnya.
 * Memperbarui sheet 'petugas', 'pending_petugas', dan referensi di sheet 'absensi'.
 */
function migratePetugasIdsToPam() {
  const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
  const year = Utilities.formatDate(new Date(), 'Asia/Makassar', 'yyyy');
  const prefix = 'PAM-' + year;
  let counter = 1;
  const idMap = {};

  // 1. Cari nomor urut tertinggi yang sudah pakai prefix PAM-YYYY
  const pSheet = ss.getSheetByName(CONFIG.SHEET_PETUGAS);
  if (pSheet) {
    const pData = pSheet.getDataRange().getValues();
    for (let i = 1; i < pData.length; i++) {
      const existingId = String(pData[i][0] || '').trim();
      if (existingId.startsWith(prefix)) {
        const numPart = parseInt(existingId.substring(prefix.length), 10);
        if (!isNaN(numPart) && numPart >= counter) {
          counter = numPart + 1;
        }
      }
    }

    // Ubah baris yang belum pakai prefix PAM-YYYY
    for (let i = 1; i < pData.length; i++) {
      const oldId = String(pData[i][0] || '').trim();
      if (oldId && !oldId.startsWith(prefix)) {
        const newId = prefix + String(counter).padStart(3, '0');
        counter++;
        idMap[oldId] = newId;
        pSheet.getRange(i + 1, 1).setValue(newId);
      }
    }
  }

  // 2. Migrasi sheet pending_petugas
  const pendSheet = ss.getSheetByName(CONFIG.SHEET_PENDING);
  if (pendSheet) {
    const pendData = pendSheet.getDataRange().getValues();
    for (let i = 1; i < pendData.length; i++) {
      const oldId = String(pendData[i][0] || '').trim();
      if (oldId && !oldId.startsWith(prefix)) {
        const newId = idMap[oldId] || (prefix + String(counter).padStart(3, '0'));
        if (!idMap[oldId]) counter++;
        idMap[oldId] = newId;
        pendSheet.getRange(i + 1, 1).setValue(newId);
      }
    }
  }

  // 3. Perbarui relasi petugasId di sheet absensi (kolom 3)
  const absSheet = ss.getSheetByName(CONFIG.SHEET_ABSENSI);
  if (absSheet && Object.keys(idMap).length > 0) {
    const absData = absSheet.getDataRange().getValues();
    for (let i = 1; i < absData.length; i++) {
      const oldPetugasId = String(absData[i][2] || '').trim();
      if (idMap[oldPetugasId]) {
        absSheet.getRange(i + 1, 3).setValue(idMap[oldPetugasId]);
      }
    }
  }

  return {
    success: true,
    message: 'Berhasil migrasi ' + Object.keys(idMap).length + ' ID petugas ke format ' + prefix + 'xxx.',
    mapped: idMap
  };
}



function getCurrentShift() {
  const now = new Date();
  const hour = parseInt(Utilities.formatDate(now, 'Asia/Makassar', 'HH'));
  if (hour >= 7 && hour < 15) return 'PAGI';
  if (hour >= 15 && hour < 23) return 'SIANG';
  return 'MALAM';
}

function generateShiftId(date) {
  const d = date || new Date();
  const shift = getCurrentShift();
  const hour = parseInt(Utilities.formatDate(d, 'Asia/Makassar', 'HH'));

  // Jam 00-06 masih shift malam hari sebelumnya
  let refDate = new Date(d);
  if (shift === 'MALAM' && hour < 7) {
    refDate.setDate(refDate.getDate() - 1);
  }
  const dateStr = Utilities.formatDate(refDate, 'Asia/Makassar', 'yyyyMMdd');
  return dateStr + '-' + shift;
}

function calculateLateStatus(jamMasuk, shift) {
  if (!jamMasuk) return 'TIDAK_DIKETAHUI';
  const parts = jamMasuk.split(':');
  const hour = parseInt(parts[0]);
  const minute = parseInt(parts[1]);
  const totalMins = hour * 60 + minute;

  const startMins = { PAGI: 7 * 60, SIANG: 15 * 60, MALAM: 23 * 60 };
  const limit = (startMins[shift] || 0) + CONFIG.TOLERANCE_MINUTES;

  return totalMins > limit ? 'TERLAMBAT' : 'TEPAT';
}

function getTodayDate() {
  return Utilities.formatDate(new Date(), 'Asia/Makassar', 'yyyy-MM-dd');
}

// ============================================================
// DASHBOARD DATA
// ============================================================

function getDashboardData(tanggal) {
  try {
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = ss.getSheetByName(CONFIG.SHEET_ABSENSI);
    const reqTgl = tanggal || getTodayDate();
    if (!sheet) {
      return JSON.parse(JSON.stringify({
        tanggal: reqTgl,
        shift: getCurrentShift(),
        shiftId: generateShiftId(),
        stats: { total: 0, tepat: 0, terlambat: 0 },
        absensi: [],
        generatedAt: new Date().toISOString()
      }));
    }

    const data = sheet.getDataRange().getValues();
    if (data.length <= 1) {
      return JSON.parse(JSON.stringify({
        tanggal: reqTgl,
        shift: getCurrentShift(),
        shiftId: generateShiftId(),
        stats: { total: 0, tepat: 0, terlambat: 0 },
        absensi: [],
        generatedAt: new Date().toISOString()
      }));
    }

    const headers = data[0];

    const absensi = data.slice(1)
      .map(row => {
        const obj = {};
        headers.forEach((h, i) => {
          const val = row[i];
          if (val instanceof Date) {
            if (h === 'tanggal') {
              obj[h] = Utilities.formatDate(val, 'Asia/Makassar', 'yyyy-MM-dd');
            } else if (h === 'jamMasuk' || h === 'jamKeluar' || h === 'jam') {
              obj[h] = Utilities.formatDate(val, 'Asia/Makassar', 'HH:mm:ss');
            } else {
              obj[h] = Utilities.formatDate(val, 'Asia/Makassar', "yyyy-MM-dd'T'HH:mm:ss'Z'");
            }
          } else if (typeof val === 'number' || typeof val === 'boolean') {
            obj[h] = val;
          } else {
            obj[h] = (val === null || val === undefined) ? '' : String(val);
          }
        });

        if (obj.fotoUrl) {
          const dId = extractDriveId(String(obj.fotoUrl));
          if (dId) {
            obj.fotoUrl = 'https://lh3.googleusercontent.com/d/' + dId;
          }
        }
        return obj;
      })
      .filter(a => {
        const rowTgl = normalizeDateStr(a.tanggal);
        const filterTgl = normalizeDateStr(reqTgl);
        return rowTgl === filterTgl || !filterTgl;
      });

    const stats = {
      total: absensi.length,
      tepat: absensi.filter(a => String(a.statusKeterlambatan).trim().toUpperCase() === 'TEPAT').length,
      terlambat: absensi.filter(a => String(a.statusKeterlambatan).trim().toUpperCase() === 'TERLAMBAT').length
    };

    return JSON.parse(JSON.stringify({
      tanggal: reqTgl,
      shift: getCurrentShift(),
      shiftId: generateShiftId(),
      stats: stats,
      absensi: absensi,
      generatedAt: new Date().toISOString()
    }));
  } catch (err) {
    Logger.log('Error getDashboardData: ' + err.message);
    return JSON.parse(JSON.stringify({
      error: err.message,
      tanggal: tanggal || getTodayDate(),
      shift: getCurrentShift(),
      shiftId: generateShiftId(),
      stats: { total: 0, tepat: 0, terlambat: 0 },
      absensi: [],
      generatedAt: new Date().toISOString()
    }));
  }
}

// ============================================================
// WHATSAPP NOTIFICATIONS (FONNTE)
// ============================================================

function sendCheckInNotification(data, status) {
  try {
    const emoji = status === 'TEPAT' ? '✅' : '⚠️';
    const message = `${emoji} *ABSENSI MASUK*\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `👤 Nama   : ${data.nama}\n` +
      `🕐 Jam    : ${data.jamMasuk}\n` +
      `📅 Tanggal: ${data.tanggal}\n` +
      `🔄 Shift  : ${data.shift}\n` +
      `🆔 ID     : ${data.shiftId}\n` +
      `📊 Status : *${status === 'TEPAT' ? 'TEPAT WAKTU ✓' : 'TERLAMBAT ✗'}*\n` +
      `━━━━━━━━━━━━━━━━`;

    sendWhatsApp(message);
  } catch (err) {
    Logger.log('Error WA notif: ' + err.message);
  }
}

function sendShiftSummary(shift) {
  try {
    const tanggal = getTodayDate();
    const shiftId = generateShiftId();
    const dashData = getDashboardData(tanggal);
    const shiftAbsensi = dashData.absensi.filter(a => a.shift === shift);

    let listPetugas = '';
    shiftAbsensi.forEach((a, i) => {
      const icon = a.statusKeterlambatan === 'TEPAT' ? '✅' : '⚠️';
      listPetugas += `${i + 1}. ${icon} ${a.nama} (${a.jamMasuk})\n`;
    });

    if (listPetugas === '') listPetugas = '   (tidak ada yang absen)\n';

    const nextShift = shift === 'PAGI' ? 'SIANG' : shift === 'SIANG' ? 'MALAM' : 'PAGI';
    const message = `📋 *REKAP SHIFT ${shift}*\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `📅 Tanggal : ${tanggal}\n` +
      `🆔 Shift ID: ${shiftId}\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `👥 *Daftar Hadir:*\n${listPetugas}` +
      `━━━━━━━━━━━━━━━━\n` +
      `📊 Total   : ${shiftAbsensi.length} petugas\n` +
      `✅ Tepat   : ${shiftAbsensi.filter(a => a.statusKeterlambatan === 'TEPAT').length}\n` +
      `⚠️ Terlambat: ${shiftAbsensi.filter(a => a.statusKeterlambatan === 'TERLAMBAT').length}\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `🔄 Shift selanjutnya: *${nextShift}*\n` +
      `_Laporan otomatis SatpamPiket_`;

    sendWhatsApp(message);
    logShiftSummary(shiftId, message);
  } catch (err) {
    Logger.log('Error sendShiftSummary: ' + err.message);
  }
}

// ============================================================
// FONNTE WHATSAPP CONFIGURATION & DISPATCHER
// ============================================================

function getFonnteConfig() {
  const props = PropertiesService.getScriptProperties();
  const token = props.getProperty('FONNTE_TOKEN') || (CONFIG.FONNTE_TOKEN !== 'ISI_TOKEN_FONNTE_ANDA' ? CONFIG.FONNTE_TOKEN : '');
  const groupId = props.getProperty('WA_GROUP_ID') || (CONFIG.WA_GROUP_ID !== 'NOMOR_GRUP@g.us' ? CONFIG.WA_GROUP_ID : '');
  const isConfigured = !!(token && token !== 'ISI_TOKEN_FONNTE_ANDA' && groupId && groupId !== 'NOMOR_GRUP@g.us');

  return JSON.parse(JSON.stringify({
    success: true,
    token: token,
    tokenMasked: token ? (token.length > 8 ? token.substring(0, 4) + '••••••••' + token.substring(token.length - 4) : '••••••••') : '',
    groupId: groupId,
    isConfigured: isConfigured
  }));
}

function saveFonnteConfig(data) {
  try {
    const props = PropertiesService.getScriptProperties();
    const token = data && data.token !== undefined ? String(data.token).trim() : '';
    const groupId = data && data.groupId !== undefined ? String(data.groupId).trim() : '';

    if (token) {
      props.setProperty('FONNTE_TOKEN', token);
    }
    if (groupId) {
      props.setProperty('WA_GROUP_ID', groupId);
    }

    return JSON.parse(JSON.stringify({
      success: true,
      message: 'Pengaturan API Fonnte & ID Grup WhatsApp berhasil disimpan!',
      data: getFonnteConfig()
    }));
  } catch (err) {
    return JSON.parse(JSON.stringify({ success: false, message: 'Gagal menyimpan pengaturan Fonnte: ' + err.message }));
  }
}

function testFonnteConnection(data) {
  try {
    const props = PropertiesService.getScriptProperties();
    const token = (data && data.token && String(data.token).trim()) || props.getProperty('FONNTE_TOKEN') || (CONFIG.FONNTE_TOKEN !== 'ISI_TOKEN_FONNTE_ANDA' ? CONFIG.FONNTE_TOKEN : '');
    const groupId = (data && data.groupId && String(data.groupId).trim()) || props.getProperty('WA_GROUP_ID') || (CONFIG.WA_GROUP_ID !== 'NOMOR_GRUP@g.us' ? CONFIG.WA_GROUP_ID : '');

    if (!token || token === 'ISI_TOKEN_FONNTE_ANDA') {
      return JSON.parse(JSON.stringify({ success: false, message: 'Token Fonnte belum diisi. Masukkan token dari dashboard https://fonnte.com' }));
    }
    if (!groupId || groupId === 'NOMOR_GRUP@g.us') {
      return JSON.parse(JSON.stringify({ success: false, message: 'Target ID Grup WhatsApp belum diisi. Contoh: 120363028392131234@g.us' }));
    }

    const testMessage = `🧪 *TES KONEKSI FONNTE WHATSAPP*\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `Aplikasi : *SATPAM PIKET PLN UP3 BAUBAU*\n` +
      `Waktu    : ${Utilities.formatDate(new Date(), 'Asia/Makassar', 'dd-MM-yyyy HH:mm:ss')} WITA\n` +
      `Status   : *TERHUBUNG & AKTIF* ✅\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `Sistem WhatsApp Gateway Fonnte berhasil terhubung! Seluruh notifikasi absensi, rekap shift, dan checklist 8 lokasi siap dikirim otomatis. 🛡️⚡`;

    const payload = {
      target: groupId,
      message: testMessage,
      countryCode: '62'
    };

    const response = UrlFetchApp.fetch('https://api.fonnte.com/send', {
      method: 'POST',
      headers: { 'Authorization': token },
      payload: payload,
      muteHttpExceptions: true
    });

    const respText = response.getContentText();
    let respJson = null;
    try { respJson = JSON.parse(respText); } catch(e) {}

    if (response.getResponseCode() === 200 && respJson && respJson.status) {
      return JSON.parse(JSON.stringify({
        success: true,
        message: 'Pesan tes berhasil dikirim ke grup WhatsApp!',
        response: respJson
      }));
    } else {
      const errMsg = (respJson && (respJson.reason || respJson.message)) || respText || ('HTTP ' + response.getResponseCode());
      return JSON.parse(JSON.stringify({
        success: false,
        message: 'Fonnte merespons: ' + errMsg
      }));
    }
  } catch (err) {
    return JSON.parse(JSON.stringify({ success: false, message: 'Gagal menghubungi Fonnte: ' + err.message }));
  }
}

function sendWhatsApp(message, imageUrl) {
  try {
    const props = PropertiesService.getScriptProperties();
    const token = props.getProperty('FONNTE_TOKEN') || (CONFIG.FONNTE_TOKEN !== 'ISI_TOKEN_FONNTE_ANDA' ? CONFIG.FONNTE_TOKEN : '');
    const target = props.getProperty('WA_GROUP_ID') || (CONFIG.WA_GROUP_ID !== 'NOMOR_GRUP@g.us' ? CONFIG.WA_GROUP_ID : '');

    if (!token || token === 'ISI_TOKEN_FONNTE_ANDA') {
      Logger.log('[WA MOCK - Token Belum Diset] ' + message + (imageUrl ? ' [IMAGE: ' + imageUrl + ']' : ''));
      return { success: false, message: 'Token Fonnte belum dikonfigurasi' };
    }
    if (!target || target === 'NOMOR_GRUP@g.us') {
      Logger.log('[WA MOCK - Target Grup Belum Diset] ' + message + (imageUrl ? ' [IMAGE: ' + imageUrl + ']' : ''));
      return { success: false, message: 'ID Grup WA belum dikonfigurasi' };
    }

    const payload = {
      target: target,
      message: message,
      countryCode: '62'
    };
    if (imageUrl) {
      payload.url = imageUrl;
    }

    const options = {
      method: 'POST',
      headers: { 'Authorization': token },
      payload: payload,
      muteHttpExceptions: true
    };
    const response = UrlFetchApp.fetch('https://api.fonnte.com/send', options);
    const respCode = response.getResponseCode();
    const respText = response.getContentText();
    Logger.log('Fonnte send [' + respCode + ']: ' + respText);
    return { success: respCode === 200, response: respText };
  } catch (err) {
    Logger.log('Error sendWhatsApp: ' + err.message);
    return { success: false, error: err.message };
  }
}

/**
 * Ekstrak ID Google Drive dari URL
 */
function extractDriveId(url) {
  if (!url) return null;
  const match = url.match(/id=([a-zA-Z0-9_-]+)/);
  if (match) return match[1];
  const match2 = url.match(/\/d\/([a-zA-Z0-9_-]+)/);
  return match2 ? match2[1] : null;
}

/**
 * Buat gambar gabungan petugas lepas piket (keluar) dan masuk piket
 * menggunakan Google Slides export ke PNG, lalu simpan ke Drive.
 */
function createCombinedHandoverPhoto(fotoKeluarUrl, fotoMasukUrl, namaKeluar, namaMasuk, lokasi, labelShift) {
  try {
    let blobKeluar = null;
    let blobMasuk = null;

    if (fotoKeluarUrl) {
      try {
        const fileId = extractDriveId(fotoKeluarUrl);
        if (fileId) blobKeluar = DriveApp.getFileById(fileId).getBlob();
        else blobKeluar = UrlFetchApp.fetch(fotoKeluarUrl).getBlob();
      } catch (e) {
        Logger.log('Gagal ambil blob foto keluar: ' + e.message);
      }
    }

    if (fotoMasukUrl) {
      try {
        const fileId = extractDriveId(fotoMasukUrl);
        if (fileId) blobMasuk = DriveApp.getFileById(fileId).getBlob();
        else blobMasuk = UrlFetchApp.fetch(fotoMasukUrl).getBlob();
      } catch (e) {
        Logger.log('Gagal ambil blob foto masuk: ' + e.message);
      }
    }

    if (!blobKeluar && !blobMasuk) {
      return '';
    }

    if (blobKeluar && !blobMasuk) return fotoKeluarUrl;
    if (blobMasuk && !blobKeluar) return fotoMasukUrl;

    // Buat slide sementara untuk menggabungkan kedua foto secara berdampingan
    const pres = SlidesApp.create('Handover_' + Utilities.getUuid());
    const slide = pres.getSlides()[0];
    slide.getShapes().forEach(s => s.remove());

    // Background biru PLN profesional
    slide.getBackground().setSolidFill('#0D47A1');

    // Header kartu
    const titleBox = slide.insertTextBox('⚡ PERGANTIAN PIKET SATPAM - ' + lokasi, 20, 15, 680, 35);
    titleBox.getText().getTextStyle().setFontSize(16).setBold(true).setForegroundColor('#FFFFFF');

    const subBox = slide.insertTextBox('Pergantian: ' + labelShift + '  |  ' + Utilities.formatDate(new Date(), 'Asia/Makassar', 'dd MMM yyyy, HH:mm') + ' WITA', 20, 48, 680, 25);
    subBox.getText().getTextStyle().setFontSize(11).setForegroundColor('#BBDEFB');

    // Foto Lepas Piket (Kiri)
    slide.insertImage(blobKeluar, 40, 85, 290, 240);
    const lblKeluar = slide.insertTextBox('🔴 LEPAS PIKET:\n' + (namaKeluar || '-'), 40, 335, 290, 45);
    lblKeluar.getText().getTextStyle().setFontSize(12).setBold(true).setForegroundColor('#FFFFFF');

    // Foto Masuk Piket (Kanan)
    slide.insertImage(blobMasuk, 390, 85, 290, 240);
    const lblMasuk = slide.insertTextBox('🟢 MASUK PIKET:\n' + (namaMasuk || '-'), 390, 335, 290, 45);
    lblMasuk.getText().getTextStyle().setFontSize(12).setBold(true).setForegroundColor('#FFFFFF');

    pres.saveAndClose();

    // Export slide ke format gambar PNG menggunakan Docs export endpoint
    const presId = pres.getId();
    const exportUrl = 'https://docs.google.com/presentation/d/' + presId + '/export/png';
    const resp = UrlFetchApp.fetch(exportUrl, {
      headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken() },
      muteHttpExceptions: true
    });

    let finalImageUrl = '';
    if (resp.getResponseCode() === 200) {
      const folder = getOrCreateFolder('SatpamPiket_Handover');
      const file = folder.createFile(resp.getBlob().setName('handover_' + new Date().getTime() + '.png'));
      file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
      finalImageUrl = 'https://drive.google.com/uc?id=' + file.getId();
    }

    // Bersihkan file slide sementara
    DriveApp.getFileById(presId).setTrashed(true);
    return finalImageUrl || fotoMasukUrl || fotoKeluarUrl || '';
  } catch (err) {
    Logger.log('Error createCombinedHandoverPhoto: ' + err.message);
    return fotoMasukUrl || fotoKeluarUrl || '';
  }
}

/**
 * Memproses laporan pertukaran shift otomatis untuk satu lokasi
 */
function processShiftHandover(targetLokasi, shiftTujuan) {
  try {
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const absensiSheet = ss.getSheetByName(CONFIG.SHEET_ABSENSI);
    if (!absensiSheet) return { success: false, message: 'Sheet absensi tidak ditemukan' };

    const data = absensiSheet.getDataRange().getValues();
    if (data.length <= 1) return { success: false, message: 'Belum ada data absensi' };

    const headers = data[0];
    const rows = data.slice(1).map(r => {
      const o = {};
      headers.forEach((h, i) => o[h] = r[i]);
      return o;
    });

    const currentShift = shiftTujuan || getCurrentShift();
    let shiftAsal = 'MALAM';
    let labelGanti = 'Malam ke Pagi';
    if (currentShift === 'SIANG') {
      shiftAsal = 'PAGI';
      labelGanti = 'Pagi ke Siang/sore';
    } else if (currentShift === 'MALAM') {
      shiftAsal = 'SIANG';
      labelGanti = 'Siang ke Malam';
    } else if (currentShift === 'PAGI') {
      shiftAsal = 'MALAM';
      labelGanti = 'Malam ke Pagi';
    }

    // Filter absensi di lokasi ini
    const absensiLokasi = rows.filter(r =>
      String(r.lokasi || '').trim().toLowerCase() === String(targetLokasi || '').trim().toLowerCase()
    );

    if (absensiLokasi.length === 0) {
      return { success: false, message: 'Tidak ada data absensi untuk lokasi ' + targetLokasi };
    }

    // Cari petugas masuk piket
    const masukList = absensiLokasi.filter(r =>
      r.shift === currentShift && (r.tipeAbsen === 'MASUK' || !r.tipeAbsen)
    );
    const masukPetugas = masukList.length > 0 ? masukList[masukList.length - 1] : null;

    // Cari petugas lepas piket
    const lepasList = absensiLokasi.filter(r =>
      r.tipeAbsen === 'KELUAR' || r.shift === shiftAsal
    );
    const lepasPetugas = lepasList.length > 0 ? lepasList[lepasList.length - 1] : null;

    if (!masukPetugas && !lepasPetugas) {
      return { success: false, message: 'Tidak ada petugas piket untuk pergantian di ' + targetLokasi };
    }

    const namaMasuk = masukPetugas ? masukPetugas.nama : '-';
    const namaLepas = lepasPetugas ? lepasPetugas.nama : '-';

    // Keterangan diambil dari isian saat absen masuk maupun keluar di user berbeda
    let keteranganFinal = '';
    const ketLepas = (lepasPetugas && lepasPetugas.keterangan) ? String(lepasPetugas.keterangan).trim() : '';
    const ketMasuk = (masukPetugas && masukPetugas.keterangan) ? String(masukPetugas.keterangan).trim() : '';

    if (ketLepas && ketMasuk) {
      keteranganFinal = ketLepas + (ketLepas !== ketMasuk ? ' | ' + ketMasuk : '');
    } else if (ketLepas) {
      keteranganFinal = ketLepas;
    } else if (ketMasuk) {
      keteranganFinal = ketMasuk;
    } else {
      keteranganFinal = 'Situasi aman terkendali';
    }

    // Buat foto gabungan petugas masuk & keluar
    const fotoKeluarUrl = lepasPetugas ? (lepasPetugas.fotoUrl || '') : '';
    const fotoMasukUrl = masukPetugas ? (masukPetugas.fotoUrl || '') : '';
    const fotoGabunganUrl = createCombinedHandoverPhoto(
      fotoKeluarUrl,
      fotoMasukUrl,
      namaLepas,
      namaMasuk,
      targetLokasi,
      labelGanti
    );

    // Format pesan WA sesuai permintaan pengguna:
    const message = `🔄 *PERGANTIAN PIKET*\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `Pergantian piket : ${labelGanti}\n` +
      `Kantor PLN : ${targetLokasi}\n` +
      `Lepas piket : ${namaLepas}\n` +
      `Masuk piket : ${namaMasuk}\n` +
      `Keterangan : ${keteranganFinal}\n` +
      `━━━━━━━━━━━━━━━━\n` +
      `_Laporan otomatis SatpamPiket_`;

    sendWhatsApp(message, fotoGabunganUrl);

    // Catat ke sheet handover_log
    const handoverSheet = ss.getSheetByName(CONFIG.SHEET_HANDOVER);
    if (handoverSheet) {
      handoverSheet.appendRow([
        Utilities.getUuid(),
        getTodayDate(),
        labelGanti,
        targetLokasi,
        namaLepas,
        namaMasuk,
        keteranganFinal,
        fotoGabunganUrl,
        new Date().toISOString()
      ]);
    }

    return {
      success: true,
      message: 'Laporan pergantian piket ' + targetLokasi + ' berhasil dikirim ke grup',
      data: {
        lokasi: targetLokasi,
        labelGanti: labelGanti,
        lepasPiket: namaLepas,
        masukPiket: namaMasuk,
        keterangan: keteranganFinal,
        fotoUrl: fotoGabunganUrl
      }
    };
  } catch (err) {
    Logger.log('Error processShiftHandover: ' + err.message);
    return { success: false, message: err.message };
  }
}

/**
 * Trigger pengiriman laporan pergantian shift untuk semua lokasi terdaftar
 */
function triggerAllLocationsHandover(shift) {
  try {
    const lokasiList = getLokasiKonfig();
    lokasiList.forEach(loc => {
      processShiftHandover(loc.namaLokasi, shift);
    });
  } catch (e) {
    Logger.log('Error triggerAllLocationsHandover: ' + e.message);
  }
}

// ============================================================
// TRIGGERS (dipanggil oleh time-based trigger)
// ============================================================

function onShiftPagi() {
  sendShiftSummary('MALAM');            // Kirim rekap malam, buka shift pagi
  triggerAllLocationsHandover('PAGI');  // Kirim laporan pergantian piket semua lokasi
}

function onShiftSiang() {
  sendShiftSummary('PAGI');             // Kirim rekap pagi, buka shift siang
  triggerAllLocationsHandover('SIANG'); // Kirim laporan pergantian piket semua lokasi
}

function onShiftMalam() {
  sendShiftSummary('SIANG');            // Kirim rekap siang, buka shift malam
  triggerAllLocationsHandover('MALAM'); // Kirim laporan pergantian piket semua lokasi
}

// ============================================================
// LOKASI & GEOFENCE CONFIG FUNCTIONS
// ============================================================

/**
 * Ambil daftar konfigurasi koordinat dan radius untuk setiap lokasi
 */
function getLokasiKonfig() {
  try {
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    let sheet = ss.getSheetByName(CONFIG.SHEET_LOKASI);

    if (!sheet) {
      // Inisialisasi sheet jika belum ada
      sheet = ss.insertSheet(CONFIG.SHEET_LOKASI);
      sheet.appendRow(['namaLokasi', 'latitude', 'longitude', 'radiusMeter', 'updatedAt']);
      const defaultLokasi = [
        'Kantor UP3 Baubau',
        'Rujab UP3 Baubau',
        'Gudang UP3 Baubau',
        'ULP Baubau Kota',
        'ULP Raha',
        'ULP Pasarwajo',
        'ULP Mawasangka',
        'ULP Wangi-wangi'
      ];
      const now = new Date().toISOString();
      defaultLokasi.forEach(lok => sheet.appendRow([lok, 0.0, 0.0, 50, now]));
    }

    const data = sheet.getDataRange().getValues();
    if (data.length <= 1) return [];

    const headers = data[0];
    const res = data.slice(1).map(row => {
      const obj = {};
      headers.forEach((h, i) => {
        const val = row[i];
        if (val instanceof Date) {
          obj[h] = Utilities.formatDate(val, 'Asia/Makassar', "yyyy-MM-dd'T'HH:mm:ss'Z'");
        } else if (typeof val === 'number' || typeof val === 'boolean') {
          obj[h] = val;
        } else {
          obj[h] = (val === null || val === undefined) ? '' : String(val);
        }
      });
      obj.latitude = parseFloat(obj.latitude) || 0.0;
      obj.longitude = parseFloat(obj.longitude) || 0.0;
      obj.radiusMeter = parseInt(obj.radiusMeter, 10) || 50;
      return obj;
    });

    return JSON.parse(JSON.stringify(res));
  } catch (err) {
    Logger.log('Error getLokasiKonfig: ' + err.message);
    return [];
  }
}

/**
 * Simpan atau perbarui titik koordinat dan radius untuk suatu lokasi
 * Body: { namaLokasi, latitude, longitude, radiusMeter }
 */
function saveLokasiKonfig(data) {
  try {
    if (!data.namaLokasi) {
      return { success: false, message: 'namaLokasi harus disertakan' };
    }

    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    let sheet = ss.getSheetByName(CONFIG.SHEET_LOKASI);
    if (!sheet) {
      getLokasiKonfig(); // trigger auto-create
      sheet = ss.getSheetByName(CONFIG.SHEET_LOKASI);
    }

    const values = sheet.getDataRange().getValues();
    const namaLokasi = String(data.namaLokasi).trim();
    const latitude = parseFloat(data.latitude) || 0.0;
    const longitude = parseFloat(data.longitude) || 0.0;
    const radiusMeter = parseInt(data.radiusMeter, 10) || 50;
    const now = new Date().toISOString();

    let foundRow = -1;
    for (let i = 1; i < values.length; i++) {
      if (String(values[i][0]).trim().toLowerCase() === namaLokasi.toLowerCase()) {
        foundRow = i + 1; // 1-based index
        break;
      }
    }

    if (foundRow > 0) {
      sheet.getRange(foundRow, 2).setValue(latitude);
      sheet.getRange(foundRow, 3).setValue(longitude);
      sheet.getRange(foundRow, 4).setValue(radiusMeter);
      sheet.getRange(foundRow, 5).setValue(now);
    } else {
      sheet.appendRow([namaLokasi, latitude, longitude, radiusMeter, now]);
    }

    return {
      success: true,
      message: 'Koordinat & radius untuk ' + namaLokasi + ' berhasil diperbarui',
      data: {
        namaLokasi: namaLokasi,
        latitude: latitude,
        longitude: longitude,
        radiusMeter: radiusMeter,
        updatedAt: now
      }
    };
  } catch (err) {
    return { success: false, message: 'Error saveLokasiKonfig: ' + err.message };
  }
}

// ============================================================
// MONITORING CHECKLIST 8 LOKASI & LAPORAN RESMI KESIAPSIAGAAN
// ============================================================

/**
 * 8 Lokasi Penempatan Satpam PLN UP3 Baubau
 */
const DAFTAR_8_LOKASI = [
  { id: 1, key: 'Kantor UP3', label: 'Kantor UP3', full: 'KANTOR PLN UP3 BAUBAU', aliases: ['kantor up3', 'kantor up3 baubau', 'up3 baubau'] },
  { id: 2, key: 'Rujab UP3', label: 'Rujab UP3', full: 'RUJAB MUP3 BAUBAU', aliases: ['rujab up3', 'rujab up3 baubau', 'rujab mup3 baubau'] },
  { id: 3, key: 'Gudang UP3', label: 'Gudang UP3', full: 'GUDANG PLN UP3 BAUBAU', aliases: ['gudang up3', 'gudang up3 baubau', 'gudang'] },
  { id: 4, key: 'ULP BBK', label: 'ULP BBK', full: 'ULP BAUBAU KOTA', aliases: ['ulp bbk', 'ulp baubau kota', 'baubau kota'] },
  { id: 5, key: 'ULP Raha', label: 'ULP Raha', full: 'ULP RAHA', aliases: ['ulp raha', 'raha'] },
  { id: 6, key: 'ULP Pasarwajo', label: 'ULP Pasarwajo', full: 'ULP PASARWAJO', aliases: ['ulp pasarwajo', 'pasarwajo'] },
  { id: 7, key: 'ULP Mawasangka', label: 'ULP Mawasangka', full: 'ULP MAWASANGKA', aliases: ['ulp mawasangka', 'mawasangka'] },
  { id: 8, key: 'ULP Wangi-wangi', label: 'ULP Wangi-wangi', full: 'ULP WANGI-WANGI', aliases: ['ulp wangi-wangi', 'wangi-wangi', 'wangi wangi'] }
];

/**
 * Cocokkan nama lokasi dari absensi ke salah satu dari 8 lokasi
 */
function matchStandardLokasi(namaLok) {
  if (!namaLok) return null;
  const clean = String(namaLok).trim().toLowerCase();
  for (let i = 0; i < DAFTAR_8_LOKASI.length; i++) {
    const item = DAFTAR_8_LOKASI[i];
    if (item.aliases.some(a => clean.includes(a))) {
      return item;
    }
  }
  return null;
}

/**
 * Format jam shift untuk tampilan pesan
 */
function getShiftTimeLabel(shift) {
  if (shift === 'PAGI') return '07.00 – 15.00 WITA';
  if (shift === 'SIANG') return '15.00 – 23.00 WITA';
  if (shift === 'MALAM') return '23.00 – 07.00 WITA';
  return '15.00 – 23.00 WITA';
}

/**
 * Format hari dan tanggal bahasa Indonesia (contoh: Kamis, 10 September 2026)
 */
function getFormattedHariTanggal(dateInput) {
  let d = dateInput;
  if (typeof dateInput === 'string') {
    const parts = dateInput.split('-');
    if (parts.length === 3) {
      d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
    } else {
      d = new Date();
    }
  } else if (!d) {
    d = new Date();
  }
  const hariNames = ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu'];
  const bulanNames = [
    'Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
    'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'
  ];

  const witaStr = Utilities.formatDate(d, 'Asia/Makassar', 'yyyy-MM-dd HH:mm:ss');
  const dateParts = witaStr.split(' ')[0].split('-');
  const year = parseInt(dateParts[0], 10);
  const month = parseInt(dateParts[1], 10) - 1;
  const day = parseInt(dateParts[2], 10);
  const dayOfWeek = new Date(year, month, day).getDay();

  const dayStr = (day < 10 ? '0' : '') + day;
  return `${hariNames[dayOfWeek]}, ${dayStr} ${bulanNames[month]} ${year}`;
}

function normalizeDateStr(val) {
  if (!val) return '';
  if (val instanceof Date) {
    return Utilities.formatDate(val, 'Asia/Makassar', 'yyyy-MM-dd');
  }
  return String(val).trim();
}

/**
 * Ambil Pesan Kesiapsiagaan yang tersimpan
 */
function getPesanKesiapsiagaan() {
  const custom = PropertiesService.getScriptProperties().getProperty('PESAN_KESIAPSIAGAAN');
  if (custom && custom.trim() !== '') return custom;

  return `Tetap waspada dan fokus selama melaksanakan tugas malam. Lakukan patroli dan pemantauan secara berkala, khususnya pada area yang memiliki potensi risiko keamanan.

🔦 Lakukan patroli sesuai ketentuan.
🔐 Pastikan pintu, pagar, dan akses area dalam kondisi aman.
👀 Waspadai aktivitas atau situasi yang tidak biasa.
📞 Segera lakukan koordinasi dan pelaporan apabila terjadi gangguan.

Jangan lengah, keselamatan dan keamanan adalah tanggung jawab kita bersama.

🛡️ SIAP • DISIPLIN • WASPADA • PROFESIONAL

⚡ SATPAM PLN UP3 BAUBAU
Menjaga Keamanan, Mendukung Keandalan PLN.

SELAMAT BERTUGAS, TETAP WASPADA DAN JAGA KESELAMATAN! 🛡️🌙`;
}

/**
 * Simpan Pesan Kesiapsiagaan ke Script Properties
 */
function savePesanKesiapsiagaan(pesan) {
  try {
    PropertiesService.getScriptProperties().setProperty('PESAN_KESIAPSIAGAAN', pesan || '');
    return { success: true, message: 'Pesan kesiapsiagaan berhasil disimpan' };
  } catch (err) {
    return { success: false, message: 'Gagal menyimpan pesan: ' + err.message };
  }
}

/**
 * Ambil data checklist monitoring 8 lokasi untuk shift dan tanggal tertentu
 */
function getMonitoringData(shift, tanggal) {
  try {
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const absensiSheet = ss.getSheetByName(CONFIG.SHEET_ABSENSI);
    const tgl = tanggal || getTodayDate();
    const shf = (shift || getCurrentShift() || 'PAGI').toUpperCase();

    if (!absensiSheet) {
      const emptyItems = DAFTAR_8_LOKASI.map(loc => ({
        id: loc.id,
        key: loc.key,
        label: loc.label,
        full: loc.full,
        lokasi: loc.label,
        sudahHadir: false,
        isFilled: false,
        petugasNama: '',
        jam: '',
        tipeAbsen: '',
        keterangan: '',
        fotoUrl: '',
        masuk: null,
        keluar: null
      }));
      return {
        success: true,
        tanggal: tgl,
        shift: shf,
        shiftLabel: getShiftTimeLabel(shf),
        hariTanggalStr: getFormattedHariTanggal(tgl),
        locations: emptyItems,
        items: emptyItems,
        totalFilled: 0,
        totalHadir: 0,
        totalLocations: 8,
        totalLokasi: 8,
        isComplete: false
      };
    }

    const data = absensiSheet.getDataRange().getValues();
    const headers = (data.length > 0) ? data[0] : [];
    const absensiRows = data.slice(1).map(r => {
      const o = {};
      headers.forEach((h, i) => {
        const val = r[i];
        if (val instanceof Date) {
          if (h === 'tanggal') {
            o[h] = Utilities.formatDate(val, 'Asia/Makassar', 'yyyy-MM-dd');
          } else if (h === 'jamMasuk' || h === 'jamKeluar' || h === 'jam') {
            o[h] = Utilities.formatDate(val, 'Asia/Makassar', 'HH:mm:ss');
          } else {
            o[h] = Utilities.formatDate(val, 'Asia/Makassar', "yyyy-MM-dd'T'HH:mm:ss'Z'");
          }
        } else if (typeof val === 'number' || typeof val === 'boolean') {
          o[h] = val;
        } else {
          o[h] = (val === null || val === undefined) ? '' : String(val);
        }
      });
      if (o.fotoUrl) {
        const dId = extractDriveId(String(o.fotoUrl));
        if (dId) {
          o.fotoUrl = 'https://lh3.googleusercontent.com/d/' + dId;
        }
      }
      return o;
    }).filter(r => {
      const rowTgl = normalizeDateStr(r.tanggal);
      const rowShf = String(r.shift || '').trim().toUpperCase();
      return rowTgl === tgl && (rowShf === shf || !shf);
    });

    const locations = DAFTAR_8_LOKASI.map(loc => {
      const matches = absensiRows.filter(r => {
        const std = matchStandardLokasi(r.lokasi);
        return std && std.id === loc.id;
      });

      const masukRow = matches.filter(r => r.tipeAbsen === 'MASUK' || (!r.tipeAbsen && r.jamMasuk)).slice(-1)[0] || null;
      const keluarRow = matches.filter(r => r.tipeAbsen === 'KELUAR' || (!r.tipeAbsen && r.jamKeluar && !r.jamMasuk)).slice(-1)[0] || null;
      const latest = matches.length > 0 ? matches[matches.length - 1] : null;

      const isFilled = (masukRow !== null) || (latest !== null);

      return {
        id: loc.id,
        key: loc.key,
        label: loc.label,
        full: loc.full,
        lokasi: loc.label,
        sudahHadir: isFilled,
        isFilled: isFilled,
        petugasNama: masukRow ? masukRow.nama : (latest ? latest.nama : ''),
        jam: masukRow ? (masukRow.jamMasuk || masukRow.jam) : (latest ? (latest.jamMasuk || latest.jamKeluar || '') : ''),
        tipeAbsen: latest ? (latest.tipeAbsen || 'MASUK') : '',
        keterangan: masukRow ? (masukRow.keterangan || '') : (latest ? (latest.keterangan || '') : ''),
        fotoUrl: masukRow ? (masukRow.fotoUrl || '') : (latest ? (latest.fotoUrl || '') : ''),
        masuk: masukRow ? {
          nama: masukRow.nama || '',
          jam: masukRow.jamMasuk || masukRow.jam || '',
          keterangan: masukRow.keterangan || '',
          fotoUrl: masukRow.fotoUrl || ''
        } : null,
        keluar: keluarRow ? {
          nama: keluarRow.nama || '',
          jam: keluarRow.jamKeluar || keluarRow.jam || '',
          keterangan: keluarRow.keterangan || '',
          fotoUrl: keluarRow.fotoUrl || ''
        } : null
      };
    });

    const totalFilled = locations.filter(l => l.isFilled).length;
    const isComplete = totalFilled === 8;

    return JSON.parse(JSON.stringify({
      success: true,
      tanggal: tgl,
      shift: shf,
      shiftLabel: getShiftTimeLabel(shf),
      hariTanggalStr: getFormattedHariTanggal(tgl),
      locations: locations,
      items: locations,
      totalFilled: totalFilled,
      totalHadir: totalFilled,
      totalLocations: 8,
      totalLokasi: 8,
      isComplete: isComplete
    }));
  } catch (err) {
    Logger.log('Error getMonitoringData: ' + err.message);
    return JSON.parse(JSON.stringify({
      success: false,
      locations: [],
      items: [],
      totalFilled: 0,
      totalHadir: 0,
      totalLocations: 8,
      totalLokasi: 8,
      isComplete: false,
      error: err.message
    }));
  }
}

/**
 * Kirim checklist monitoring ke grup WhatsApp
 * Format persis seperti contoh user:
 *
 * Pergantian Shift  Hari / Tanggal : Rabu / 09 September 2026
 * Shift : 16.00 - 23.00 WITA
 *
 * 1. Kantor UP3✅
 * 2. Rujab UP3 ✅️
 * 3. Gudang UP3✅
 * 4. ULP BBK ✅
 * 5. ULP Raha ✅
 * 6. ULP Pasarwajo✅
 * 7. ULP Mawasangka✅
 * 8. ULP Wangi-wangi✅
 */
function sendMonitoringChecklistUpdate(shift, tanggal, forceSend) {
  try {
    const monitoring = getMonitoringData(shift, tanggal);
    if (!monitoring || !monitoring.locations) return { success: false, message: 'Data monitoring kosong' };

    let listText = '';
    monitoring.locations.forEach((loc, idx) => {
      const checkIcon = loc.isFilled ? '✅' : '⏳';
      const detailPetugas = loc.isFilled ? ` (${loc.petugasNama})` : '';
      listText += `${idx + 1}. ${loc.label} ${checkIcon}${detailPetugas}\n`;
    });

    const hariTgl = monitoring.hariTanggalStr.replace(', ', ' / ');
    const message = `📋 *MONITORING ABSENSI SATPAM*\n` +
      `Pergantian Shift  Hari / Tanggal : ${hariTgl}\n` +
      `Shift : ${monitoring.shiftLabel}\n\n` +
      listText + `\n` +
      `Progress: *${monitoring.totalFilled}/8* Lokasi Terisi ${monitoring.isComplete ? '✅ Lengkap!' : '⏳'}\n` +
      `_Laporan pemantauan otomatis SatpamPiket_`;

    sendWhatsApp(message);

    // Jika seluruh 8 lokasi telah lengkap (8/8), otomatis kirim laporan resmi lengkap dengan Pesan Kesiapsiagaan!
    if (monitoring.isComplete) {
      const alreadySentKey = 'FULL_REPORT_SENT_' + (tanggal || getTodayDate()) + '_' + (shift || getCurrentShift());
      const sentFlag = PropertiesService.getScriptProperties().getProperty(alreadySentKey);
      if (!sentFlag || forceSend) {
        sendLaporanLengkapShift(shift, tanggal);
        PropertiesService.getScriptProperties().setProperty(alreadySentKey, 'true');
      }
    }

    return {
      success: true,
      message: 'Checklist monitoring (' + monitoring.totalFilled + '/8) berhasil dikirim ke WA',
      monitoring: monitoring
    };
  } catch (err) {
    Logger.log('Error sendMonitoringChecklistUpdate: ' + err.message);
    return { success: false, message: err.message };
  }
}

/**
 * Kirim Laporan Lengkap Shift Resmi dengan Pesan Kesiapsiagaan
 * Format persis seperti contoh user:
 *
 * 🌙 SELAMAT BERTUGAS REKAN-REKAN SATPAM PLN UP3 BAUBAU 🌙
 *
 * 📅 Hari/Tanggal : Kamis, 10 September 2026
 * 🕐 SHIFT MALAM : 23.00 – 07.00 WITA
 *
 * Berikut daftar Petugas Satpam yang melaksanakan tugas Shift Malam:
 *
 * 🛡️ KANTOR PLN UP3 BAUBAU
 * 👤 LAODE AKSA FAISAL
 * ...
 * ━━━━━━━━━━━━━━━━━━
 * 📢 PESAN KESIAPSIAGAAN
 * ...
 */
function sendLaporanLengkapShift(shift, tanggal) {
  try {
    const monitoring = getMonitoringData(shift, tanggal);
    const shf = shift || getCurrentShift();
    const tglStr = monitoring.hariTanggalStr;
    const pesanKesiapsiagaan = getPesanKesiapsiagaan();

    let headerEmoji = '🌙';
    let shiftNamaLabel = 'SHIFT MALAM';
    if (shf === 'PAGI') {
      headerEmoji = '🌅';
      shiftNamaLabel = 'SHIFT PAGI';
    } else if (shf === 'SIANG') {
      headerEmoji = '☀️';
      shiftNamaLabel = 'SHIFT SIANG';
    }

    let daftarPetugasText = '';
    monitoring.locations.forEach(loc => {
      const nama = loc.petugasNama ? loc.petugasNama.toUpperCase() : '(BELUM ABSEN)';
      daftarPetugasText += `🛡️ ${loc.full}\n👤 ${nama}\n\n`;
    });

    const fullMessage = `${headerEmoji} *SELAMAT BERTUGAS REKAN-REKAN SATPAM PLN UP3 BAUBAU* ${headerEmoji}\n\n` +
      `📅 Hari/Tanggal : ${tglStr}\n` +
      `🕐 ${shiftNamaLabel} : ${monitoring.shiftLabel}\n\n` +
      `Berikut daftar Petugas Satpam yang melaksanakan tugas ${shiftNamaLabel}:\n\n` +
      daftarPetugasText +
      `━━━━━━━━━━━━━━━━━━\n` +
      `📢 *PESAN KESIAPSIAGAAN*\n\n` +
      pesanKesiapsiagaan;

    sendWhatsApp(fullMessage);

    return {
      success: true,
      message: 'Laporan Lengkap Shift ' + shf + ' berhasil dikirim ke grup WhatsApp!'
    };
  } catch (err) {
    Logger.log('Error sendLaporanLengkapShift: ' + err.message);
    return { success: false, message: err.message };
  }
}

// ============================================================
// HELPERS
// ============================================================

function findRowById(sheet, id) {
  const data = sheet.getDataRange().getValues();
  const searchId = String(id).trim();
  for (let i = 1; i < data.length; i++) {
    if (String(data[i][0]).trim() === searchId) return i + 1;
  }
  return -1;
}

/**
 * Memeriksa apakah petugas memiliki riwayat MASUK yang aktif (sebelum diperbolehkan Lepas Piket)
 */
function checkPetugasHasMasukRecord(sheet, petugasId, nama) {
  try {
    const data = sheet.getDataRange().getValues();
    if (!data || data.length <= 1) return false;

    const normPetugasId = String(petugasId || '').trim().toLowerCase();
    const normNama = String(nama || '').trim().toLowerCase();

    // Loop dari baris paling bawah (catatan terbaru) ke atas
    for (let i = data.length - 1; i >= 1; i--) {
      const row = data[i];
      const rowPetugasId = String(row[2] || '').trim().toLowerCase();
      const rowNama = String(row[3] || '').trim().toLowerCase();
      const rowTipe = String(row[7] || '').trim().toUpperCase();

      const isMatch = (normPetugasId && rowPetugasId === normPetugasId) ||
                      (normNama && rowNama === normNama);

      if (isMatch) {
        // Jika status terakhir adalah MASUK, berarti petugas boleh Lepas Piket
        return rowTipe === 'MASUK';
      }
    }
    // Belum pernah ada riwayat absensi sama sekali
    return false;
  } catch (err) {
    Logger.log('Error checkPetugasHasMasukRecord: ' + err.message);
    return true; // Jika terjadi error sistem sheet, toleransi agar tidak memblokir darurat
  }
}

function getOrCreateFolder(name) {
  const folders = DriveApp.getFoldersByName(name);
  return folders.hasNext() ? folders.next() : DriveApp.createFolder(name);
}

function logShiftSummary(shiftId, message) {
  const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
  const sheet = ss.getSheetByName(CONFIG.SHEET_SHIFT_LOG);
  if (sheet) sheet.appendRow([shiftId, message, new Date().toISOString()]);
}

function jsonResponse(obj) {
  if (obj && typeof obj.getContent === 'function') {
    return obj;
  }
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

// ============================================================
// APP VERSION & AUTO-UPDATE FUNCTIONS
// ============================================================

function getLatestAppVersion() {
  const props = PropertiesService.getScriptProperties();
  const verCode = parseInt(props.getProperty('LATEST_VERSION_CODE') || '1', 10);
  const verName = props.getProperty('LATEST_VERSION_NAME') || '1.0';
  const downloadUrl = props.getProperty('LATEST_DOWNLOAD_URL') || '';
  const releaseNotes = props.getProperty('LATEST_RELEASE_NOTES') || '';
  const forceUpdate = props.getProperty('LATEST_FORCE_UPDATE') === 'true';
  const releasedAt = props.getProperty('LATEST_RELEASED_AT') || '';
  const fileName = props.getProperty('LATEST_FILE_NAME') || 'satpam_piket.apk';
  const fileSize = props.getProperty('LATEST_FILE_SIZE') || '';
  const apiUrl = props.getProperty('ACTIVE_API_URL') || 'https://script.google.com/macros/s/AKfycbxTIZvMf3DGQM5ecrohFobHNhCGwvZGUNOg-av19T4/exec';

  return JSON.parse(JSON.stringify({
    success: true,
    versionCode: verCode,
    versionName: verName,
    downloadUrl: downloadUrl,
    releaseNotes: releaseNotes,
    forceUpdate: forceUpdate,
    releasedAt: releasedAt,
    fileName: fileName,
    fileSize: fileSize,
    apiUrl: apiUrl
  }));
}

function formatGoogleDriveDownloadUrl(url) {
  if (!url) return '';
  let fileId = '';
  const match1 = url.match(/\/file\/d\/([a-zA-Z0-9_-]+)/);
  if (match1 && match1[1]) {
    fileId = match1[1];
  } else {
    const match2 = url.match(/[?&]id=([a-zA-Z0-9_-]+)/);
    if (match2 && match2[1]) {
      fileId = match2[1];
    }
  }
  if (fileId) {
    return 'https://drive.google.com/uc?export=download&id=' + fileId;
  }
  return url;
}

function publishAppVersion(data) {
  try {
    const props = PropertiesService.getScriptProperties();
    const verCode = parseInt(data.versionCode || '1', 10);
    const verName = String(data.versionName || '1.0').trim();
    let downloadUrl = String(data.downloadUrl || '').trim();
    const releaseNotes = String(data.releaseNotes || '').trim();
    const forceUpdate = data.forceUpdate === true || data.forceUpdate === 'true';
    const fileName = String(data.fileName || 'satpam_piket_v' + verName + '.apk').trim();
    const fileSize = String(data.fileSize || '').trim();
    const releasedAt = Utilities.formatDate(new Date(), 'Asia/Makassar', 'yyyy-MM-dd HH:mm:ss');

    if (!downloadUrl) {
      return { success: false, message: 'Link download APK (Google Drive) tidak boleh kosong.' };
    }

    if (downloadUrl.indexOf('drive.google.com') !== -1) {
      downloadUrl = formatGoogleDriveDownloadUrl(downloadUrl);
    }

    props.setProperties({
      'LATEST_VERSION_CODE': String(verCode),
      'LATEST_VERSION_NAME': verName,
      'LATEST_DOWNLOAD_URL': downloadUrl,
      'LATEST_RELEASE_NOTES': releaseNotes,
      'LATEST_FORCE_UPDATE': String(forceUpdate),
      'LATEST_RELEASED_AT': releasedAt,
      'LATEST_FILE_NAME': fileName,
      'LATEST_FILE_SIZE': fileSize
    });

    if (data.apiUrl && String(data.apiUrl).trim()) {
      props.setProperty('ACTIVE_API_URL', String(data.apiUrl).trim());
    }

    // Catat ke sheet history app_version
    try {
      const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
      let sheet = ss.getSheetByName(CONFIG.SHEET_APP_VERSION);
      if (!sheet) {
        sheet = ss.insertSheet(CONFIG.SHEET_APP_VERSION);
        sheet.appendRow(['Timestamp', 'Version Code', 'Version Name', 'File Name', 'Download URL', 'Release Notes', 'Force Update']);
      }
      sheet.appendRow([releasedAt, verCode, verName, fileName, downloadUrl, releaseNotes, forceUpdate ? 'YA' : 'TIDAK']);
    } catch (err) {
      Logger.log('Gagal mencatat history ke sheet app_version: ' + err.message);
    }

    // Broadcast ke Grup WhatsApp jika opsi dicentang
    if (data.notifyWA === true || data.notifyWA === 'true') {
      sendWAAppUpdateBroadcast(verName, verCode, downloadUrl, releaseNotes, forceUpdate);
    }

    return {
      success: true,
      message: 'Pembaruan aplikasi versi ' + verName + ' (Build ' + verCode + ') berhasil dipublikasikan!',
      data: getLatestAppVersion()
    };
  } catch (err) {
    Logger.log('Error publishAppVersion: ' + err.message);
    return { success: false, message: 'Gagal mempublikasikan versi: ' + err.message };
  }
}

function uploadApkToDrive(data) {
  try {
    if (!data.fileBase64) {
      return { success: false, message: 'Data file APK base64 tidak ditemukan.' };
    }

    const decoded = Utilities.base64Decode(data.fileBase64);
    const fileName = data.fileName || ('satpam_piket_v' + (data.versionName || '1.0') + '.apk');
    const blob = Utilities.newBlob(decoded, 'application/vnd.android.package-archive', fileName);

    const folder = getOrCreateFolder('SatpamPiket_APKs');
    const file = folder.createFile(blob);
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);

    const downloadUrl = 'https://drive.google.com/uc?export=download&id=' + file.getId();
    const fileSizeStr = (blob.getBytes().length / (1024 * 1024)).toFixed(2) + ' MB';

    return publishAppVersion({
      versionCode: data.versionCode,
      versionName: data.versionName,
      downloadUrl: downloadUrl,
      releaseNotes: data.releaseNotes,
      forceUpdate: data.forceUpdate,
      fileName: fileName,
      fileSize: fileSizeStr,
      notifyWA: data.notifyWA
    });
  } catch (err) {
    Logger.log('Error uploadApkToDrive: ' + err.message);
    return { success: false, message: 'Gagal mengunggah file APK ke Google Drive: ' + err.message };
  }
}

function sendWAAppUpdateBroadcast(versionName, versionCode, downloadUrl, releaseNotes, forceUpdate) {
  try {
    let msg = "🚀 *PEMBARUAN APLIKASI SATPAM PIKET TERSEDIA!*\n\n";
    msg += "Halo Rekan-Rekan Satpam PLN UP3 Baubau,\n";
    msg += "Telah dirilis versi terbaru aplikasi absensi satpam:\n\n";
    msg += "📱 *Versi:* " + versionName + " (Build " + versionCode + ")\n";
    if (forceUpdate) {
      msg += "⚠️ *Sifat:* WAJIB UPDATE (Versi lama dinonaktifkan)\n";
    } else {
      msg += "ℹ️ *Sifat:* Pembaruan Disarankan\n";
    }
    msg += "📝 *Catatan Pembaruan:*\n" + (releaseNotes ? releaseNotes : "- Peningkatan fitur & kestabilan sistem") + "\n\n";
    msg += "📥 *Download APK Terbaru (Google Drive):*\n" + downloadUrl + "\n\n";
    msg += "Silakan unduh dan install file APK di atas agar absensi dan monitoring piket berjalan lancar. Terima kasih! 🛡️⚡";

    return sendWhatsApp(msg);
  } catch (err) {
    Logger.log('Error sendWAAppUpdateBroadcast: ' + err.message);
    return { success: false, message: err.message };
  }
}

function getAppVersionHistory() {
  try {
    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = ss.getSheetByName(CONFIG.SHEET_APP_VERSION);
    if (!sheet) return [];
    const values = sheet.getDataRange().getValues();
    if (values.length <= 1) return [];

    const list = [];
    for (let i = 1; i < values.length; i++) {
      let ts = values[i][0];
      if (ts instanceof Date) {
        ts = Utilities.formatDate(ts, 'Asia/Makassar', 'yyyy-MM-dd HH:mm:ss');
      } else {
        ts = ts ? String(ts) : '';
      }
      list.push({
        timestamp: ts,
        versionCode: parseInt(values[i][1], 10) || 0,
        versionName: String(values[i][2] || ''),
        fileName: String(values[i][3] || ''),
        downloadUrl: String(values[i][4] || ''),
        releaseNotes: String(values[i][5] || ''),
        forceUpdate: String(values[i][6]) === 'YA'
      });
    }
    return JSON.parse(JSON.stringify(list.reverse()));
  } catch (err) {
    Logger.log('Error getAppVersionHistory: ' + err.message);
    return [];
  }
}

// ============================================================
// MANAJEMEN URL WEB APP (KONEKSI ANDROID & DASHBOARD)
// ============================================================

function getActiveApiUrl() {
  const props = PropertiesService.getScriptProperties();
  let url = props.getProperty('ACTIVE_API_URL');
  if (!url || !url.trim()) {
    try {
      const serviceUrl = ScriptApp.getService().getUrl();
      if (serviceUrl && serviceUrl.indexOf('script.google.com') !== -1) {
        url = serviceUrl;
      }
    } catch (e) {}
  }
  if (!url || !url.trim() || url.indexOf('script.google.com') === -1) {
    url = 'https://script.google.com/macros/s/AKfycbxTIZvMf3DGQM5ecrohFobHNhCGwvZGUNOg-av19T4/exec';
  }
  let clean = String(url).trim();
  if (clean.endsWith('/dev')) clean = clean.substring(0, clean.length - 4) + '/exec';
  if (!clean.endsWith('/exec')) clean = clean.endsWith('/') ? clean + 'exec' : clean + '/exec';
  return clean;
}

function saveActiveApiUrl(url) {
  try {
    if (!url || !String(url).trim()) {
      return { success: false, message: 'URL tidak boleh kosong.' };
    }
    let cleanUrl = String(url).trim();
    if (cleanUrl.endsWith('/dev')) {
      cleanUrl = cleanUrl.substring(0, cleanUrl.length - 4) + '/exec';
    }
    if (!cleanUrl.endsWith('/exec')) {
      if (cleanUrl.endsWith('/')) {
        cleanUrl = cleanUrl + 'exec';
      } else {
        cleanUrl = cleanUrl + '/exec';
      }
    }
    const props = PropertiesService.getScriptProperties();
    props.setProperty('ACTIVE_API_URL', cleanUrl);
    return {
      success: true,
      message: 'URL Web App berhasil disimpan dan otomatis disinkronkan ke seluruh HP petugas!',
      apiUrl: cleanUrl
    };
  } catch (err) {
    return { success: false, message: 'Gagal menyimpan URL: ' + err.message };
  }
}

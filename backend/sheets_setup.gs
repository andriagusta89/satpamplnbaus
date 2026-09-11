// ============================================================
// SETUP SCRIPT - Jalankan SEKALI untuk inisialisasi
// ============================================================

function setupSheets() {
  const ss = SpreadsheetApp.openById('1yE3Tw0pv2-VpVZCBsQ7suO5s0X4sn3XzfXMlADizU14');

  // 1. Sheet: absensi
  createOrClearSheet(ss, 'absensi', [
    'id', 'shiftId', 'petugasId', 'nama', 'lokasi', 'tanggal', 'shift', 'tipeAbsen',
    'jamMasuk', 'jamKeluar', 'durasi', 'statusKeterlambatan', 'keterangan',
    'fotoUrl', 'latitude', 'longitude', 'createdAt'
  ]);

  // 2. Sheet: petugas (hanya yang sudah di-approve)
  createOrClearSheet(ss, 'petugas', [
    'id', 'nama', 'jabatan', 'lokasi', 'fotoReferensi', 'aktif', 'createdAt', 'approvedAt', 'approvedBy'
  ]);

  // 3. Sheet: shift_log
  createOrClearSheet(ss, 'shift_log', [
    'shiftId', 'ringkasan', 'createdAt'
  ]);

  // 4. Sheet: pending_petugas (menunggu approval admin)
  createOrClearSheet(ss, 'pending_petugas', [
    'id', 'nama', 'jabatan', 'lokasi', 'fotoBase64', 'fotoUrl', 'status',
    'submittedAt', 'reviewedAt', 'reviewNote'
  ]);

  // 5. Sheet: lokasi_konfig (konfigurasi titik koordinat GPS dan radius per lokasi)
  const lokasiSheet = createOrClearSheet(ss, 'lokasi_konfig', [
    'namaLokasi', 'latitude', 'longitude', 'radiusMeter', 'updatedAt'
  ]);

  // 6. Sheet: handover_log (rekap foto dan keterangan pergantian shift)
  createOrClearSheet(ss, 'handover_log', [
    'id', 'tanggal', 'shiftGanti', 'lokasi', 'lepasPiket', 'masukPiket', 'keterangan', 'fotoUrl', 'createdAt'
  ]);

  // 7. Sheet: app_version (catatan rilis dan update APK)
  createOrClearSheet(ss, 'app_version', [
    'Timestamp', 'Version Code', 'Version Name', 'File Name', 'Download URL', 'Release Notes', 'Force Update'
  ]);

  const daftarLokasi = [
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
  daftarLokasi.forEach(lokasi => {
    lokasiSheet.appendRow([lokasi, 0.0, 0.0, 50, now]);
  });

  // 6. Tambah data contoh petugas (sudah approved)
  const petugasSheet = ss.getSheetByName('petugas');
  petugasSheet.appendRow([
    'PAM-2026001', 'Budi Santoso', 'Komandan Regu', 'Kantor UP3 Baubau', '', true,
    new Date().toISOString(), new Date().toISOString(), 'admin'
  ]);
  petugasSheet.appendRow([
    'PAM-2026002', 'Agus Wijaya', 'Anggota', 'Kantor UP3 Baubau', '', true,
    new Date().toISOString(), new Date().toISOString(), 'admin'
  ]);

  // 7. Format header
  formatAllSheets(ss);

  Logger.log('✅ Setup selesai! Semua sheet berhasil dibuat.');
  SpreadsheetApp.getUi().alert('Setup berhasil! Sheet absensi, petugas, shift_log, pending_petugas, dan lokasi_konfig telah dibuat.');
}

function createOrClearSheet(ss, name, headers) {
  let sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
  } else {
    sheet.clearContents();
  }
  sheet.appendRow(headers);
  return sheet;
}

function formatAllSheets(ss) {
  ['absensi', 'petugas', 'shift_log', 'pending_petugas', 'lokasi_konfig', 'handover_log'].forEach(name => {
    const sheet = ss.getSheetByName(name);
    if (!sheet) return;
    const headerRange = sheet.getRange(1, 1, 1, sheet.getLastColumn());
    headerRange.setBackground('#1565C0');
    headerRange.setFontColor('#FFFFFF');
    headerRange.setFontWeight('bold');
    sheet.setFrozenRows(1);
    sheet.autoResizeColumns(1, sheet.getLastColumn());
  });
}


// ============================================================
// SETUP TRIGGERS - Jalankan SEKALI untuk set jam notifikasi
// ============================================================

function setupTriggers() {
  // Hapus trigger lama
  ScriptApp.getProjectTriggers().forEach(t => ScriptApp.deleteTrigger(t));

  // Trigger jam 07:00 (awal shift pagi)
  ScriptApp.newTrigger('onShiftPagi')
    .timeBased()
    .atHour(7)
    .everyDays(1)
    .inTimezone('Asia/Makassar')
    .create();

  // Trigger jam 15:00 (awal shift siang)
  ScriptApp.newTrigger('onShiftSiang')
    .timeBased()
    .atHour(15)
    .everyDays(1)
    .inTimezone('Asia/Makassar')
    .create();

  // Trigger jam 23:00 (awal shift malam)
  ScriptApp.newTrigger('onShiftMalam')
    .timeBased()
    .atHour(23)
    .everyDays(1)
    .inTimezone('Asia/Makassar')
    .create();

  Logger.log('✅ Triggers berhasil dibuat untuk jam 07:00, 15:00, dan 23:00 WIB');
  SpreadsheetApp.getUi().alert('Triggers berhasil! Notifikasi WA akan terkirim otomatis jam 07:00, 15:00, dan 23:00 WIB.');
}

// Test kirim WA manual
function testWhatsApp() {
  sendWhatsApp('🧪 *TEST NOTIFIKASI*\nSatpamPiket terhubung ke WhatsApp!\nWaktu: ' + new Date().toLocaleString('id-ID'));
  Logger.log('Test WA terkirim');
}

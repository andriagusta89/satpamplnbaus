# 📱 SATPAM PIKET — Panduan Setup Lengkap

## Struktur Project

```
satpam_kantor/
├── android_app/          ← Project Android (buka di Android Studio)
│   └── app/src/main/java/com/satpam/piket/
│       ├── data/         ← Room DB, Network, Models
│       ├── domain/       ← ShiftManager, FaceRecognitionManager
│       ├── di/           ← Hilt Dependency Injection
│       ├── ui/screen/    ← Compose Screens
│       └── ui/viewmodel/ ← ViewModels
└── backend/              ← Google Apps Script
    ├── Code.gs           ← Backend utama (API + WA)
    ├── sheets_setup.gs   ← Setup database (jalankan sekali)
    └── Dashboard.html    ← Web dashboard
```

---

## LANGKAH 1 — Setup Google Apps Script (Backend)

### 1.1 Buka Google Apps Script
1. Buka [script.google.com](https://script.google.com)
2. Klik **New Project**
3. Beri nama: `SatpamPiket Backend`

### 1.2 Copy file backend
1. Hapus isi `Code.gs` default
2. Copy-paste isi file `backend/Code.gs` ke editor
3. Klik **+** (New File) → pilih **HTML** → nama: `Dashboard`
4. Copy-paste isi `backend/Dashboard.html`
5. Klik **+** lagi → pilih **Script** → nama: `sheets_setup`
6. Copy-paste isi `backend/sheets_setup.gs`

### 1.3 Setup Spreadsheet
Spreadsheet ID Anda: `1yE3Tw0pv2-VpVZCBsQ7suO5s0X4sn3XzfXMlADizU14`
(sudah dikonfigurasi di Code.gs)

Jalankan setup:
1. Di Apps Script, pilih fungsi `setupSheets`
2. Klik **▶ Run**
3. Izinkan akses Google Sheets saat diminta
4. Tunggu hingga muncul alert "Setup berhasil!"

### 1.4 Setup Triggers WA
1. Pilih fungsi `setupTriggers`
2. Klik **▶ Run**
3. Muncul alert konfirmasi triggers jam 07:00, 15:00, 23:00

### 1.5 Deploy sebagai Web App
1. Klik **Deploy → New Deployment**
2. Pilih type: **Web app**
3. Execute as: **Me**
4. Who has access: **Anyone** (agar Android bisa akses)
5. Klik **Deploy**
6. **Salin URL Web App** — ini yang dipakai di Android!

URL format: `https://script.google.com/macros/s/XXXXXXX/exec`

---

## LANGKAH 2 — Konfigurasi Fonnte WhatsApp

### 2.1 Daftar Fonnte
1. Buka [fonnte.com](https://fonnte.com)
2. Daftar akun gratis
3. Hubungkan nomor WhatsApp (scan QR)
4. Salin **API Token** dari dashboard

### 2.2 Dapatkan ID Grup WhatsApp
1. Di Fonnte dashboard → menu **Contacts**
2. Pilih grup yang dituju
3. Salin nomor grup (format: `628xxx...@g.us`)

### 2.3 Isi konfigurasi di Code.gs
```javascript
const CONFIG = {
  SPREADSHEET_ID: '1yE3Tw0pv2-VpVZCBsQ7suO5s0X4sn3XzfXMlADizU14',
  FONNTE_TOKEN: 'TOKEN_ANDA_DARI_FONNTE',   // ← Ganti ini
  WA_GROUP_ID: '628xxxx@g.us',              // ← Ganti ini
  ...
};
```

### 2.4 Test koneksi WA
1. Pilih fungsi `testWhatsApp`
2. Klik **▶ Run**
3. Cek grup WhatsApp — harus muncul pesan test

---

## LANGKAH 3 — Setup Android App

### 3.1 Buka di Android Studio
1. Buka Android Studio
2. **File → Open**
3. Pilih folder: `C:\xampp\htdocs\satpam_kantor\android_app`
4. Tunggu Gradle sync selesai (bisa 3-5 menit pertama kali)

### 3.2 Isi URL API
Setelah dapat URL dari step 1.5:
1. Build & install APK ke HP
2. Buka app → **Pengaturan** (ikon gear)
3. Isi **URL Google Apps Script** dengan URL dari step 1.5
4. Klik **Simpan URL**

### 3.3 Daftarkan Petugas
1. Di Pengaturan → **Daftarkan Petugas Baru**
2. Isi nama dan jabatan
3. Petugas terdaftar di database lokal
4. (Untuk foto referensi: fitur capture foto referensi ada di FaceLoginScreen)

### 3.4 Test Absensi
1. Kembali ke Dashboard
2. Klik **ABSEN SEKARANG**
3. Arahkan wajah ke kamera depan
4. Konfirmasi saat wajah dikenali

---

## LANGKAH 4 — Build APK untuk Distribusi

```bash
# Di Android Studio:
Build → Generate Signed Bundle/APK → APK
```

Atau via Gradle:
```bash
cd android_app
./gradlew assembleRelease
```

APK output: `android_app/app/build/outputs/apk/release/app-release.apk`

---

## Alur Data Lengkap

```
HP Satpam               Google Apps Script         WhatsApp Grup
──────────              ──────────────────         ─────────────
Scan wajah ──────────►  POST /exec?action=          Notif masuk:
ML Kit detect          submitAbsensi               "✅ Budi masuk
Cocokkan dengan    ──►  Simpan ke Sheets       ──►  07:05 TEPAT"
foto referensi          Upload foto Drive
Simpan lokal            ─────────────────
WorkManager sync        Jam 07/15/23:00        ──►  Rekap shift
saat ada internet  ──►  Kirim rekap otomatis       otomatis
```

---

## Troubleshooting

| Masalah | Solusi |
|---|---|
| Gradle sync error | Pastikan internet aktif, klik File → Invalidate Caches |
| Kamera tidak terbuka | Cek permission CAMERA di Settings HP |
| Wajah tidak dikenali | Daftarkan ulang foto referensi di cahaya yang baik |
| Sync gagal | Cek URL API di Pengaturan sudah benar |
| WA tidak terkirim | Cek token Fonnte di Code.gs sudah diisi |

---

## Kontak & Support
Project: SatpamPiket v1.0  
Spreadsheet ID: `1yE3Tw0pv2-VpVZCBsQ7suO5s0X4sn3XzfXMlADizU14`

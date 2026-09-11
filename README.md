# Dashboard & Sistem Presensi Piket Satpam PLN Baubau

Aplikasi Presensi Piket Satpam Terintegrasi berbasis **Android (Pengenalan Wajah & Geofence GPS)**, **Web Dashboard (GitHub Pages)**, dan **Backend Serverless (Google Apps Script & Google Sheets)**.

---

## 🌐 Akses Web Dashboard

* **Hosting Dashboard (GitHub Pages):**
  👉 **[https://andriagusta89.github.io/satpamplnbaus/](https://andriagusta89.github.io/satpamplnbaus/)**
* **Backend API (Google Apps Script):**
  https://script.google.com/macros/s/AKfycbz8UDNsWTN589ErtvETaaI3o9A3fRzQZfw460XBRCpwq4x2xKjbGtn7GWg-syQnbWiJ/exec

---

## 🏗️ Arsitektur Sistem

`
       +-------------------------------------------------------------+
       |                  TAMPILAN WEB (FRONTEND)                    |
       |                Hosted on GitHub Pages                       |
       |         https://andriagusta89.github.io/satpamplnbaus/      |
       +------------------------------+------------------------------+
                                      |
                           HTTPS REST API (AJAX / Fetch)
                                      |
       +------------------------------v------------------------------+
       |               LOGIKA & BACKEND (JAVASCRIPT)                 |
       |                 Google Apps Script (Code.gs)                |
       +------------------+-----------------------+------------------+
                          |                       |
                 Google Sheets Database      Google Drive
                   (Data & Presensi)      (Foto Presensi/Enrol)
                          ^                       ^
                          |                       |
                   HTTPS REST API (Retrofit / OkHttp)
                          |                       |
       +------------------+-----------------------+------------------+
       |                     APLIKASI ANDROID                        |
       |           Face Recognition + Geofence GPS (Offline)         |
       +-------------------------------------------------------------+
`

---

## 📋 Fitur Utama

1. **Dashboard Utama:**
   - Rekap absensi shift aktif (Pagi, Siang, Malam).
   - Indikator status kehadiran (Tepat / Terlambat).
   - Pratinjau foto petugas saat absen masuk langsung dari Google Drive.
2. **Pusat Menu & Pengaturan:**
   - **Persetujuan Petugas Baru:** Tinjau enrol wajah pendaftar baru beserta foto selfie referensi wajah.
   - **Monitoring 8 Titik Pos:** Rekap kesiapsiagaan pos jaga di Baubau & kirim laporan ke WhatsApp grup via Fonnte.
   - **Setting Titik Lokasi & Radius GPS:** Atur koordinat kantor dan batas radius kehadiran satpam.
   - **WhatsApp Gateway (Fonnte):** Integrasi kirim pesan notifikasi otomatis shift dan laporan presensi.
   - **Pusat Update APK:** Distribusi versi aplikasi Android terbaru langsung ke HP petugas.

---

## 🚀 Cara Mengaktifkan GitHub Pages

1. Masuk ke repositori Anda di GitHub: **[andriagusta89/satpamplnbaus](https://github.com/andriagusta89/satpamplnbaus)**.
2. Klik menu **Settings** > pilih **Pages** di sidebar kiri.
3. Di bagian **Build and deployment** > **Source**, pilih **Deploy from a branch**.
4. Di bagian **Branch**, pilih **main** dan folder **/ (root)**, lalu klik **Save**.
5. Tunggu sekitar 1–2 menit, web dashboard Anda akan aktif di:
   **https://andriagusta89.github.io/satpamplnbaus/**

---

*Dikelola oleh Tim Satpam PLN UP3 Baubau - © 2026*

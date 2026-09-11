package com.satpam.piket.domain

import com.satpam.piket.data.model.Shift
import com.satpam.piket.data.model.StatusKeterlambatan
import java.text.SimpleDateFormat
import java.util.*

object ShiftManager {

    /**
     * Tentukan shift berdasarkan jam sekarang:
     * Pagi  : 07:00 - 14:59
     * Siang : 15:00 - 22:59
     * Malam : 23:00 - 06:59
     */
    fun getCurrentShift(timeMillis: Long = System.currentTimeMillis()): Shift {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        return when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 7..14 -> Shift.PAGI
            in 15..22 -> Shift.SIANG
            else -> Shift.MALAM
        }
    }

    /**
     * Generate Shift ID: YYYYMMDD-PAGI/SIANG/MALAM
     * Untuk shift MALAM yang mulai jam 23:00, tanggal mengikuti hari saat jam 23:00.
     * Jika jam 00-06, tanggal mundur 1 hari (masih shift malam hari sebelumnya).
     */
    fun generateShiftId(timeMillis: Long = System.currentTimeMillis()): String {
        val shift = getCurrentShift(timeMillis)
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }

        // Jam 00-06 = masih shift malam hari sebelumnya
        if (shift == Shift.MALAM && cal.get(Calendar.HOUR_OF_DAY) < 7) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return "${sdf.format(cal.time)}-${shift.name}"
    }

    /**
     * Tentukan status keterlambatan dengan toleransi 15 menit
     */
    fun calculateLateStatus(checkInMillis: Long, shift: Shift): StatusKeterlambatan {
        val cal = Calendar.getInstance().apply { timeInMillis = checkInMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val totalMins = hour * 60 + minute

        val shiftStartMins = when (shift) {
            Shift.PAGI -> 7 * 60      // 07:00 = 420 menit
            Shift.SIANG -> 15 * 60    // 15:00 = 900 menit
            Shift.MALAM -> 23 * 60    // 23:00 = 1380 menit
        }
        val toleranceMins = shiftStartMins + 15

        return if (totalMins > toleranceMins) StatusKeterlambatan.TERLAMBAT
        else StatusKeterlambatan.TEPAT
    }

    /** Hitung durasi dalam menit antara masuk dan keluar */
    fun calculateDuration(masukMillis: Long, keluarMillis: Long): Int {
        return ((keluarMillis - masukMillis) / 1000 / 60).toInt()
    }

    /** Jam mulai shift */
    fun getShiftStartTime(shift: Shift): String = when (shift) {
        Shift.PAGI -> "07:00"
        Shift.SIANG -> "15:00"
        Shift.MALAM -> "23:00"
    }

    /** Jam selesai shift */
    fun getShiftEndTime(shift: Shift): String = when (shift) {
        Shift.PAGI -> "15:00"
        Shift.SIANG -> "23:00"
        Shift.MALAM -> "07:00"
    }

    /** Nama shift dalam Bahasa Indonesia */
    fun getShiftLabel(shift: Shift): String = when (shift) {
        Shift.PAGI -> "Shift Pagi (07:00 - 15:00)"
        Shift.SIANG -> "Shift Siang (15:00 - 23:00)"
        Shift.MALAM -> "Shift Malam (23:00 - 07:00)"
    }

    fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getCurrentTimeString(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun formatDuration(menit: Int): String {
        val jam = menit / 60
        val min = menit % 60
        return if (jam > 0) "${jam}j ${min}m" else "${min} menit"
    }
}

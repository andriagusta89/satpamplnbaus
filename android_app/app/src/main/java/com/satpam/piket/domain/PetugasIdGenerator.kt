package com.satpam.piket.domain

import com.satpam.piket.data.model.Petugas
import java.util.Calendar

object PetugasIdGenerator {
    /**
     * Menghasilkan ID Petugas resmi berikutnya dengan format:
     * PAM-YYYY001, PAM-YYYY002, dan seterusnya (contoh: PAM-2026001).
     */
    fun generateNextId(existingPetugas: List<Petugas>): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val prefix = "PAM-$year"
        var maxSeq = 0

        for (p in existingPetugas) {
            val id = p.id.trim()
            if (id.startsWith(prefix)) {
                val numPart = id.removePrefix(prefix).toIntOrNull()
                if (numPart != null && numPart > maxSeq) {
                    maxSeq = numPart
                }
            }
        }

        val nextSeq = maxSeq + 1
        return "$prefix%03d".format(nextSeq)
    }
}

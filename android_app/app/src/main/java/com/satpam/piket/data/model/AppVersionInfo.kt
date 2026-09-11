package com.satpam.piket.data.model

import com.google.gson.annotations.SerializedName

data class AppVersionInfo(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("versionCode") val versionCode: Int = 1,
    @SerializedName("versionName") val versionName: String = "1.0",
    @SerializedName("downloadUrl") val downloadUrl: String = "",
    @SerializedName("releaseNotes") val releaseNotes: String = "",
    @SerializedName("forceUpdate") val forceUpdate: Boolean = false,
    @SerializedName("releasedAt") val releasedAt: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("fileSize") val fileSize: String? = null,
    @SerializedName("apiUrl") val apiUrl: String? = null
)

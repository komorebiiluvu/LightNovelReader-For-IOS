package indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupFileHeader(
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("app_ver_code")
    val appVersionCode: Int? = null,
    @SerialName("app_ver_name")
    val appVersionName: String? = null,
    val compression: BackupCompression = BackupCompression.NONE
)

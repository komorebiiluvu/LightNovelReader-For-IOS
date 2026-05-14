package indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark

import kotlinx.serialization.Serializable

@Serializable
data class BackupTableHeader(
    val webBookDataSourceId: Int,
    val tableId: Int
)
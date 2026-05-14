package indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark

import kotlinx.serialization.Serializable

@Serializable
enum class BackupCompression {
    NONE,
    GZIP
}
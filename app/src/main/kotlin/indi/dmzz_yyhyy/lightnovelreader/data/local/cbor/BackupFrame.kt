package indi.dmzz_yyhyy.lightnovelreader.data.local.cbor

internal class BackupFrame(
    val type: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BackupFrame

        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

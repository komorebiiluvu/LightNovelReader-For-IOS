package indi.dmzz_yyhyy.lightnovelreader.data.local.cbor

internal object FrameType {
    const val FILE_HEADER = 1
    const val TABLE_HEADER = 2
    const val ENTITY_BATCH = 3
    const val TABLE_END = 250
    const val FILE_END = 255
}
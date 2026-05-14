package indi.dmzz_yyhyy.lightnovelreader.data.local.cbor

import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark.BackupTableHeader
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

internal fun readExactly(input: InputStream, size: Int): ByteArray {
    val buffer = ByteArray(size)
    var offset = 0

    while (offset < size) {
        val count = input.read(buffer, offset, size - offset)
        if (count == -1) {
            throw EOFException("Unexpected EOF")
        }
        offset += count
    }

    return buffer
}

internal fun readInt(input: InputStream): Int {
    val bytes = readExactly(input, 4)

    return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
}

internal fun readFrame(input: InputStream): BackupFrame {
    val type = input.read()
    if (type == -1) {
        throw EOFException("Unexpected end of backup file")
    }

    val length = readInt(input)
    require(length >= 0) { "Invalid backup frame length: $length" }

    val payload = if (length == 0) ByteArray(0) else readExactly(input, length)
    return BackupFrame(type = type, payload = payload)
}

internal fun writeInt(output: OutputStream, value: Int) {
    output.write(
        byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    )
}

internal fun writeFrame(
    output: OutputStream,
    type: Int,
    payload: ByteArray = ByteArray(0)
) {
    require(type in 0..255)

    output.write(type)
    writeInt(output, payload.size)
    output.write(payload)
}

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified T> writeCborFrame(
    output: OutputStream,
    type: Int,
    payload: T
) {
    writeFrame(output, type, Cbor.encodeToByteArray(payload))
}

internal inline fun <reified T> writeTable(
    output: OutputStream,
    sourceId: Int,
    tableId: Int,
    items: List<T>,
    batchSize: Int = 100
) {
    if (items.isEmpty()) return

    writeCborFrame(
        output = output,
        type = FrameType.TABLE_HEADER,
        payload = BackupTableHeader(sourceId, tableId)
    )

    for (batch in items.chunked(batchSize)) {
        writeCborFrame(
            output = output,
            type = FrameType.ENTITY_BATCH,
            payload = batch
        )
    }

    writeFrame(output, FrameType.TABLE_END)
}

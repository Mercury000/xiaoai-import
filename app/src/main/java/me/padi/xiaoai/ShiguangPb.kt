package com.mercury.xiaoaiimport

data class ShiguangAdapterEntry(
    val adapterId: String,
    val adapterName: String,
    val importType: Int,
    val assetJsPath: String,
    val importUrl: String,
    val description: String,
    val maintainer: String
)

data class ShiguangSchoolEntry(
    val id: String,
    val name: String,
    val initial: String,
    val resourceFolder: String,
    val adapters: List<ShiguangAdapterEntry>
)

private class ProtoReader(private val data: ByteArray) {
    var position: Int = 0

    fun isAtEnd(): Boolean = position >= data.size

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (position < data.size) {
            val b = data[position++].toInt() and 0xFF
            result = result or (((b and 0x7F).toLong()) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
            if (shift > 63) break
        }
        throw IllegalArgumentException("Invalid protobuf varint")
    }

    fun readTag(): Int = readVarint().toInt()

    fun readLengthDelimited(): ByteArray {
        val length = readVarint().toInt()
        if (length < 0 || position + length > data.size) {
            throw IllegalArgumentException("Invalid protobuf length")
        }
        val bytes = data.copyOfRange(position, position + length)
        position += length
        return bytes
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> position += 8
            2 -> {
                val length = readVarint().toInt()
                position += length
            }
            5 -> position += 4
            else -> throw IllegalArgumentException("Unsupported wire type: $wireType")
        }
        if (position > data.size) {
            throw IllegalArgumentException("Invalid protobuf field boundary")
        }
    }
}

private fun ByteArray.decodeUtf8OrEmpty(): String = try {
    String(this, Charsets.UTF_8)
} catch (_: Exception) {
    ""
}

private fun parseShiguangAdapter(bytes: ByteArray): ShiguangAdapterEntry {
    val reader = ProtoReader(bytes)
    var adapterId = ""
    var adapterName = ""
    var importType = 0
    var assetJsPath = ""
    var importUrl = ""
    var description = ""
    var maintainer = ""

    while (!reader.isAtEnd()) {
        val tag = reader.readTag()
        val fieldNumber = tag ushr 3
        val wireType = tag and 0x07
        when (fieldNumber) {
            1 -> adapterId = reader.readLengthDelimited().decodeUtf8OrEmpty()
            2 -> adapterName = reader.readLengthDelimited().decodeUtf8OrEmpty()
            3 -> importType = reader.readVarint().toInt()
            4 -> assetJsPath = reader.readLengthDelimited().decodeUtf8OrEmpty()
            5 -> importUrl = reader.readLengthDelimited().decodeUtf8OrEmpty()
            6 -> description = reader.readLengthDelimited().decodeUtf8OrEmpty()
            7 -> maintainer = reader.readLengthDelimited().decodeUtf8OrEmpty()
            else -> reader.skipField(wireType)
        }
    }

    return ShiguangAdapterEntry(
        adapterId = adapterId,
        adapterName = adapterName,
        importType = importType,
        assetJsPath = assetJsPath,
        importUrl = importUrl,
        description = description,
        maintainer = maintainer
    )
}

private fun parseShiguangSchool(bytes: ByteArray): ShiguangSchoolEntry {
    val reader = ProtoReader(bytes)
    var id = ""
    var name = ""
    var initial = ""
    var resourceFolder = ""
    val adapters = mutableListOf<ShiguangAdapterEntry>()

    while (!reader.isAtEnd()) {
        val tag = reader.readTag()
        val fieldNumber = tag ushr 3
        val wireType = tag and 0x07
        when (fieldNumber) {
            1 -> id = reader.readLengthDelimited().decodeUtf8OrEmpty()
            2 -> name = reader.readLengthDelimited().decodeUtf8OrEmpty()
            3 -> initial = reader.readLengthDelimited().decodeUtf8OrEmpty()
            4 -> resourceFolder = reader.readLengthDelimited().decodeUtf8OrEmpty()
            5 -> adapters += parseShiguangAdapter(reader.readLengthDelimited())
            else -> reader.skipField(wireType)
        }
    }

    return ShiguangSchoolEntry(
        id = id,
        name = name,
        initial = initial,
        resourceFolder = if (resourceFolder.isBlank()) id else resourceFolder,
        adapters = adapters
    )
}

fun parseShiguangSchoolIndexPb(bytes: ByteArray): List<ShiguangSchoolEntry> {
    val reader = ProtoReader(bytes)
    val schools = mutableListOf<ShiguangSchoolEntry>()

    while (!reader.isAtEnd()) {
        val tag = reader.readTag()
        val fieldNumber = tag ushr 3
        val wireType = tag and 0x07
        when (fieldNumber) {
            3 -> schools += parseShiguangSchool(reader.readLengthDelimited())
            else -> reader.skipField(wireType)
        }
    }

    return schools
}

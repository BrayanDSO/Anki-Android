// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.themes

import android.app.Activity
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.TypedValue
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RequiresApi(Build.VERSION_CODES.R)
internal fun Activity.installThemeColorOverrides(colors: Map<Int, Int>): Boolean {
    val overrides = colors.filter { (id, color) -> getColor(id) != color }
    if (overrides.isEmpty()) return true
    return try {
        val table = colorResourceTable(resources, overrides)
        val file = Os.memfd_create("ankidroid-theme-colors", OsConstants.MFD_CLOEXEC)
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(file)).use { it.write(table) }
            ParcelFileDescriptor.dup(file).use { descriptor ->
                val loader = ResourcesLoader()
                loader.addProvider(ResourcesProvider.loadFromTable(descriptor, null))
                resources.addLoaders(loader)
            }
        } finally {
            Os.close(file)
        }
        true
    } catch (e: Exception) {
        Timber.w(e, "Could not load preview theme colors")
        false
    }
}

/**
 * Encodes a resources.arsc table for the app's color resources using Android's ResourceTypes.h
 * format. The loader overlays entries by their IDs, which aapt assigns separately for each build.
 */
private fun colorResourceTable(
    resources: Resources,
    colors: Map<Int, Int>,
): ByteArray {
    val ids = colors.keys.sorted()
    val first = ids.first()
    val packageName = resources.getResourcePackageName(first)
    val typeId = first ushr 16 and 0xff
    require(packageName.length < 128 && ids.all { it ushr 16 == first ushr 16 })
    val entryCount = (ids.last() and 0xffff) + 1
    val entryIndices = IntArray(entryCount) { -1 }
    ids.forEachIndexed { index, id -> entryIndices[id and 0xffff] = index }

    val globalStrings = stringPool(emptyList())
    val typeNames = stringPool(List(typeId) { if (it == typeId - 1) "color" else "?$it" })
    val entryNames = stringPool(ids.map(resources::getResourceEntryName))

    val entrySize = 16 // ResTable_entry and Res_value
    val packageHeaderSize = 288
    val typeSpecHeaderSize = 16
    val configSize = 64
    val typeHeaderSize = 20 + configSize
    val typeSpecSize = typeSpecHeaderSize + Int.SIZE_BYTES * entryCount
    val entriesOffset = typeHeaderSize + Int.SIZE_BYTES * entryCount
    val typeSize = entriesOffset + entrySize * ids.size
    val packageSize = packageHeaderSize + typeNames.size + entryNames.size + typeSpecSize + typeSize
    val tableSize = 12 + globalStrings.size + packageSize

    val table = ByteBuffer.allocate(tableSize).order(ByteOrder.LITTLE_ENDIAN)
    table.putChunkHeader(0x0002, 12, tableSize) // RES_TABLE_TYPE
    table.putInt(1) // One package
    table.put(globalStrings)

    table.putChunkHeader(0x0200, packageHeaderSize, packageSize) // RES_TABLE_PACKAGE_TYPE
    table.putInt(first ushr 24)
    packageName.padEnd(128, '\u0000').forEach { table.putChar(it) }
    table.putInt(packageHeaderSize) // Offset to type names
    table.putInt(0) // Last public type
    table.putInt(packageHeaderSize + typeNames.size) // Offset to entry names
    table.putInt(0) // Last public entry
    table.putInt(0) // Type ID offset
    table.put(typeNames)
    table.put(entryNames)

    table.putChunkHeader(0x0202, typeSpecHeaderSize, typeSpecSize) // RES_TABLE_TYPE_SPEC_TYPE
    table.putInt(typeId)
    table.putInt(entryCount)
    entryIndices.forEach { table.putInt(if (it == -1) 0 else 0x40000000) } // SPEC_PUBLIC

    table.putChunkHeader(0x0201, typeHeaderSize, typeSize) // RES_TABLE_TYPE_TYPE
    table.putInt(typeId)
    table.putInt(entryCount)
    table.putInt(entriesOffset)
    table.putInt(configSize)
    table.position(table.position() + configSize - Int.SIZE_BYTES) // Default configuration
    entryIndices.forEach { table.putInt(if (it == -1) -1 else it * entrySize) }
    ids.forEachIndexed { index, id ->
        table.putShort(8) // ResTable_entry size
        table.putShort(0x0002) // FLAG_PUBLIC
        table.putInt(index)
        table.putShort(8) // Res_value size
        table.put(0)
        table.put(TypedValue.TYPE_INT_COLOR_ARGB8.toByte())
        table.putInt(colors.getValue(id))
    }
    return table.array()
}

private fun stringPool(strings: List<String>): ByteArray {
    val headerSize = 28
    val stringsStart = headerSize + Int.SIZE_BYTES * strings.size
    // Each UTF-16 string has a length and a null terminator. Chunks are aligned to four bytes.
    val size = (stringsStart + strings.sumOf { (it.length + 2) * Char.SIZE_BYTES } + 3) and -4
    val pool = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    pool.putChunkHeader(0x0001, headerSize, size) // RES_STRING_POOL_TYPE
    pool.putInt(strings.size)
    pool.putInt(0) // No styles
    pool.putInt(0) // UTF-16
    pool.putInt(stringsStart)
    pool.putInt(0) // Offset to styles
    var offset = 0
    for (string in strings) {
        pool.putInt(offset)
        offset += (string.length + 2) * Char.SIZE_BYTES
    }
    for (string in strings) {
        pool.putShort(string.length.toShort())
        string.forEach { pool.putChar(it) }
        pool.putChar('\u0000')
    }
    return pool.array()
}

private fun ByteBuffer.putChunkHeader(
    type: Int,
    headerSize: Int,
    size: Int,
) {
    putShort(type.toShort())
    putShort(headerSize.toShort())
    putInt(size)
}

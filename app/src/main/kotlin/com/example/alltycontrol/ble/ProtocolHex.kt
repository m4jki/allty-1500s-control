package com.example.alltycontrol.ble

fun ByteArray.toHex(separator: String = ""): String =
    joinToString(separator) { byte -> "%02X".format(byte.toUByte().toInt()) }

fun parseHex(value: String): Result<ByteArray> = runCatching {
    val compact = value.filterNot(Char::isWhitespace)
    require(compact.isNotEmpty()) { "Enter a hexadecimal frame" }
    require(compact.length % 2 == 0) { "Hexadecimal input must contain an even number of digits" }
    require(compact.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        "Input contains a non-hexadecimal character"
    }
    compact.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

package mars


fun ULong.toHex(pad: Int = 16) = "0x${this.toString(16).uppercase().padStart(pad, '0')}"
fun Long.toHex() = toULong().toHex(16)
fun UInt.toHex(pad: Int = 8) = toULong().toHex(pad)
fun Int.toHex() = toUInt().toHex(8)
fun Char.toHex() = code.toUInt().toHex(2)

fun String.detectRadix() = if (length < 2) 10 else when (substring(0, 2))
{
    "0x", "0X" -> 16
    "0b", "0B" -> 2
    else -> 10
}
